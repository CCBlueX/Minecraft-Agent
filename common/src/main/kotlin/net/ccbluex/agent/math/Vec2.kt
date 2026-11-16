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
 * Two floats: a rotation (x pitch, y yaw) or the movement keys (x strafe, y forward).
 *
 * @agent claude-opus-5-5
 */
public class Vec2(@JvmField public val x: Float, @JvmField public val y: Float) {

    public fun scale(factor: Float): Vec2 = Vec2(x * factor, y * factor)

    public fun length(): Float = Math.sqrt(lengthSquared().toDouble()).toFloat()

    public fun lengthSquared(): Float = x * x + y * y

    public fun normalized(): Vec2 {
        val length = length()
        return if (length < 1.0E-4f) ZERO else Vec2(x / length, y / length)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is Vec2 && other.x.compareTo(x) == 0 && other.y.compareTo(y) == 0

    override fun hashCode(): Int = x.hashCode() * 31 + y.hashCode()

    override fun toString(): String = "($x, $y)"

    public companion object {
        @JvmField
        public val ZERO: Vec2 = Vec2(0f, 0f)
    }
}
