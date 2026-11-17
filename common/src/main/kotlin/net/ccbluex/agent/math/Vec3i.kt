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
 * Three whole coordinates; equal and hashed as the game's, so maps and sets keep their order.
 *
 * @agent claude-opus-5-5
 */
public open class Vec3i(x: Int, y: Int, z: Int) : Comparable<Vec3i> {

    public var x: Int = x
        protected set
    public var y: Int = y
        protected set
    public var z: Int = z
        protected set

    public fun subtract(other: Vec3i): Vec3i = Vec3i(x - other.x, y - other.y, z - other.z)

    public fun distSqr(other: Vec3i): Double {
        val dx = (x - other.x).toDouble()
        val dy = (y - other.y).toDouble()
        val dz = (z - other.z).toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    public fun closerThan(other: Vec3i, distance: Double): Boolean = distSqr(other) < Mth.square(distance)

    public fun distToCenterSqr(x: Double, y: Double, z: Double): Double {
        val dx = this.x + 0.5 - x
        val dy = this.y + 0.5 - y
        val dz = this.z + 0.5 - z
        return dx * dx + dy * dy + dz * dz
    }

    public fun distToCenterSqr(pos: Vec3): Double = distToCenterSqr(pos.x, pos.y, pos.z)

    public fun distManhattan(other: Vec3i): Int =
        Math.abs(other.x - x) + Math.abs(other.y - y) + Math.abs(other.z - z)

    override fun compareTo(other: Vec3i): Int = when {
        y != other.y -> y - other.y
        z != other.z -> z - other.z
        else -> x - other.x
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is Vec3i && other.x == x && other.y == y && other.z == z

    override fun hashCode(): Int = (y + z * 31) * 31 + x

    override fun toString(): String = "[$x, $y, $z]"
}
