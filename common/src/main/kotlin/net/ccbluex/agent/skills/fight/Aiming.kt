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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.math.ViewRotation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure aiming math of the fight: rotations, turning with noise, how close an aim has to be and how to throw.
 *
 * @agent claude-opus-5-5
 */
internal object Aiming {

    /**
     * Pitch turns this much slower than yaw, and gets half the yaw's noise.
     */
    private const val X_ROT_SPEED = 0.7
    private const val X_ROT_NOISE = 0.5

    /**
     * Half a hitbox wide plus a margin a click lands within.
     */
    private const val HIT_HALF_WIDTH = 0.45
    private const val HIT_MARGIN = 8.0

    fun rotation(from: Vec3, to: Vec3): ViewRotation {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val yRot = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val xRot = (-Math.toDegrees(atan2(dy, hypot(dx, dz)))).toFloat()
        return ViewRotation(yRot, xRot)
    }

    /**
     * The unit vector a rotation looks along.
     */
    fun direction(rotation: ViewRotation): Vec3 {
        val yRot = Math.toRadians(rotation.yRot.toDouble())
        val xRot = Math.toRadians(rotation.xRot.toDouble())
        return Vec3(-sin(yRot) * cos(xRot), -sin(xRot), cos(yRot) * cos(xRot))
    }

    /**
     * One tick of turning from [current] towards [want] at [speed] degrees a tick at most, plus the noise: yaw gets
     * [yRotNoise] times [noise], pitch turns 0.7 times as fast and gets half the noise. Constant speed, no easing.
     */
    fun step(
        current: ViewRotation,
        want: ViewRotation,
        speed: Double,
        noise: Double,
        yRotNoise: Double,
        xRotNoise: Double,
    ): ViewRotation {
        val dyRot = Mth.wrapDegrees(want.yRot - current.yRot).toDouble()
        val dxRot = (want.xRot - current.xRot).toDouble()
        val yRot = current.yRot + sign(dyRot) * min(abs(dyRot), speed) + yRotNoise * noise
        val xRot = current.xRot + sign(dxRot) * min(abs(dxRot), speed * X_ROT_SPEED) +
            xRotNoise * noise * X_ROT_NOISE
        return ViewRotation(Mth.wrapDegrees(yRot.toFloat()), xRot.toFloat().coerceIn(-90f, 90f))
    }

    fun aimed(current: ViewRotation, want: ViewRotation, tolerance: Double): Boolean =
        abs(Mth.wrapDegrees(want.yRot - current.yRot)) <= tolerance && abs(want.xRot - current.xRot) <= tolerance

    /**
     * How far off an aim may be at [distance] for a click to land on a player's hitbox, in degrees.
     */
    fun hitTolerance(distance: Double): Double = Math.toDegrees(atan2(HIT_HALF_WIDTH, max(0.5, distance))) + HIT_MARGIN

    /**
     * The point of [entity]'s hitbox closest to [eye].
     */
    fun nearestPoint(entity: Entity, eye: Vec3): Vec3 {
        val box = entity.boundingBox
        return Vec3(
            eye.x.coerceIn(box.minX, box.maxX),
            eye.y.coerceIn(box.minY, box.maxY),
            eye.z.coerceIn(box.minZ, box.maxZ),
        )
    }

    fun horizontal(a: Vec3, b: Vec3): Double = hypot(a.x - b.x, a.z - b.z)

    /**
     * The rotation that throws a projectile of [speed] and [gravity] (blocks a tick) from [eye] onto [point], led by
     * the target's [motion] a tick over the flight; the low arc when it reaches, a straight line when not.
     */
    fun throwAt(eye: Vec3, point: Vec3, motion: Vec3, speed: Double, gravity: Double): ViewRotation {
        val flight = hypot(point.x - eye.x, point.z - eye.z) / speed
        val aimed = Vec3(point.x + motion.x * flight, point.y, point.z + motion.z * flight)
        val straight = rotation(eye, aimed)
        val x = hypot(aimed.x - eye.x, aimed.z - eye.z)
        val y = aimed.y - eye.y
        val v2 = speed * speed
        val root = v2 * v2 - gravity * (gravity * x * x + 2 * y * v2)
        if (root < 0 || x <= 0.5) return straight
        val xRot = -Math.toDegrees(atan((v2 - sqrt(root)) / (gravity * x)))
        return ViewRotation(straight.yRot, xRot.toFloat())
    }
}
