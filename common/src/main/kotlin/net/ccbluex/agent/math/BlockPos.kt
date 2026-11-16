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
 * A block's coordinates. [asLong] packs them as the game does.
 *
 * @agent claude-opus-5-5
 */
public open class BlockPos(x: Int, y: Int, z: Int) : Vec3i(x, y, z) {

    public open fun immutable(): BlockPos = this

    public fun asLong(): Long = asLong(x, y, z)

    public open fun offset(x: Int, y: Int, z: Int): BlockPos =
        if (x == 0 && y == 0 && z == 0) this else BlockPos(this.x + x, this.y + y, this.z + z)

    public fun offset(other: Vec3i): BlockPos = offset(other.x, other.y, other.z)

    public fun above(): BlockPos = relative(Direction.UP)

    public fun above(steps: Int): BlockPos = relative(Direction.UP, steps)

    public fun below(): BlockPos = relative(Direction.DOWN)

    public fun below(steps: Int): BlockPos = relative(Direction.DOWN, steps)

    public fun north(): BlockPos = relative(Direction.NORTH)

    public fun south(): BlockPos = relative(Direction.SOUTH)

    public fun west(): BlockPos = relative(Direction.WEST)

    public fun east(): BlockPos = relative(Direction.EAST)

    public open fun relative(direction: Direction): BlockPos =
        BlockPos(x + direction.stepX, y + direction.stepY, z + direction.stepZ)

    public open fun relative(direction: Direction, steps: Int): BlockPos = if (steps == 0) this else
        BlockPos(x + direction.stepX * steps, y + direction.stepY * steps, z + direction.stepZ * steps)

    public fun mutable(): MutableBlockPos = MutableBlockPos(x, y, z)

    /**
     * A position that changes, for loops over many blocks.
     */
    public class MutableBlockPos(x: Int = 0, y: Int = 0, z: Int = 0) : BlockPos(x, y, z) {

        public fun set(x: Int, y: Int, z: Int): MutableBlockPos {
            this.x = x
            this.y = y
            this.z = z
            return this
        }

        public fun set(other: Vec3i): MutableBlockPos = set(other.x, other.y, other.z)

        public fun setX(x: Int): MutableBlockPos = apply { this.x = x }

        public fun setY(y: Int): MutableBlockPos = apply { this.y = y }

        public fun setZ(z: Int): MutableBlockPos = apply { this.z = z }

        public fun move(x: Int, y: Int, z: Int): MutableBlockPos = set(this.x + x, this.y + y, this.z + z)

        public fun move(direction: Direction): MutableBlockPos = move(direction.stepX, direction.stepY, direction.stepZ)

        override fun immutable(): BlockPos = BlockPos(x, y, z)

        override fun offset(x: Int, y: Int, z: Int): BlockPos = BlockPos(this.x + x, this.y + y, this.z + z)

        override fun relative(direction: Direction): BlockPos =
            BlockPos(x + direction.stepX, y + direction.stepY, z + direction.stepZ)

        override fun relative(direction: Direction, steps: Int): BlockPos =
            BlockPos(x + direction.stepX * steps, y + direction.stepY * steps, z + direction.stepZ * steps)
    }

    public companion object {
        private const val HORIZONTAL_BITS = 26
        private const val Y_BITS = 64 - 2 * HORIZONTAL_BITS
        private const val HORIZONTAL_MASK = (1L shl HORIZONTAL_BITS) - 1L
        private const val Y_MASK = (1L shl Y_BITS) - 1L
        private const val Z_OFFSET = Y_BITS
        private const val X_OFFSET = Y_BITS + HORIZONTAL_BITS

        @JvmField
        public val ZERO: BlockPos = BlockPos(0, 0, 0)

        @JvmStatic
        public fun asLong(x: Int, y: Int, z: Int): Long =
            (x.toLong() and HORIZONTAL_MASK shl X_OFFSET) or (y.toLong() and Y_MASK) or
                (z.toLong() and HORIZONTAL_MASK shl Z_OFFSET)

        @JvmStatic
        public fun getX(packed: Long): Int =
            (packed shl 64 - X_OFFSET - HORIZONTAL_BITS shr 64 - HORIZONTAL_BITS).toInt()

        @JvmStatic
        public fun getY(packed: Long): Int = (packed shl 64 - Y_BITS shr 64 - Y_BITS).toInt()

        @JvmStatic
        public fun getZ(packed: Long): Int =
            (packed shl 64 - Z_OFFSET - HORIZONTAL_BITS shr 64 - HORIZONTAL_BITS).toInt()

        @JvmStatic
        public fun of(packed: Long): BlockPos = BlockPos(getX(packed), getY(packed), getZ(packed))

        @JvmStatic
        public fun containing(x: Double, y: Double, z: Double): BlockPos =
            BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))

        @JvmStatic
        public fun containing(pos: Vec3): BlockPos = containing(pos.x, pos.y, pos.z)

        /**
         * Every block from the lower to the upper corner, x fastest, then y, then z; one position reused, call
         * [immutable] to keep one.
         */
        @JvmStatic
        public fun betweenClosed(a: BlockPos, b: BlockPos): Iterable<BlockPos> {
            val minX = minOf(a.x, b.x)
            val minY = minOf(a.y, b.y)
            val minZ = minOf(a.z, b.z)
            val width = maxOf(a.x, b.x) - minX + 1
            val height = maxOf(a.y, b.y) - minY + 1
            val end = width * height * (maxOf(a.z, b.z) - minZ + 1)
            return Iterable {
                object : Iterator<BlockPos> {
                    private val cursor = MutableBlockPos()
                    private var index = 0

                    override fun hasNext() = index < end

                    override fun next(): BlockPos {
                        if (index >= end) throw NoSuchElementException()
                        val slice = index / width
                        cursor.set(minX + index % width, minY + slice % height, minZ + slice / height)
                        index++
                        return cursor
                    }
                }
            }
        }
    }
}

/**
 * Packed chunk coordinates, as the game packs them.
 *
 * @agent claude-opus-5-5
 */
public object ChunkPos {

    @JvmStatic
    public fun pack(x: Int, z: Int): Long = (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
}

/**
 * Packed coordinates of a 16×16×16 section, as the game packs them.
 *
 * @agent claude-opus-5-5
 */
public object SectionPos {

    @JvmStatic
    public fun blockToSectionCoord(coord: Int): Int = coord shr 4

    @JvmStatic
    public fun asLong(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFL shl 42) or (y.toLong() and 0xFFFFFL) or (z.toLong() and 0x3FFFFFL shl 20)

    @JvmStatic
    public fun asLong(pos: BlockPos): Long =
        asLong(blockToSectionCoord(pos.x), blockToSectionCoord(pos.y), blockToSectionCoord(pos.z))

    @JvmStatic
    public fun x(packed: Long): Int = (packed shl 0 shr 42).toInt()

    @JvmStatic
    public fun y(packed: Long): Int = (packed shl 44 shr 44).toInt()

    @JvmStatic
    public fun z(packed: Long): Int = (packed shl 22 shr 42).toInt()
}
