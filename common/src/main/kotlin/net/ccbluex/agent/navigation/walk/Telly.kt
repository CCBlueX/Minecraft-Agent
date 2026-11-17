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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.agent.navigation.simulateTick
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.math.Vec3
import kotlin.math.abs

/**
 * Telly bridging along a straight level [Bridge]: sprinting forwards on the floor placed so far, a jump from its end,
 * a quick turn round in the air to place the floor under and behind the flight, and forwards again once the landing
 * is on blocks. The turns ease like a flick of the mouse; one click a tick at most, on a face the eye sees first.
 *
 * @agent claude-opus-5-5
 */
internal class Telly(
    private val context: NavigationContext,
    private val actions: BlockActions,
    private val check: StepCheck,
) {

    private val body get() = context.body
    private val player get() = body.player

    /**
     * The view turned towards, null before the first tick.
     */
    var view: ViewRotation? = null
        private set

    /**
     * The floor placed or aimed at this tick, null while only running or flying.
     */
    var acting: BlockPos? = null
        private set

    /**
     * One tick along [bridge] of [steps], the view held [xRot] degrees below the horizon.
     */
    fun tick(keys: MovementKeys, frameYRot: Float, steps: List<Step>, bridge: Bridge, xRot: Float): Movement {
        val first = steps[bridge.start]
        val way = Vec3.atLowerCornerOf(first.to.subtract(first.from))
        val end = steps[bridge.end - 1].feet
        val forward = ViewRotation(bridge.yRot, xRot)
        val back = ViewRotation(Mth.wrapDegrees(bridge.yRot + HALF_TURN), xRot)
        keys.directionalInput = getDirectionalInputTowards(way, frameYRot)
        keys.jump = false
        keys.sneak = false
        acting = null

        val pending = (bridge.start until bridge.end).firstNotNullOfOrNull { index ->
            steps[index].places.firstOrNull { !check.isPlaced(it) }
        }
        if (pending == null) {
            turn(forward)
            return Movement(end, sprint = true)
        }

        // Where the missing floor begins along the bridge: the face of the block before it, which it is placed on.
        val edge = Vec3.atCenterOf(pending).dot(way) - HALF_BLOCK
        val along = player.position().dot(way)
        val isPassing = along > edge + PASSING
        if (player.onGround()) {
            if (isPassing) {
                keys.directionalInput = Input.EMPTY
                place(keys, pending, way, edge, back)
                return Movement(end)
            }
            val yRot = turn(forward)
            keys.jump = along >= edge - RUN_UP && abs(Mth.wrapDegrees(yRot - bridge.yRot)) < FACING
            return Movement(end, sprint = true)
        }

        if (!isPassing && lands(keys.toInput(sprint = false), first.from.y.toDouble())) {
            turn(forward)
            return Movement(end, sprint = true)
        }
        place(keys, pending, way, edge, back)
        return Movement(end)
    }

    /**
     * Faces back at the face [pending] is placed on, from where the eye is after this tick, and clicks it once the
     * crosshair is there; straight back while that face is still ahead of the eye.
     */
    private fun place(keys: MovementKeys, pending: BlockPos, way: Vec3, edge: Double, back: ViewRotation) {
        val eye = body.simulateTick(keys.toInput(sprint = false)).player.eyePosition
        val face = Vec3.atCenterOf(pending).subtract(way.scale(HALF_BLOCK))
        val aim = if (eye.dot(way) > edge) ViewRotation.fromVector(face.subtract(eye)) else back
        turn(aim)
        actions.place(pending, ready = true, held = aim)
        acting = pending
    }

    /**
     * Turns towards [goal] as a flick eases out, and the yaw turned to.
     */
    private fun turn(goal: ViewRotation): Float {
        view = goal
        val turned = FLICK.turn(body.sentRotation, goal, LookPurpose.MOVEMENT)
        body.look(turned, LookPurpose.STEER)
        return turned.yRot
    }

    /**
     * Whether the flight with [input] held ends on blocks at height [y], not lower.
     */
    private fun lands(input: Input, y: Double): Boolean {
        val simulated = body.simulateTick(input)
        repeat(FLIGHT_TICKS) {
            if (simulated.player.onGround()) {
                return simulated.player.y >= y - LANDING_MARGIN
            }
            if (simulated.player.y < y - 1.0) {
                return false
            }
            simulated.tick()
        }
        return false
    }

    private companion object {
        const val HALF_BLOCK = 0.5
        const val HALF_TURN = 180f

        /**
         * Blocks before the end of the floor from which the jump goes.
         */
        const val RUN_UP = 0.3

        /**
         * Blocks past the face of a missing floor from which the player places it standing, having passed over it.
         */
        const val PASSING = 0.5

        /**
         * Degrees off the way of the bridge within which a jump still sprints forwards.
         */
        const val FACING = 45f
        const val FLIGHT_TICKS = 30
        const val LANDING_MARGIN = 0.01

        /**
         * A flick that eases out over a few ticks, with small corrections, such as pulling down onto the next face,
         * made in one tick.
         */
        val FLICK = Turning.eased(most = Turning.FLICK_TURN, share = 0.6f, least = 10f)
    }
}
