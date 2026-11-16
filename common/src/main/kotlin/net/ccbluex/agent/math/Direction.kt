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
 * The six faces of a block, in the game's order.
 *
 * @agent claude-opus-5-5
 */
public enum class Direction(
    public val stepX: Int,
    public val stepY: Int,
    public val stepZ: Int,
    public val axis: Axis,
    private val oppositeIndex: Int,
    /**
     * 0 south, 1 west, 2 north, 3 east; -1 up and down.
     */
    public val get2DDataValue: Int,
) {
    DOWN(0, -1, 0, Axis.Y, 1, -1),
    UP(0, 1, 0, Axis.Y, 0, -1),
    NORTH(0, 0, -1, Axis.Z, 3, 2),
    SOUTH(0, 0, 1, Axis.Z, 2, 0),
    WEST(-1, 0, 0, Axis.X, 5, 1),
    EAST(1, 0, 0, Axis.X, 4, 3),
    ;

    public val unitVec3: Vec3 = Vec3(stepX.toDouble(), stepY.toDouble(), stepZ.toDouble())

    public val unitVec3i: Vec3i = Vec3i(stepX, stepY, stepZ)

    public val opposite: Direction
        get() = entries[oppositeIndex]

    public val isPositive: Boolean
        get() = stepX + stepY + stepZ > 0

    public fun getClockWise(): Direction = when (this) {
        NORTH -> EAST
        EAST -> SOUTH
        SOUTH -> WEST
        WEST -> NORTH
        else -> throw IllegalStateException("Unable to get Y-rotated facing of $this")
    }

    public fun getCounterClockWise(): Direction = when (this) {
        NORTH -> WEST
        WEST -> SOUTH
        SOUTH -> EAST
        EAST -> NORTH
        else -> throw IllegalStateException("Unable to get CCW facing of $this")
    }

    /**
     * The three axes.
     */
    public enum class Axis {
        X, Y, Z;

        public val isHorizontal: Boolean
            get() = this != Y

        public val isVertical: Boolean
            get() = this == Y

        public fun choose(x: Double, y: Double, z: Double): Double = when (this) {
            X -> x
            Y -> y
            Z -> z
        }

        public fun choose(x: Int, y: Int, z: Int): Int = when (this) {
            X -> x
            Y -> y
            Z -> z
        }
    }

    /**
     * The horizontal faces, in the game's order (north, east, south, west), or the vertical ones.
     */
    public enum class Plane(private val faces: List<Direction>) : Iterable<Direction> {
        HORIZONTAL(listOf(NORTH, EAST, SOUTH, WEST)),
        VERTICAL(listOf(UP, DOWN)),
        ;

        override fun iterator(): Iterator<Direction> = faces.iterator()

        public fun test(direction: Direction): Boolean = direction in faces
    }

    public companion object {
        private val Y_X_Z = listOf(Axis.Y, Axis.X, Axis.Z)
        private val Y_Z_X = listOf(Axis.Y, Axis.Z, Axis.X)

        /**
         * The order the game resolves collisions in: y first, then the axis with the longer movement.
         */
        @JvmStatic
        public fun axisStepOrder(movement: Vec3): List<Axis> =
            if (Math.abs(movement.x) < Math.abs(movement.z)) Y_Z_X else Y_X_Z

        @JvmStatic
        public fun getApproximateNearest(x: Double, y: Double, z: Double): Direction {
            val fx = x.toFloat()
            val fy = y.toFloat()
            val fz = z.toFloat()
            var nearest = NORTH
            var best = Float.MIN_VALUE
            for (direction in entries) {
                val dot = fx * direction.stepX + fy * direction.stepY + fz * direction.stepZ
                if (dot > best) {
                    best = dot
                    nearest = direction
                }
            }
            return nearest
        }

        @JvmStatic
        public fun getApproximateNearest(vector: Vec3): Direction = getApproximateNearest(vector.x, vector.y, vector.z)

        @JvmStatic
        public fun from2DDataValue(value: Int): Direction = when (Math.floorMod(value, 4)) {
            0 -> SOUTH
            1 -> WEST
            2 -> NORTH
            else -> EAST
        }

        @JvmStatic
        public fun fromYRot(yRot: Double): Direction = from2DDataValue(Mth.floor(yRot / 90.0 + 0.5) and 3)
    }
}
