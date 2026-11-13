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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.math.toDegrees
import net.ccbluex.agent.math.yRot
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns the camera the way navigation goes, eased and capped per tick like a person looking ahead. A view taken over
 * by the user or another feature stops it.
 *
 * The turn of a tick is made once the player moved, by [Navigator.turnView], or else at the start of the next tick,
 * so the movement keys of that tick are worked out relative to the same yaw the player then moves with.
 *
 * From LiquidBounce: [LookAhead.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/LookAhead.kt#L59-L186)
 *
 * @agent claude-opus-5-5
 */
internal class LookAhead(private val context: NavigationContext) {

    private val options get() = context.options()

    /**
     * How far along the route the point looked at lies.
     */
    val distance: Float
        get() = options.lookAheadDistance

    private var point: Vec3? = null
    private var aimed = false
    private var yRotSpeed = 0f
    private var xRotSpeed = 0f

    /**
     * Looks towards [point] after this tick, or the way the player moves without one. Call every tick it walks.
     */
    fun aim(point: Vec3?) {
        this.point = point
        aimed = true
    }

    fun reset() {
        point = null
        aimed = false
        yRotSpeed = 0f
        xRotSpeed = 0f
    }

    /**
     * The turn for the tick before; once per tick, before anything else of the navigator.
     */
    fun tick() {
        val wasAimed = aimed
        val point = point
        this.point = null
        aimed = false

        if (!options.lookAhead || context.body.isViewTakenOver) {
            stopTurning()
            return
        }

        val player = context.body.player
        if (wasAimed) {
            val yRot = targetYRot(point)
            val yRotDelta = if (yRot.isNaN()) 0f else Mth.wrapDegrees(yRot - player.yRot)
            turn(yRotDelta, targetXRot(point) - player.xRot)
        } else if (yRotSpeed != 0f || xRotSpeed != 0f) {
            // Navigation stopped: the turn slows down instead of ending at once.
            turn(0f, 0f)
        }
    }

    /**
     * NaN to keep the yaw: the point lies too close to tell a way, or the player stands still.
     */
    private fun targetYRot(point: Vec3?): Float {
        val player = context.body.player
        val position = player.position()
        if (point != null) {
            val near = point.horizontalDistanceToSqr(position) < MIN_POINT_DISTANCE * MIN_POINT_DISTANCE
            return if (near) Float.NaN else point.subtract(position).yRot
        }

        val motion = player.rootVehicle.deltaMovement
        return if (motion.horizontalDistanceSqr() >= MIN_MOTION * MIN_MOTION) motion.yRot else Float.NaN
    }

    /**
     * Towards the horizon, tilted by part of the route's slope up to the point; steep up or down a ladder.
     */
    private fun targetXRot(point: Vec3?): Float {
        val xRot = options.lookAheadXRot
        if (point == null) {
            return xRot
        }

        val position = context.body.player.position()
        val rise = point.y - position.y
        val horizontal = point.horizontalDistanceTo(position)
        if (horizontal < MIN_POINT_DISTANCE && abs(rise) < MIN_POINT_DISTANCE) {
            return xRot
        }

        val slope = atan2(rise, max(horizontal, MIN_POINT_DISTANCE)).toDegrees().toFloat()
        return (xRot - slope * SLOPE_FOLLOW).coerceIn(MIN_X_ROT, MAX_X_ROT)
    }

    /**
     * Eases towards the deltas: the wanted speed shrinks with the angle left and is capped at the turn speed, and the
     * actual speed changes by a limited amount per tick so turns start and end softly.
     */
    private fun turn(yRotDelta: Float, xRotDelta: Float) {
        val turnSpeed = options.lookAheadTurnSpeed
        val maxChange = turnSpeed * ACCELERATION
        yRotSpeed = approach(yRotSpeed, (yRotDelta * EASING).coerceIn(-turnSpeed, turnSpeed), maxChange)
        val maxXRotSpeed = turnSpeed * X_ROT_SHARE
        xRotSpeed = approach(xRotSpeed, (xRotDelta * EASING).coerceIn(-maxXRotSpeed, maxXRotSpeed), maxChange)

        if (abs(yRotSpeed) < REST_SPEED && abs(yRotDelta) < REST_SPEED) {
            yRotSpeed = 0f
        }
        if (abs(xRotSpeed) < REST_SPEED && abs(xRotDelta) < REST_SPEED) {
            xRotSpeed = 0f
        }

        // Steps in whole mouse counts, like a turn made with the mouse.
        val player = context.body.player
        val yRot = player.yRot + steps(yRotSpeed)
        val xRot = (player.xRot + steps(xRotSpeed)).coerceIn(-90f, 90f)
        context.body.look(ViewRotation(yRot, xRot), LookPurpose.VIEW)
    }

    private fun steps(speed: Float): Float {
        val step = context.body.viewStep
        return if (step > 0f) (speed / step).roundToInt() * step else speed
    }

    private fun stopTurning() {
        yRotSpeed = 0f
        xRotSpeed = 0f
    }

    private fun approach(current: Float, wanted: Float, maxChange: Float) =
        current + (wanted - current).coerceIn(-maxChange, maxChange)

    private companion object {
        const val EASING = 0.2f
        const val ACCELERATION = 0.3f
        const val X_ROT_SHARE = 0.5f
        const val SLOPE_FOLLOW = 0.5f
        const val MIN_X_ROT = -30f
        const val MAX_X_ROT = 60f
        const val REST_SPEED = 0.05f
        const val MIN_POINT_DISTANCE = 1.0
        const val MIN_MOTION = 0.05
    }

}
