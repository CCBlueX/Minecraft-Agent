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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.TagKey

/**
 * One state of a block. The adapter makes one instance per state of the game, so states compare with `===`.
 *
 * @agent claude-opus-5-5
 */
public abstract class BlockState(public val block: Block) {

    public abstract val isAir: Boolean

    public abstract val fluidState: FluidState

    /**
     * The boxes that stop movement, relative to the block's corner. Shapes of fences, panes and stairs depend on the
     * blocks around [pos].
     */
    public abstract fun getCollisionShape(level: BlockGetter, pos: BlockPos): VoxelShape

    /**
     * The outline a crosshair or a click hits.
     */
    public abstract fun getShape(level: BlockGetter, pos: BlockPos): VoxelShape

    /**
     * The hardness; -1 for unbreakable blocks.
     */
    public abstract fun getDestroySpeed(level: BlockGetter, pos: BlockPos): Float

    public abstract fun canBeReplaced(): Boolean

    public abstract fun requiresCorrectToolForDrops(): Boolean

    public abstract fun hasBlockEntity(): Boolean

    public abstract fun isCollisionShapeFullBlock(level: BlockGetter, pos: BlockPos): Boolean

    /**
     * Whether the collision shape fills the whole face towards [direction], as the block under a player's feet does.
     *
     * @agent claude-sonnet-5-5
     */
    public fun isFaceSturdy(level: BlockGetter, pos: BlockPos, direction: Direction): Boolean {
        val axis = direction.axis
        val side = if (direction.isPositive) 1.0 else 0.0
        val (u, v) = Direction.Axis.entries.filter { it != axis }
        val touching = getCollisionShape(level, pos).toAabbs().filter {
            (if (direction.isPositive) it.max(axis) else it.min(axis)) == side
        }
        // The face is full when every cell the box edges cut it into lies under a box
        fun cuts(a: Direction.Axis) = (touching.flatMap { listOf(it.min(a), it.max(a)) } + listOf(0.0, 1.0))
            .distinct().sorted().zipWithNext { from, to -> (from + to) / 2 }.filter { it in 0.0..1.0 }
        return touching.isNotEmpty() && cuts(u).all { cu ->
            cuts(v).all { cv -> touching.any { it.min(u) <= cu && cu <= it.max(u) && it.min(v) <= cv && cv <= it.max(v) } }
        }
    }

    public abstract fun <T : Any> getOptionalValue(property: Property<T>): T?

    /**
     * This state with [property] set to [value]; this state when the block has no such property.
     */
    public abstract fun <T : Any> setValue(property: Property<T>, value: T): BlockState

    public fun <T : Any> getValue(property: Property<T>): T =
        getOptionalValue(property) ?: throw IllegalArgumentException("$block has no property ${property.name}")

    public fun hasProperty(property: Property<*>): Boolean = getOptionalValue(property) != null

    /**
     * What a click and the look decide when this block is placed, such as facing, axis, half and hinge; the rest, such
     * as the shape of stairs or the sides a fence joins, follows from the neighbours. Two states of one block were
     * placed the same way when these are equal; empty where an adapter does not tell.
     */
    public open val placedValues: List<Any> get() = emptyList()

    public fun `is`(block: Block): Boolean = this.block === block

    public fun `is`(tag: TagKey<Block>): Boolean = block.`is`(tag)

    public fun `is`(kind: BlockKind): Boolean = block.`is`(kind)

    override fun toString(): String = "BlockState{${block.id}}"
}

/**
 * A block state property, by its name in the game, with its values as Kotlin types.
 *
 * @agent claude-opus-5-5
 */
public class Property<T : Any>(public val name: String, public val type: Class<T>) {
    override fun toString(): String = name
}

/**
 * The properties the agents read.
 *
 * @agent claude-opus-5-5
 */
public object BlockStateProperties {
    @JvmField
    public val OPEN: Property<Boolean> = Property("open", Boolean::class.javaObjectType)

    @JvmField
    public val DOUBLE_BLOCK_HALF: Property<DoubleBlockHalf> = Property("half", DoubleBlockHalf::class.java)

    @JvmField
    public val HALF: Property<Half> = Property("half", Half::class.java)

    @JvmField
    public val HORIZONTAL_FACING: Property<net.ccbluex.agent.math.Direction> =
        Property("facing", net.ccbluex.agent.math.Direction::class.java)

    @JvmField
    public val SLAB_TYPE: Property<SlabType> = Property("type", SlabType::class.java)

    @JvmField
    public val BED_PART: Property<BedPart> = Property("part", BedPart::class.java)
}

/**
 * @agent claude-opus-5-5
 */
public enum class DoubleBlockHalf { UPPER, LOWER }

/**
 * @agent claude-opus-5-5
 */
public enum class Half { TOP, BOTTOM }

/**
 * @agent claude-opus-5-5
 */
public enum class SlabType { TOP, BOTTOM, DOUBLE }

/**
 * @agent claude-opus-5-5
 */
public enum class BedPart { HEAD, FOOT }

/**
 * @agent claude-opus-5-5
 */
public enum class Fluid {
    EMPTY,
    WATER,
    LAVA,
    ;

    /**
     * Whether both are the same liquid, flowing or still.
     */
    public fun isSame(other: Fluid): Boolean = this == other
}

/**
 * @agent claude-opus-5-5
 */
public object Fluids {
    @JvmField
    public val EMPTY: Fluid = Fluid.EMPTY

    @JvmField
    public val WATER: Fluid = Fluid.WATER

    @JvmField
    public val LAVA: Fluid = Fluid.LAVA
}

/**
 * The liquid in a block: its kind, whether it is a source, and its height in eighths (8 for a source).
 *
 * @agent claude-opus-5-5
 */
public class FluidState(public val type: Fluid, public val isSource: Boolean, public val amount: Int) {

    public val isEmpty: Boolean get() = type == Fluid.EMPTY

    override fun equals(other: Any?): Boolean =
        other is FluidState && other.type == type && other.isSource == isSource && other.amount == amount

    override fun hashCode(): Int = (type.hashCode() * 31 + isSource.hashCode()) * 31 + amount

    public companion object {
        @JvmField
        public val EMPTY: FluidState = FluidState(Fluid.EMPTY, false, 0)
    }
}
