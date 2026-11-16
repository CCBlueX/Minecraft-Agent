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

package net.ccbluex.agent.math

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs
import kotlin.math.roundToInt

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f

/**
 * Keys pressed within this many degrees of a key's border count as not pressed.
 */
private const val DEAD_ANGLE = 20f

/**
 * Directions closer than this (squared, horizontally) are no direction.
 */
private const val MIN_DIRECTION_SQR = 1.0E-6

/**
 * From LiquidBounce: [DirectionalInput.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/DirectionalInput.kt#L78-L78)
 *
 * @agent claude-opus-5-5
 */
@JvmField
public val DIRECTION_FORWARDS: Input = directionalInput(forwards = true, backwards = false, left = false, right = false)

/**
 * The four movement keys, with jump, sneak and sprint released.
 *
 * From LiquidBounce: [DirectionalInput.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/DirectionalInput.kt#L29-L34)
 *
 * @agent claude-opus-5-5
 */
public fun directionalInput(forwards: Boolean, backwards: Boolean, left: Boolean, right: Boolean): Input =
    Input(forwards, backwards, left, right, false, false, false)

/**
 * Whether the movement keys move the player: opposite keys cancel out.
 *
 * From LiquidBounce: [InputExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/InputExtensions.kt#L26-L27)
 *
 * @agent claude-opus-5-5
 */
public val Input.isMoving: Boolean
    get() = forward != backward || left != right

/**
 * From LiquidBounce: [InputExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/InputExtensions.kt#L29-L47)
 */
@Suppress("LongParameterList")
public fun Input.copy(
    forward: Boolean = this.forward,
    backward: Boolean = this.backward,
    left: Boolean = this.left,
    right: Boolean = this.right,
    jump: Boolean = this.jump,
    sneak: Boolean = this.shift,
    sprint: Boolean = this.sprint,
): Input = Input(forward, backward, left, right, jump, sneak, sprint)

/**
 * These jump, sneak and sprint keys with the movement keys of [direction].
 *
 * @agent claude-opus-5-5
 */
public fun Input.withDirection(direction: Input): Input =
    Input(direction.forward, direction.backward, direction.left, direction.right, jump, shift, sprint)

/**
 * The movement keys of this input, pressed relative to a view at [fromYRot], turned to move the nearest same way
 * relative to a view at [toYRot]; jump, shift and sprint stay.
 *
 * @agent claude-opus-5-5
 */
public fun Input.turned(fromYRot: Float, toYRot: Float): Input {
    val delta = fromYRot - toYRot
    if (delta == 0f) {
        return this
    }

    val strafe = axis(left, right)
    val forward = axis(this.forward, backward)
    val radians = (delta * Mth.DEG_TO_RAD).toDouble()
    val cos = Mth.cos(radians)
    val sin = Mth.sin(radians)
    val turnedStrafe = (strafe * cos - forward * sin).roundToInt()
    val turnedForward = (forward * cos + strafe * sin).roundToInt()
    return Input(turnedForward > 0, turnedForward < 0, turnedStrafe > 0, turnedStrafe < 0, jump, shift, sprint)
}

/**
 * 1 for only the positive key, -1 for only the negative one, else 0.
 */
private fun axis(positive: Boolean, negative: Boolean): Int = when {
    positive == negative -> 0
    positive -> 1
    else -> -1
}

/**
 * Movement keys that walk towards [direction] when the game applies them relative to [frameYRot]; none for a
 * direction without a horizontal part.
 *
 * From LiquidBounce: [MovementUtils.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/MovementUtils.kt#L84-L90)
 *
 * @agent claude-opus-5-5
 */
public fun getDirectionalInputTowards(direction: Vec3, frameYRot: Float): Input {
    if (direction.horizontalDistanceSqr() < MIN_DIRECTION_SQR) {
        return Input.EMPTY
    }

    val angle = Mth.wrapDegrees(direction.yRot - Mth.wrapDegrees(frameYRot))
    val ahead = abs(angle)
    return directionalInput(
        forwards = ahead < QUARTER_TURN - DEAD_ANGLE,
        backwards = ahead > QUARTER_TURN + DEAD_ANGLE,
        left = angle < -DEAD_ANGLE && angle > -(HALF_TURN - DEAD_ANGLE),
        right = angle > DEAD_ANGLE && angle < HALF_TURN - DEAD_ANGLE,
    )
}

/**
 * The yaw the movement keys act relative to: [strictMovementYRot], the rotation the host turns the movement to
 * under strict movement correction, else the player's view.
 *
 * From LiquidBounce: [MovementUtils.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/MovementUtils.kt#L93-L96)
 *
 * @agent claude-opus-5-5
 */
public fun inputYRot(player: Player, strictMovementYRot: Float? = null): Float = strictMovementYRot ?: player.yRot

/**
 * Keys that move [forward] and [strafe] (-1, 0 or 1, strafe positive to the left) relative to the movement yaw.
 * With [silentYRot], the rotation a host sends while the camera stays at [frameYRot] and the keys are turned onto
 * that rotation, the keys are picked so the turned ones come out as [forward] and [strafe].
 *
 * From LiquidBounce: [MovementUtils.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/MovementUtils.kt#L99-L119)
 *
 * @agent claude-opus-5-5
 */
public fun getDirectionalInputForYRot(
    forward: Int,
    strafe: Int,
    frameYRot: Float,
    silentYRot: Float? = null,
): Input {
    if (silentYRot == null) {
        return axisInput(forward, strafe)
    }

    val turn = ((frameYRot - silentYRot) * Mth.DEG_TO_RAD).toDouble()
    val cos = Mth.cos(turn)
    val sin = Mth.sin(turn)
    for (pressedForward in -1..1) {
        for (pressedStrafe in -1..1) {
            val turnedStrafe = (pressedStrafe * cos - pressedForward * sin).roundToInt()
            val turnedForward = (pressedForward * cos + pressedStrafe * sin).roundToInt()
            if (turnedStrafe == strafe && turnedForward == forward) {
                return axisInput(pressedForward, pressedStrafe)
            }
        }
    }
    return axisInput(forward, strafe)
}

/**
 * @agent claude-opus-5-5
 */
private fun axisInput(forward: Int, strafe: Int): Input =
    directionalInput(forwards = forward > 0, backwards = forward < 0, left = strafe > 0, right = strafe < 0)

/**
 * The yaw the player walks at when it faces [facingYRot] and holds the movement keys of [input].
 *
 * From LiquidBounce: [EntityExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/EntityExtensions.kt#L334-L354)
 *
 * @agent claude-opus-5-5
 */
public fun getInputYRot(facingYRot: Float, input: Input): Float {
    val forward = input.forward && !input.backward
    val backward = input.backward && !input.forward
    val sideways = when {
        backward -> -QUARTER_TURN / 2
        forward -> QUARTER_TURN / 2
        else -> QUARTER_TURN
    }

    var yRot = if (backward) facingYRot + HALF_TURN else facingYRot
    if (input.left && !input.right) {
        yRot -= sideways
    }
    if (input.right && !input.left) {
        yRot += sideways
    }
    return yRot
}

/**
 * [getInputYRot] for this player's view.
 *
 * From LiquidBounce: [EntityExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/EntityExtensions.kt#L334-L354)
 *
 * @agent claude-opus-5-5
 */
public fun Player.getInputYRot(input: Input): Float = getInputYRot(yRot, input)
