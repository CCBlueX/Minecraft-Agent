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

package net.ccbluex.agent.world.block

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Direction

/**
 * Boxes relative to a block's corner, six numbers each: minimum x, y, z, then maximum x, y, z. Shapes are compared by
 * their boxes; [Shapes.block] and [Shapes.empty] are single instances the adapters reuse, so `===` finds them.
 *
 * @agent claude-opus-5-5
 */
public class VoxelShape(private val boxes: DoubleArray) {

    init {
        require(boxes.size % BOX == 0) { "A shape needs six numbers per box" }
    }

    public val isEmpty: Boolean get() = boxes.isEmpty()

    public val boxCount: Int get() = boxes.size / BOX

    private val aabbs: List<AABB> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        List(boxCount) {
            val o = it * BOX
            AABB(boxes[o], boxes[o + 1], boxes[o + 2], boxes[o + 3], boxes[o + 4], boxes[o + 5])
        }
    }

    public fun toAabbs(): List<AABB> = aabbs

    /**
     * A copy of the boxes, six numbers each.
     */
    public fun toArray(): DoubleArray = boxes.copyOf()

    public fun min(axis: Direction.Axis): Double {
        if (isEmpty) return Double.POSITIVE_INFINITY
        var min = Double.POSITIVE_INFINITY
        for (o in boxes.indices step BOX) min = minOf(min, boxes[o + axis.ordinal])
        return min
    }

    public fun max(axis: Direction.Axis): Double {
        if (isEmpty) return Double.NEGATIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (o in boxes.indices step BOX) max = maxOf(max, boxes[o + AXES + axis.ordinal])
        return max
    }

    public fun bounds(): AABB {
        if (isEmpty) throw UnsupportedOperationException("No bounds for empty shape.")
        return AABB(
            min(Direction.Axis.X), min(Direction.Axis.Y), min(Direction.Axis.Z),
            max(Direction.Axis.X), max(Direction.Axis.Y), max(Direction.Axis.Z),
        )
    }

    public fun move(x: Double, y: Double, z: Double): VoxelShape {
        if (isEmpty) return this
        val moved = boxes.copyOf()
        for (o in moved.indices step BOX) {
            moved[o] += x
            moved[o + 1] += y
            moved[o + 2] += z
            moved[o + 3] += x
            moved[o + 4] += y
            moved[o + 5] += z
        }
        return VoxelShape(moved)
    }

    /**
     * How far [box] moves along [axis] of [distance] before one of these boxes stops it, as the game's shapes do.
     */
    public fun collide(axis: Direction.Axis, box: AABB, distance: Double): Double {
        if (isEmpty || Math.abs(distance) < EPSILON) return if (isEmpty) distance else 0.0
        val a = axis.ordinal
        val b = (a + 1) % AXES
        val c = (a + 2) % AXES
        var moved = distance
        for (o in boxes.indices step BOX) {
            if (boxes[o + AXES + b] - EPSILON <= box.min(Direction.Axis.entries[b]) ||
                boxes[o + b] + EPSILON >= box.max(Direction.Axis.entries[b]) ||
                boxes[o + AXES + c] - EPSILON <= box.min(Direction.Axis.entries[c]) ||
                boxes[o + c] + EPSILON >= box.max(Direction.Axis.entries[c])
            ) {
                continue
            }
            if (moved > 0.0) {
                val gap = boxes[o + a] - box.max(axis)
                if (gap > -EPSILON) moved = minOf(moved, gap)
            } else {
                val gap = boxes[o + AXES + a] - box.min(axis)
                if (gap < EPSILON) moved = maxOf(moved, gap)
            }
        }
        return moved
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is VoxelShape && boxes.contentEquals(other.boxes)

    override fun hashCode(): Int = boxes.contentHashCode()

    override fun toString(): String = "VoxelShape$aabbs"

    internal companion object {
        const val AXES = 3
        const val BOX = 2 * AXES
        const val EPSILON = 1.0E-7
    }
}

/**
 * @agent claude-opus-5-5
 */
public object Shapes {

    public const val EPSILON: Double = 1.0E-7

    private val BLOCK = VoxelShape(doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
    private val EMPTY = VoxelShape(DoubleArray(0))

    @JvmStatic
    public fun block(): VoxelShape = BLOCK

    @JvmStatic
    public fun empty(): VoxelShape = EMPTY

    @JvmStatic
    public fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): VoxelShape =
        if (minX == 0.0 && minY == 0.0 && minZ == 0.0 && maxX == 1.0 && maxY == 1.0 && maxZ == 1.0) BLOCK else
            VoxelShape(doubleArrayOf(minX, minY, minZ, maxX, maxY, maxZ))

    /**
     * How far [box] moves along [axis] of [distance] through all [shapes].
     */
    @JvmStatic
    public fun collide(axis: Direction.Axis, box: AABB, shapes: Iterable<VoxelShape>, distance: Double): Double {
        var moved = distance
        for (shape in shapes) {
            if (Math.abs(moved) < VoxelShape.EPSILON) return 0.0
            moved = shape.collide(axis, box, moved)
        }
        return moved
    }
}

/**
 * The kind of a block entity, as far as the agents care.
 *
 * @agent claude-opus-5-5
 */
public enum class BlockEntityType {
    CHEST,

    /**
     * Holds items but is no chest: barrels, hoppers, furnaces, shulker boxes.
     */
    CONTAINER,
    OTHER,
}

/**
 * The block tags the agents read.
 *
 * @agent claude-opus-5-5
 */
public object BlockTags {
    @JvmField
    public val BASE_STONE_NETHER: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("base_stone_nether")

    @JvmField
    public val BASE_STONE_OVERWORLD: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("base_stone_overworld")

    @JvmField
    public val IMPERMEABLE: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("impermeable")

    @JvmField
    public val LEAVES: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("leaves")

    @JvmField
    public val LOGS: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("logs")

    @JvmField
    public val ORES: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("ores")

    @JvmField
    public val OVERWORLD_NATURAL_LOGS: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("overworld_natural_logs")

    @JvmField
    public val PLANKS: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("planks")

    @JvmField
    public val RAILS: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("rails")

    @JvmField
    public val SAND: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("sand")

    @JvmField
    public val SNOW: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("snow")

    @JvmField
    public val SUBSTRATE_OVERWORLD: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("substrate_overworld")

    @JvmField
    public val SUPPORTS_CROPS: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("supports_crops")

    @JvmField
    public val SUPPORTS_NETHER_WART: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("supports_nether_wart")

    @JvmField
    public val TURNS_INTO_FARMLAND: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("turns_into_farmland")

    @JvmField
    public val WOOL: net.ccbluex.agent.world.TagKey<Block> = net.ccbluex.agent.world.TagKey.of("wool")
}
