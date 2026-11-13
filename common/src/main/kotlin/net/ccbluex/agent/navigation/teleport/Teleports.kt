/*
 * This file is part of Minecraft Agent (https://github.com/CCBlueX/Minecraft-Agent)
 *
 * Copyright (c) 2026 CCBlueX
 *
 * Minecraft Agent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minecraft Agent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minecraft Agent. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.agent.navigation.teleport

import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.pathfinder.teleport.Route
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * Sends teleport routes for [body] through its [Body.teleporter], one batch of steps per tick, and tells the host
 * where the server has the player so its own movement packets stay in line ([serverStep]). One owner at a time;
 * one that stops running is undone.
 *
 * The host calls [tick] once per tick after everything else moved the player, [setBack] when the server sets the
 * player back, [blockChanged] for every block update and [cancel] on a world change or disconnect.
 *
 * From LiquidBounce: [TeleportManager.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/teleport/TeleportManager.kt#L47-L248)
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class Teleports(private val body: Body) {

    /**
     * The latest run, also after it ended.
     */
    public var run: Teleport? = null
        private set

    @Volatile
    private var holder: Owner? = null

    /**
     * The last step the server got, while it has the player somewhere else than the client.
     */
    @Volatile
    private var lastSent: Route.Step? = null

    @Volatile
    private var setback = false

    /**
     * Where the current excursion began, followed by the route points it passed.
     */
    private val trail = ArrayList<Vec3>()
    private var stepLength = Options.DEFAULT_STEP_LENGTH
    private var pacing: Pacing = Pacing.Batches()
    private var claimsGroundOnDescent = false

    public val serverPosition: Vec3?
        get() = lastSent?.position

    /**
     * Whether the server has the player on the ground at [serverPosition]; outgoing movement packets carry
     * [serverPosition] and this while [serverPosition] is not null and no setback is pending.
     */
    public val serverOnGround: Boolean
        get() = lastSent?.onGround ?: body.player.onGround()

    /**
     * Whether outgoing movement packets must be rewritten to [serverPosition] and [serverOnGround].
     */
    public val rewritesMovement: Boolean
        get() = !setback && lastSent != null

    public val isMoving: Boolean
        get() = run?.isMoving == true

    public fun holds(owner: Owner): Boolean = holder === owner

    /**
     * Where the server has the player for [owner] between the last tick and this one, null if with the client.
     */
    public fun serverPosition(owner: Owner, partialTicks: Float): Vec3? =
        if (holds(owner)) run?.takeIf { it.isMoving }?.head(partialTicks) ?: serverPosition else null

    /**
     * Whether nothing moves and the server has the player with the client or where [owner] left it.
     */
    public fun isAvailable(owner: Owner): Boolean = !isMoving && (holder == null || holder === owner)

    /**
     * Starts sending [route] from where the server has the player; null when busy or it begins too far off.
     */
    internal fun dispatch(
        owner: Owner,
        route: Route,
        finish: Teleport.Finish,
        options: Options,
    ): Teleport? {
        if (!isAvailable(owner) || body.player.isPassenger || body.teleporter == null) {
            return null
        }

        val held = serverPosition
        val current = held ?: body.player.position()
        val drift = route.start.distanceToSqr(current)
        val linked = when {
            drift > MAX_DRIFT_SQ -> return null
            drift <= Route.SAME_POSITION_SQ -> route
            else -> route.startingAt(current, Terrain.live(body.level), options.stepLength) ?: return null
        }

        if (held == null) {
            trail.clear()
            trail += current
        }

        stepLength = options.stepLength
        pacing = options.pacing
        claimsGroundOnDescent = options.shouldClaimGroundOnDescent
        return start(owner, linked, finish)
    }

    /**
     * Brings the server back to where the excursion of [owner] began, or lands the client if the way is blocked.
     */
    public fun undo(owner: Owner) {
        val current = run
        if (holder !== owner || current?.isMoving == true && current.finish == Teleport.Finish.RESYNC) {
            return
        }

        current?.takeIf { it.isMoving }?.end(Teleport.State.CANCELLED)

        val position = serverPosition ?: return release()
        val back = wayBack(position, trail) ?: return release()
        val route = Route.plan(Terrain.live(body.level), back, stepLength) ?: return land(position)
        start(owner, route, Teleport.Finish.RESYNC)
    }

    /**
     * Sends this tick's batch; call once per tick after the player moved.
     */
    public fun tick() {
        val run = run ?: return
        val holder = holder
        when {
            setback -> reset(Teleport.State.REJECTED)
            holder != null && !holder.isRunning() && run.finish != Teleport.Finish.RESYNC -> undo(holder)
            !run.isMoving -> run.settle()
            run.isBlocked -> {
                run.end(Teleport.State.INVALID)
                undo(run.owner)
            }
            else -> send(run)
        }
    }

    /**
     * The server moved the player itself; the run ends as rejected on the next [tick].
     */
    public fun setBack() {
        if (holder != null) {
            setback = true
            lastSent = null
        }
    }

    public fun blockChanged(pos: BlockPos, state: BlockState) {
        run?.onBlockChange(body.level, pos, state)
    }

    /**
     * Sends where the client is, for the server to follow it again after the host sent movement packets of its own
     * outside an excursion. Does nothing while a route is being sent.
     */
    public fun syncPosition() {
        val teleporter = body.teleporter
        if (isMoving || teleporter == null) {
            return
        }

        teleporter.send(body.player.position(), body.player.onGround())
        release()
    }

    /**
     * Ends everything without a way back, for a world change or disconnect.
     */
    public fun cancel() {
        reset(Teleport.State.CANCELLED)
    }

    private fun start(owner: Owner, route: Route, finish: Teleport.Finish) =
        Teleport(owner, route, pacing.batches(route), finish).also {
            run = it
            holder = owner
            setback = false
        }

    private fun send(run: Teleport) {
        val teleporter = body.teleporter
        if (!run.isSent && teleporter != null) {
            for (planned in run.nextBatch()) {
                if (setback) {
                    return
                }

                val previous = lastSent?.position ?: run.route.start
                val claimsGround = claimsGroundOnDescent && planned.position.y < previous.y
                val step = if (claimsGround && !planned.onGround) {
                    Route.Step(planned.position, true, planned.segment)
                } else {
                    planned
                }
                teleporter.send(step.position, step.onGround)
                lastSent = step

                val reached = run.route.points[step.segment + 1]
                if (run.finish != Teleport.Finish.RESYNC &&
                    step.position.distanceToSqr(reached) < Route.SAME_POSITION_SQ) {
                    trail += reached
                }
            }
        }

        if (run.isSent) {
            complete(run)
        }
    }

    private fun complete(run: Teleport) {
        run.end(Teleport.State.DONE)
        when (run.finish) {
            Teleport.Finish.LAND -> land(run.route.destination)
            Teleport.Finish.HOLD -> {}
            Teleport.Finish.RESYNC -> release()
        }
    }

    private fun land(position: Vec3) {
        body.teleporter?.land(position)
        body.player.resetFallDistance()
        release()
    }

    private fun release() {
        lastSent = null
        holder = null
        trail.clear()
    }

    private fun reset(state: Teleport.State) {
        run?.takeIf { it.isMoving || holder != null }?.end(state)
        release()
        setback = false
    }

    private companion object {
        const val MAX_DRIFT_SQ = 2.0 * 2.0
    }

}

/**
 * The way from [position] back along [trail], without the points the server is already at.
 *
 * From LiquidBounce: [TeleportManager.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/teleport/TeleportManager.kt#L248-L256)
 */
private fun wayBack(position: Vec3, trail: List<Vec3>): List<Vec3>? {
    val points = arrayListOf(position)
    for (point in trail.asReversed()) {
        if (point.distanceToSqr(points.last()) > Route.SAME_POSITION_SQ) {
            points += point
        }
    }
    return points.takeIf { it.size >= 2 }
}
