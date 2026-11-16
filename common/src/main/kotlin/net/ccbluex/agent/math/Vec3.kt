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
 * A point or a movement; the same arithmetic as the game's, so results match to the last bit.
 *
 * @agent claude-opus-5-5
 */
public class Vec3(
    @JvmField public val x: Double,
    @JvmField public val y: Double,
    @JvmField public val z: Double,
) {

    public fun add(x: Double, y: Double, z: Double): Vec3 = Vec3(this.x + x, this.y + y, this.z + z)

    public fun add(other: Vec3): Vec3 = add(other.x, other.y, other.z)

    public fun add(value: Double): Vec3 = add(value, value, value)

    public fun subtract(x: Double, y: Double, z: Double): Vec3 = add(-x, -y, -z)

    public fun subtract(other: Vec3): Vec3 = subtract(other.x, other.y, other.z)

    public fun vectorTo(other: Vec3): Vec3 = Vec3(other.x - x, other.y - y, other.z - z)

    public fun multiply(x: Double, y: Double, z: Double): Vec3 = Vec3(this.x * x, this.y * y, this.z * z)

    public fun multiply(other: Vec3): Vec3 = multiply(other.x, other.y, other.z)

    public fun scale(factor: Double): Vec3 = multiply(factor, factor, factor)

    public fun reverse(): Vec3 = scale(-1.0)

    public fun horizontal(): Vec3 = Vec3(x, 0.0, z)

    public fun normalize(): Vec3 {
        val length = Math.sqrt(x * x + y * y + z * z)
        return if (length < 1.0E-5f) ZERO else Vec3(x / length, y / length, z / length)
    }

    public fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z

    public fun cross(other: Vec3): Vec3 =
        Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    public fun length(): Double = Math.sqrt(lengthSqr())

    public fun lengthSqr(): Double = x * x + y * y + z * z

    public fun horizontalDistance(): Double = Math.sqrt(horizontalDistanceSqr())

    public fun horizontalDistanceSqr(): Double = x * x + z * z

    public fun distanceToSqr(x: Double, y: Double, z: Double): Double {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        return dx * dx + dy * dy + dz * dz
    }

    public fun distanceToSqr(other: Vec3): Double = distanceToSqr(other.x, other.y, other.z)

    public fun distanceTo(other: Vec3): Double = Math.sqrt(distanceToSqr(other))

    public fun closerThan(other: Vec3, distance: Double): Boolean = distanceToSqr(other) < distance * distance

    public fun closerThan(other: Vec3, horizontal: Double, vertical: Double): Boolean =
        Mth.lengthSquared(other.x - x, other.z - z) < Mth.square(horizontal) && Math.abs(other.y - y) < vertical

    public fun lerp(other: Vec3, alpha: Double): Vec3 =
        Vec3(Mth.lerp(alpha, x, other.x), Mth.lerp(alpha, y, other.y), Mth.lerp(alpha, z, other.z))

    public fun get(axis: Direction.Axis): Double = axis.choose(x, y, z)

    public fun with(axis: Direction.Axis, value: Double): Vec3 = when (axis) {
        Direction.Axis.X -> Vec3(value, y, z)
        Direction.Axis.Y -> Vec3(x, value, z)
        Direction.Axis.Z -> Vec3(x, y, value)
    }

    public fun relative(direction: Direction, distance: Double): Vec3 =
        add(direction.stepX * distance, direction.stepY * distance, direction.stepZ * distance)

    public fun yRot(radians: Float): Vec3 {
        val cos = Mth.cos(radians.toDouble())
        val sin = Mth.sin(radians.toDouble())
        return Vec3(x * cos + z * sin, y, z * cos - x * sin)
    }

    public fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    override fun equals(other: Any?): Boolean =
        this === other || other is Vec3 && other.x.compareTo(x) == 0 && other.y.compareTo(y) == 0 &&
            other.z.compareTo(z) == 0

    override fun hashCode(): Int = (x.hashCode() * 31 + y.hashCode()) * 31 + z.hashCode()

    override fun toString(): String = "($x, $y, $z)"

    public companion object {
        @JvmField
        public val ZERO: Vec3 = Vec3(0.0, 0.0, 0.0)

        @JvmStatic
        public fun atLowerCornerOf(pos: Vec3i): Vec3 = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

        @JvmStatic
        public fun atCenterOf(pos: Vec3i): Vec3 = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)

        @JvmStatic
        public fun atBottomCenterOf(pos: Vec3i): Vec3 = Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)

        @JvmStatic
        public fun upFromBottomCenterOf(pos: Vec3i, y: Double): Vec3 = Vec3(pos.x + 0.5, pos.y + y, pos.z + 0.5)

        /**
         * The look vector of [xRot] (pitch) and [yRot] (yaw), in degrees.
         */
        @JvmStatic
        public fun directionFromRotation(xRot: Float, yRot: Float): Vec3 {
            val yCos = Mth.cos((-yRot * Mth.DEG_TO_RAD - Mth.PI).toDouble())
            val ySin = Mth.sin((-yRot * Mth.DEG_TO_RAD - Mth.PI).toDouble())
            val xCos = -Mth.cos((-xRot * Mth.DEG_TO_RAD).toDouble())
            val xSin = Mth.sin((-xRot * Mth.DEG_TO_RAD).toDouble())
            return Vec3((ySin * xCos).toDouble(), xSin.toDouble(), (yCos * xCos).toDouble())
        }

        @JvmStatic
        public fun directionFromRotation(rotation: Vec2): Vec3 = directionFromRotation(rotation.x, rotation.y)
    }
}
