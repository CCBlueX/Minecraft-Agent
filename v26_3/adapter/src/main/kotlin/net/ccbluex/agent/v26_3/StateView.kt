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

package net.ccbluex.agent.v26_3

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.BedPart
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.DoubleBlockHalf
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.block.FluidState
import net.ccbluex.agent.world.block.Half
import net.ccbluex.agent.world.block.Property
import net.ccbluex.agent.world.block.Shapes
import net.ccbluex.agent.world.block.SlabType
import net.ccbluex.agent.world.block.VoxelShape
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Fluids
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReferenceArray
import net.minecraft.world.level.BlockGetter as McBlockGetter
import net.minecraft.world.level.block.state.BlockState as McBlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties as McProperties
import net.minecraft.world.level.block.state.properties.Property as McProperty
import net.minecraft.world.level.material.FluidState as McFluidState
import net.minecraft.world.phys.shapes.Shapes as McShapes
import net.minecraft.world.phys.shapes.VoxelShape as McVoxelShape

/**
 * One state of the game's blocks; one instance per state, made by [toAgent].
 *
 * @agent claude-opus-5-5
 */
internal class StateView(val handle: McBlockState) : BlockState(handle.block.toAgent()) {

    override val isAir: Boolean = handle.isAir

    override val fluidState: FluidState = handle.fluidState.toAgent()

    override fun getCollisionShape(level: BlockGetter, pos: BlockPos): VoxelShape =
        handle.getCollisionShape(level.toMinecraft(), pos.toMinecraft()).toAgent()

    override fun getShape(level: BlockGetter, pos: BlockPos): VoxelShape =
        handle.getShape(level.toMinecraft(), pos.toMinecraft()).toAgent()

    override fun getDestroySpeed(level: BlockGetter, pos: BlockPos): Float =
        handle.getDestroySpeed(level.toMinecraft(), pos.toMinecraft())

    override fun canBeReplaced(): Boolean = handle.canBeReplaced()

    override fun requiresCorrectToolForDrops(): Boolean = handle.requiresCorrectToolForDrops()

    override fun hasBlockEntity(): Boolean = handle.hasBlockEntity()

    override fun isCollisionShapeFullBlock(level: BlockGetter, pos: BlockPos): Boolean =
        handle.isCollisionShapeFullBlock(level.toMinecraft(), pos.toMinecraft())

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOptionalValue(property: Property<T>): T? {
        val game = property.toMinecraft() ?: return null
        val value = handle.getOptionalValue(game).orElse(null) ?: return null
        return fromGame(value) as T?
    }

    override fun <T : Any> setValue(property: Property<T>, value: T): BlockState {
        val game = property.toMinecraft() ?: return this
        if (!handle.hasProperty(game)) return this
        return setGame(game, value).toAgent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V : Comparable<V>> setGame(property: McProperty<V>, value: Any): McBlockState =
        handle.setValue(property, toGame(value) as V)

    private companion object {
        fun Property<*>.toMinecraft(): McProperty<*>? = when (this) {
            BlockStateProperties.OPEN -> McProperties.OPEN
            BlockStateProperties.DOUBLE_BLOCK_HALF -> McProperties.DOUBLE_BLOCK_HALF
            BlockStateProperties.HALF -> McProperties.HALF
            BlockStateProperties.HORIZONTAL_FACING -> McProperties.HORIZONTAL_FACING
            BlockStateProperties.SLAB_TYPE -> McProperties.SLAB_TYPE
            BlockStateProperties.BED_PART -> McProperties.BED_PART
            else -> null
        }

        fun fromGame(value: Any): Any = when (value) {
            is net.minecraft.core.Direction -> value.toAgent()
            is net.minecraft.world.level.block.state.properties.DoubleBlockHalf -> DoubleBlockHalf.valueOf(value.name)
            is net.minecraft.world.level.block.state.properties.Half -> Half.valueOf(value.name)
            is net.minecraft.world.level.block.state.properties.SlabType -> SlabType.valueOf(value.name)
            is net.minecraft.world.level.block.state.properties.BedPart -> BedPart.valueOf(value.name)
            else -> value
        }

        fun toGame(value: Any): Any = when (value) {
            is Direction -> value.toMinecraft()
            is DoubleBlockHalf -> net.minecraft.world.level.block.state.properties.DoubleBlockHalf.valueOf(value.name)
            is Half -> net.minecraft.world.level.block.state.properties.Half.valueOf(value.name)
            is SlabType -> net.minecraft.world.level.block.state.properties.SlabType.valueOf(value.name)
            is BedPart -> net.minecraft.world.level.block.state.properties.BedPart.valueOf(value.name)
            else -> value
        }
    }
}

private val states = AtomicReferenceArray<StateView?>(Block.BLOCK_STATE_REGISTRY.size())

/**
 * The library's state for the game's [this]; the same instance every time.
 *
 * @agent claude-opus-5-5
 */
public fun McBlockState.toAgent(): BlockState {
    val id = Block.getId(this)
    states.get(id)?.let { return it }
    val view = StateView(this)
    return if (states.compareAndSet(id, null, view)) view else states.get(id)!!
}

/**
 * @agent claude-opus-5-5
 */
public fun BlockState.toMinecraft(): McBlockState = (this as StateView).handle

/**
 * @agent claude-opus-5-5
 */
internal fun McFluidState.toAgent(): FluidState = when {
    isEmpty -> FluidState.EMPTY
    else -> FluidState(
        when {
            type.isSame(Fluids.WATER) -> Fluid.WATER
            type.isSame(Fluids.LAVA) -> Fluid.LAVA
            else -> Fluid.EMPTY
        },
        isSource,
        amount,
    )
}

private val shapes: MutableMap<McVoxelShape, VoxelShape> = Collections.synchronizedMap(WeakHashMap())

/**
 * The boxes of [this]; full and empty blocks become [Shapes.block] and [Shapes.empty].
 *
 * @agent claude-opus-5-5
 */
public fun McVoxelShape.toAgent(): VoxelShape = when {
    this === McShapes.block() -> Shapes.block()
    isEmpty -> Shapes.empty()
    else -> shapes.getOrPut(this) {
        val boxes = toAabbs()
        val array = DoubleArray(boxes.size * 6)
        boxes.forEachIndexed { i, box ->
            array[i * 6] = box.minX
            array[i * 6 + 1] = box.minY
            array[i * 6 + 2] = box.minZ
            array[i * 6 + 3] = box.maxX
            array[i * 6 + 4] = box.maxY
            array[i * 6 + 5] = box.maxZ
        }
        VoxelShape(array)
    }
}

/**
 * Blocks the library reads that the game also reads, such as a level or a snapshot of one.
 *
 * @agent claude-opus-5-5
 */
public interface GameBlocks {
    public val blocks: McBlockGetter
}

/**
 * The game's view of [this]: the game's own getter behind a [GameBlocks], otherwise one that asks [this].
 *
 * @agent claude-opus-5-5
 */
public fun BlockGetter.toMinecraft(): McBlockGetter = when (this) {
    is GameBlocks -> blocks
    net.ccbluex.agent.world.EmptyBlockGetter -> EmptyBlockGetter.INSTANCE
    else -> AgentBlocks(this)
}

/**
 * The game's getter over the library's [blocks], for shapes that depend on neighbours.
 *
 * @agent claude-opus-5-5
 */
private class AgentBlocks(private val blocks: BlockGetter) : McBlockGetter {
    override fun getBlockEntity(pos: net.minecraft.core.BlockPos) = null

    override fun getBlockState(pos: net.minecraft.core.BlockPos): McBlockState =
        blocks.getBlockState(pos.toAgent()).toMinecraft()

    override fun getFluidState(pos: net.minecraft.core.BlockPos): McFluidState = getBlockState(pos).fluidState

    override fun getHeight(): Int = blocks.height

    override fun getMinY(): Int = blocks.minY
}
