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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.FollowState
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.Step
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import kotlin.math.min

/**
 * Walks a [Route] step by step through movement keys, mining, placing and opening with [actions] on the way. While
 * another feature places bridge floors, level bridges are run across at full speed and the floor is left to it.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L175-L397)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class Follower(
    private val context: NavigationContext,
    private val actions: BlockActions,
) : Progress {

    private val player get() = context.body.player
    private val level get() = context.body.level
    private val water = Swimming(context)

    /**
     * How fast the player really walks, measured every tick it follows a path.
     */
    val stride = Stride()
    private val hops = Hops(context, stride)
    private val check = StepCheck(context, actions)
    private val overshoot = Overshoot(context)

    var path: Route? = null
        private set
    private var index = 0

    private val step: Step?
        get() = path?.steps?.getOrNull(index)

    val isFinished: Boolean
        get() = path != null && step == null

    /**
     * The block the step given up last leads into.
     */
    var stuckAt: BlockPos? = null
        private set

    private var movement = Movement.NONE

    val target get() = movement.target
    val sprint get() = movement.sprint
    val dive get() = movement.dive
    val isPlainWalk get() = movement.isPlainWalk
    val safeWalk get() = movement.safeWalk

    /**
     * Whether this step or the next mines or places blocks, which aims at them in between the walking.
     */
    val building: Boolean
        get() = step?.builds == true || path?.steps?.getOrNull(index + 1)?.builds == true

    /**
     * The bridge steps in a row from this step or the next, 0 without.
     */
    val bridgeAhead: Int
        get() {
            val steps = path?.steps ?: return 0
            val start = if (steps.getOrNull(index)?.isBridge == true) index else index + 1
            return runEnd(steps, start) - start
        }

    /**
     * The line the bridge being crossed is run along, null while not crossing one.
     */
    private var bridgeLine: BridgeLine? = null

    /**
     * The yaw of the bridge being crossed, null while not crossing one.
     */
    val bridgeYRot: Float?
        get() = bridgeLine?.heading

    /**
     * The far end of the bridge being crossed, which the view keeps to instead of the steps ahead.
     */
    val bridgeTarget: Vec3?
        get() = bridgeLine?.to

    var remainingBreaks = emptyList<BlockPos>()
        private set
    var remainingPlaces = emptyList<BlockPos>()
        private set

    private var progress = StepProgress()

    /**
     * Times the aim lay behind the way the player moved fast, which shows overshooting the path.
     */
    val overshoots: Int
        get() = overshoot.count

    fun follow(path: Route, startIndex: Int) {
        this.path = path
        index = startIndex
        stepChanged()
    }

    fun clear() {
        path = null
        index = 0
        movement = Movement.NONE
        hops.clear()
        bridgeLine = null
        stepChanged()
    }

    /**
     * The server moved the player back: the hop it aimed is over and the time lost does not count as stalling.
     */
    fun positionCorrected() {
        hops.clear()
        progress = StepProgress()
    }

    /**
     * Only checked while the player stands, swims or climbs, as jumps and drops leave the path on purpose.
     */
    fun isOffPath(): Boolean {
        val path = path ?: return false
        return step != null && player.isStable && path.isOffPath(index, player.position())
    }

    fun tick(keys: MovementKeys, frameYRot: Float): FollowState {
        stride.tick(player)
        movement = Movement.NONE
        advance()
        val step = step ?: run {
            actions.idle()
            standStill(keys)
            return FollowState.FINISHED
        }
        if (step.isBridge && context.options().placesBridgeFloors) {
            return crossBridge(keys, frameYRot)
        }
        bridgeLine = null

        // A server that keeps refusing the blocks will not hold a bridge here.
        if (step.places.isNotEmpty() && actions.rejectedInRow >= MAX_REJECTIONS) {
            actions.forgetRejections()
            actions.idle()
            standStill(keys)
            return giveUp(step)
        }

        val pendingBreak = step.breaks.firstOrNull { !check.isCleared(it) }
        val pendingPlace = step.places.firstOrNull { !check.isPlaced(it) }
        val unconfirmedPlace = step.places.firstOrNull(actions::isUnconfirmed)
        val pendingOpen = step.opens.firstOrNull { !check.isOpen(it) || actions.isOpenUnconfirmed(it) }
        return when {
            pendingBreak != null -> {
                standStill(keys)
                actions.mine(pendingBreak)
                act(pendingBreak, step)
            }

            pendingPlace != null -> {
                place(keys, frameYRot, step, pendingPlace)
                act(pendingPlace, step)
            }

            // The server may still take the block back, and the player with it into the void.
            unconfirmedPlace != null -> {
                actions.idle()
                standStill(keys)
                holdAtEdge(keys, step, unconfirmedPlace)
                act(unconfirmedPlace, step)
            }

            pendingOpen != null -> {
                standStill(keys)
                // Climbing on keeps the player at a hatch above.
                keys.jump = player.isInWater || player.onClimbable()
                actions.open(pendingOpen)
                act(pendingOpen, step)
            }

            else -> walk(keys, frameYRot, step)
        }
    }

    private fun walk(keys: MovementKeys, frameYRot: Float, step: Step): FollowState {
        actions.idle()
        progress.acting = null
        movement = move(keys, frameYRot, step)

        // A jump moves away from its target while walking back to the run-up, so it is only timed.
        val stuck = if (step.jump != null) {
            ++progress.jumpTicks >= JUMP_TICKS || progress.leap.hopeless
        } else {
            progress.watch.stalls(player.position().distanceTo(step.feet)) ||
                progress.isDueToPredict && !context.getsAhead(keys, frameYRot, step, movement)
        }
        return if (stuck) giveUp(step) else FollowState.WALKING
    }

    private fun place(keys: MovementKeys, frameYRot: Float, step: Step, pos: BlockPos) {
        val position = player.position()
        val pillars = step.move == Move.PILLAR
        standStill(keys)
        when {
            pillars -> {
                keys.directionalInput = centerInput(position, step.feet, frameYRot)
                keys.jump = player.onGround()
            }

            // Bridging: lean over the edge, so the side of the block below is in sight.
            pos.y < step.from.y -> {
                keys.directionalInput = getDirectionalInputTowards(step.feet.subtract(position), frameYRot)
                holdAtEdge(keys, step, pos, step.feet)
            }
        }
        actions.place(pos, ready = !pillars || position.y >= pos.y + 1.0)
    }

    /**
     * Keeps the player on the ground while it leans over the edge for the floor at [pos], or waits there for the
     * server to keep it: sneaking, which anticheats expect a player to stop at an edge with, rather than safe walk.
     *
     * @agent claude-opus-5-5
     */
    private fun holdAtEdge(keys: MovementKeys, step: Step, pos: BlockPos, target: Vec3? = null) {
        if (pos.y < step.from.y) {
            keys.sneak = true
            movement = Movement(target)
        } else {
            movement = Movement(target, safeWalk = true)
        }
    }

    private fun act(pos: BlockPos, step: Step) =
        if (progress.actsTooLong(pos)) giveUp(step) else FollowState.ACTING

    /**
     * Runs along one straight line, at any angle, from where the crossing began to the far end of the bridge,
     * sprinting, while another feature places the floor underfoot. Following the staircase of bridge steps instead
     * would turn the way at every step.
     */
    private fun crossBridge(keys: MovementKeys, frameYRot: Float): FollowState {
        actions.idle()
        progress.acting = null
        val steps = path?.steps ?: return FollowState.FINISHED
        val end = runEnd(steps, index) - 1
        val target = steps[end].feet
        val position = player.position()
        val left = position.horizontalDistanceTo(target)

        // The floor follows the straight run rather than every block of a staircase, so passed steps count as done.
        val start = index
        while (index <= end && left <= steps[index].feet.horizontalDistanceTo(target) + REACH_HORIZONTAL) {
            index++
        }
        if (index != start) {
            reached(steps, start)
            stepChanged()
            if (index > end) {
                bridgeLine = null
                return tick(keys, frameYRot)
            }
        }

        // A new search that ends the run about where it did keeps the line, so the heading does not jump.
        val line = bridgeLine?.takeIf { it.to.horizontalDistanceTo(target) <= LINE_KEEP }
            ?: BridgeLine(position, target).also { bridgeLine = it }
        keys.directionalInput = getDirectionalInputTowards(line.aimFrom(position).subtract(position), frameYRot)
        keys.jump = false
        keys.sneak = false
        movement = Movement(line.to, sprint = true, safeWalk = true)
        return if (progress.watch.stalls(left)) giveUp(steps[index]) else FollowState.WALKING
    }

    private fun giveUp(step: Step): FollowState {
        stuckAt = step.to
        progress = StepProgress()
        return FollowState.STUCK
    }

    private fun move(keys: MovementKeys, frameYRot: Float, step: Step): Movement {
        // Air is filled up before a dive, and during one once it runs low.
        if (water.breathes() || step.isUnderWater && water.isShortOfAir()) {
            standStill(keys)
            progress.watch.excuse()
            return Movement.NONE
        }
        if (step.isUnderWater) {
            return water.swim(keys, frameYRot, step)
        }
        if (step.move == Move.CLIMB_UP) {
            return context.climbUp(keys, frameYRot, step)
        }

        val jump = step.jump
        val abilities = path?.abilities
        if (jump != null && abilities != null) {
            return context.leap(keys, frameYRot, step, jump, abilities, progress.leap, stride)
        }

        val position = player.position()
        if (player.onGround()) {
            hops.clear()
        }
        val shortcut = path?.takeIf { step.isPlain && player.onGround() }
            ?.shortcut(index, position, Terrain.live(level))
        val aim = overshoot.forwardAim(position, shortcut ?: hops.aim ?: step.feet, step, ::pointAhead)

        keys.directionalInput = centerInput(position, aim, frameYRot)
        keys.sneak = step.shift
        keys.jump = wantsJump(step, player) || hops.starts(position, aim, step.feet)
        return Movement(aim, sprint = !step.shift, isPlainWalk = step.isLevelWalk, safeWalk = step.edge)
    }

    private fun standStill(keys: MovementKeys) {
        keys.directionalInput = Input.EMPTY
        keys.jump = player.isInWater
        keys.sneak = false
    }

    private fun advance() {
        val path = path ?: return
        val start = index
        while (index < path.steps.size && check.isReached(path.steps[index], path.steps.getOrNull(index + 1))) {
            index++
        }
        skipAhead(path)

        if (index != start) {
            reached(path.steps, start)
            stepChanged()
        }
    }

    private fun reached(steps: List<Step>, start: Int) {
        for (i in start until min(index, steps.size)) {
            context.listener.stepReached(i, steps[i].to)
        }
    }

    /**
     * A shortcut walks by the blocks of plain steps: standing in the block of a later one, those before are done.
     */
    private fun skipAhead(path: Route) {
        index = check.skipAhead(path, index)
    }

    /**
     * [Route.lookAhead] from the step walked.
     */
    fun lookAhead(target: Vec3, position: Vec3, distance: Double): Vec3? =
        path?.lookAhead(index, target, position, distance)

    /**
     * [Route.pointAhead] from the step walked; null without a path.
     */
    fun pointAhead(position: Vec3, distance: Double): Vec3? = path?.pointAhead(index, position, distance)

    /**
     * The sharpest turn of the path within [distance] blocks ahead, in degrees; 0 without a path.
     */
    fun turnAhead(distance: Double): Float = path?.turnAhead(index, player.position(), distance) ?: 0f

    private fun stepChanged() {
        progress = StepProgress()
        val remaining = path?.steps.orEmpty().drop(index)
        remainingBreaks = remaining.flatMap(Step::breaks)
        remainingPlaces = remaining.flatMap(Step::places)
    }

    override val isMoving: Boolean
        get() = step != null

    override fun head(partialTicks: Float): Vec3 = player.getPosition(partialTicks)

    override val ahead: List<Vec3>
        get() {
            val points = path?.points ?: return emptyList()
            return points.subList(min(index + 1, points.size), points.size)
        }

    private companion object {
        const val MAX_REJECTIONS = 3

        /**
         * Walking back to the run-up included.
         */
        const val JUMP_TICKS = 120

        /**
         * How far the end of a newly searched bridge may lie from the old one for the line to stay.
         */
        const val LINE_KEEP = 1.5
    }

}
