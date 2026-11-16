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

/**
 * The game's number helpers, with the same results: [sin] and [cos] read the same 65536-entry table the game reads.
 *
 * @agent claude-opus-5-5
 */
public object Mth {

    public const val PI: Float = Math.PI.toFloat()
    public const val DEG_TO_RAD: Float = (Math.PI / 180.0).toFloat()
    public const val RAD_TO_DEG: Float = 180f / PI
    public val SQRT_OF_TWO: Float = kotlin.math.sqrt(2f)

    private const val SIN_SCALE = 10430.378350470453
    private val SIN = FloatArray(65536) { Math.sin(it / SIN_SCALE).toFloat() }

    @JvmStatic
    public fun sin(value: Double): Float = SIN[((value * SIN_SCALE).toLong() and 0xFFFF).toInt()]

    @JvmStatic
    public fun cos(value: Double): Float = SIN[((value * SIN_SCALE + 16384.0).toLong() and 0xFFFF).toInt()]

    @JvmStatic
    public fun sqrt(value: Float): Float = Math.sqrt(value.toDouble()).toFloat()

    @JvmStatic
    public fun floor(value: Double): Int = Math.floor(value).toInt()

    @JvmStatic
    public fun floor(value: Float): Int = Math.floor(value.toDouble()).toInt()

    @JvmStatic
    public fun ceil(value: Double): Int = Math.ceil(value).toInt()

    @JvmStatic
    public fun equal(a: Double, b: Double): Boolean = Math.abs(b - a) < 1.0E-5f

    @JvmStatic
    public fun equal(a: Float, b: Float): Boolean = Math.abs(b - a) < 1.0E-5f

    @JvmStatic
    public fun wrapDegrees(angle: Double): Double {
        var wrapped = angle % 360.0
        if (wrapped >= 180.0) wrapped -= 360.0
        if (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    @JvmStatic
    public fun wrapDegrees(angle: Float): Float {
        var wrapped = angle % 360f
        if (wrapped >= 180f) wrapped -= 360f
        if (wrapped < -180f) wrapped += 360f
        return wrapped
    }

    @JvmStatic
    public fun lerp(alpha: Double, from: Double, to: Double): Double = from + alpha * (to - from)

    @JvmStatic
    public fun lerp(alpha: Float, from: Float, to: Float): Float = from + alpha * (to - from)

    @JvmStatic
    public fun square(value: Double): Double = value * value

    @JvmStatic
    public fun square(value: Float): Float = value * value

    @JvmStatic
    public fun lengthSquared(x: Double, y: Double): Double = x * x + y * y

    @JvmStatic
    public fun lengthSquared(x: Double, y: Double, z: Double): Double = x * x + y * y + z * z

    @JvmStatic
    public fun length(x: Double, y: Double): Double = Math.sqrt(lengthSquared(x, y))

    @JvmStatic
    public fun length(x: Double, y: Double, z: Double): Double = Math.sqrt(lengthSquared(x, y, z))

    @JvmStatic
    public fun clamp(value: Double, min: Double, max: Double): Double = if (value < min) min else minOf(value, max)

    @JvmStatic
    public fun clamp(value: Float, min: Float, max: Float): Float = if (value < min) min else minOf(value, max)

    @JvmStatic
    public fun clamp(value: Int, min: Int, max: Int): Int = minOf(maxOf(value, min), max)
}
