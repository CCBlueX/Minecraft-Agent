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

package net.ccbluex.agent.navigation.flight

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ifNaN
import net.ccbluex.agent.pathfinder.flight.Acceleration
import net.ccbluex.agent.pathfinder.flight.FlyAbilities
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.entity.LivingEntity.Companion.FLYING_AIR_DRAG
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How the player's flight answers the keys, measured from what the player did after them: the motion the keys add a
 * tick (thrust), level, sprinting and vertical, and the share of the motion kept a tick without keys (drag), which
 * sets how far it coasts. Until measured, the values follow from the [FlyAbilities] the flight is captured with; every
 * measurement after the first is blended in, so a fly mode that changes how it moves is measured again.
 *
 * @agent claude-opus-5-5
 */
internal class Handling {

    private var control = Acceleration.DIRECT
    private var baseSpeed = Double.NaN
    private var baseVerticalSpeed = Double.NaN

    private var measuredDrag = Double.NaN
    private var measuredVerticalDrag = Double.NaN
    private var measuredThrust = Double.NaN
    private var measuredSprintThrust = Double.NaN
    private var measuredVerticalThrust = Double.NaN

    private var lastPosition: Vec3? = null

    /**
     * The move of the last tick recorded; null after a tick that was not.
     */
    var lastMove: Vec3? = null
        private set
    private var lastTick = 0
    private var lastPressed: Input? = null

    /**
     * Share of the level motion kept a tick with no keys held.
     */
    val drag: Double
        get() = measuredDrag.ifNaN { if (control == Acceleration.MOMENTUM) FLYING_AIR_DRAG.toDouble() else 0.0 }

    val verticalDrag: Double
        get() = measuredVerticalDrag.ifNaN { if (control == Acceleration.MOMENTUM) FlyAbilities.VERTICAL_DRAG else 0.0 }

    // Until one is measured it is taken from the other, as a fly mode moves the same sprinting or not.
    private val sprintThrust: Double
        get() = measuredSprintThrust.ifNaN { measuredThrust }.ifNaN { baseSpeed * (1.0 - drag) }

    private val thrust: Double
        get() = measuredThrust.ifNaN { measuredSprintThrust }.ifNaN { baseSpeed * (1.0 - drag) * plainShare }

    private val verticalThrust: Double
        get() = measuredVerticalThrust.ifNaN { baseVerticalSpeed * (1.0 - verticalDrag) }

    // Creative flight goes twice as fast sprinting; a fly mode is taken to ignore sprinting.
    private val plainShare: Double
        get() = if (control == Acceleration.MOMENTUM) SPRINT_SHARE else 1.0

    /**
     * Blocks a tick flying level with the keys held, sprinting: the speed the motion settles at.
     */
    val speed: Double
        get() = (sprintThrust / (1.0 - drag)).coerceAtLeast(MIN_SPEED)

    val verticalSpeed: Double
        get() = (verticalThrust / (1.0 - verticalDrag)).coerceAtLeast(MIN_SPEED)

    /**
     * How much further level the player comes to rest for one more tick of the keys, not sprinting.
     */
    val step: Double
        get() = thrust / (1.0 - drag)

    /**
     * Whether released keys leave the player coasting on rather than stopping.
     */
    val coasts: Boolean
        get() = drag > COASTING_DRAG

    /**
     * Blocks the player coasts level from full speed once the keys are released.
     */
    val coastDistance: Double
        get() = speed * drag / (1.0 - drag)

    /**
     * The flight as captured in [base] with the speeds measured so far; a [base] with another control or other
     * speeds, such as a changed creative flying speed, starts the measuring over.
     */
    fun abilities(base: FlyAbilities): FlyAbilities {
        if (base.control != control || base.horizontalSpeed != baseSpeed || base.verticalSpeed != baseVerticalSpeed) {
            control = base.control
            baseSpeed = base.horizontalSpeed
            baseVerticalSpeed = base.verticalSpeed
            measuredDrag = Double.NaN
            measuredVerticalDrag = Double.NaN
            measuredThrust = Double.NaN
            measuredSprintThrust = Double.NaN
            measuredVerticalThrust = Double.NaN
        }
        return FlyAbilities(control, speed, verticalSpeed, base.isFlying)
    }

    /**
     * Measures the move [player] made since the last call with [pressed] held, sprinting or not as the player did;
     * null [pressed] when the keys were not the flight's own, such as during a take-off.
     */
    fun record(player: Player, pressed: Input?) {
        val position = player.position()
        val last = lastPosition
        val move = if (last != null && player.tickCount == lastTick + 1) position.subtract(last) else null
        val before = lastMove
        if (pressed != null && move != null && before != null && move.lengthSqr() < MAX_MOVE * MAX_MOVE) {
            if (!player.horizontalCollision) {
                measureLevel(move, before, pressed, player.yRot, player.isSprinting)
            }
            // Only keys held a tick before as well: a fly mode that answers a tick late still moves on the old ones
            val isHeld = lastPressed?.let { it.jump == pressed.jump && it.shift == pressed.shift } == true
            if (!player.verticalCollision && isHeld) {
                measureVertical(move.y, before.y, pressed)
            }
        }
        lastPosition = position
        lastMove = move
        lastTick = player.tickCount
        lastPressed = pressed
    }

    /**
     * Where [player] comes to rest with the keys released now, as its last move fades by the drag every tick.
     */
    fun rest(player: Player): Vec3 {
        val move = lastMove ?: player.deltaMovement
        val coast = drag / (1.0 - drag)
        return player.position().add(move.x * coast, move.y * verticalDrag / (1.0 - verticalDrag), move.z * coast)
    }

    private fun measureLevel(move: Vec3, before: Vec3, pressed: Input, yRot: Float, sprint: Boolean) {
        val strafe = (if (pressed.left) 1.0 else 0.0) - (if (pressed.right) 1.0 else 0.0)
        val forward = (if (pressed.forward) 1.0 else 0.0) - (if (pressed.backward) 1.0 else 0.0)
        if (strafe == 0.0 && forward == 0.0) {
            measureDrag(move, before.x, before.z)
            return
        }

        // Across the keys' direction the motion only fades, so a turn measures the drag with the keys held
        val yaw = Math.toRadians(yRot.toDouble())
        val length = sqrt(strafe * strafe + forward * forward)
        val directionX = (strafe * cos(yaw) - forward * sin(yaw)) / length
        val directionZ = (forward * cos(yaw) + strafe * sin(yaw)) / length
        val along = before.x * directionX + before.z * directionZ
        measureDrag(move, before.x - along * directionX, before.z - along * directionZ)

        val drag = drag
        val added = Vec3(move.x - before.x * drag, 0.0, move.z - before.z * drag).horizontalDistance()
        if (sprint) {
            measuredSprintThrust = blend(measuredSprintThrust, added)
        } else {
            measuredThrust = blend(measuredThrust, added)
        }
    }

    /**
     * Measures the share of the earlier motion ([x], [z]) that [move] kept, where no keys pushed along it.
     */
    private fun measureDrag(move: Vec3, x: Double, z: Double) {
        val lengthSqr = x * x + z * z
        if (lengthSqr <= MIN_MOVE * MIN_MOVE) {
            return
        }
        if (measuredDrag.isNaN()) {
            // The thrust so far was taken against the assumed drag
            measuredThrust = Double.NaN
            measuredSprintThrust = Double.NaN
        }
        val kept = (move.x * x + move.z * z) / lengthSqr
        measuredDrag = blend(measuredDrag, kept.coerceIn(0.0, MAX_DRAG))
    }

    private fun measureVertical(move: Double, before: Double, pressed: Input) {
        if (pressed.jump != pressed.shift) {
            measuredVerticalThrust = blend(measuredVerticalThrust, abs(move - before * verticalDrag))
        } else if (abs(before) > MIN_MOVE) {
            // Falling faster or turning around without keys is gravity or the fly mode's own push, not drag
            val kept = move / before
            if (kept in 0.0..1.0) {
                if (measuredVerticalDrag.isNaN()) measuredVerticalThrust = Double.NaN
                measuredVerticalDrag = blend(measuredVerticalDrag, kept.coerceAtMost(MAX_DRAG))
            }
        }
    }

    private companion object {
        const val SPRINT_SHARE = 0.5
        const val MIN_SPEED = 0.05
        const val COASTING_DRAG = 0.5
        const val MAX_DRAG = 0.95

        /**
         * Blocks a tick below which a move tells nothing about the drag.
         */
        const val MIN_MOVE = 0.02

        /**
         * Blocks a tick above which a move is a teleport or a setback, not flight.
         */
        const val MAX_MOVE = 10.0

        /**
         * Weight of a new measurement against what was measured before.
         */
        const val WEIGHT = 0.5

        fun blend(measured: Double, sample: Double) =
            if (measured.isNaN()) sample else measured + (sample - measured) * WEIGHT
    }
}
