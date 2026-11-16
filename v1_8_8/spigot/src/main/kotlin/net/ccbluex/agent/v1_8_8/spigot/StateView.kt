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

package net.ccbluex.agent.v1_8_8.spigot

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.block.FluidState
import net.ccbluex.agent.world.block.Property
import net.ccbluex.agent.world.block.Shapes
import net.ccbluex.agent.world.block.SlabType
import net.ccbluex.agent.world.block.VoxelShape
import net.minecraft.server.v1_8_R3.AxisAlignedBB
import net.minecraft.server.v1_8_R3.BlockFluids
import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.BlockStepAbstract
import net.minecraft.server.v1_8_R3.IBlockData
import net.minecraft.server.v1_8_R3.IBlockState
import net.minecraft.server.v1_8_R3.Material
import net.minecraft.server.v1_8_R3.World
import net.minecraft.server.v1_8_R3.Block as McBlock

/**
 * A 1.8 block state, one per combined id. Its collision shape comes from the server's own block code at the
 * position, on the server thread; shapes that depend only on the state are kept once seen.
 *
 * @agent claude-opus-5-5
 */
internal class StateView(block: Block, val data: IBlockData) : BlockState(block) {

    val stateId: Int = GameTypes.stateId(data)

    /**
     * Whether the shape depends on the position, so only the level at that position knows it.
     */
    val isPlaced: Boolean = GameTypes.isPlaced(stateId)

    @Volatile
    var shape: VoxelShape? = null
        private set

    override val isAir: Boolean = data.block.getMaterial() == Material.AIR

    override val fluidState: FluidState = fluid(data)

    override fun getCollisionShape(level: BlockGetter, pos: BlockPos): VoxelShape = when (level) {
        is LevelView -> shapeAt(level.handle, pos.x, pos.y, pos.z)
        is LevelSnapshot -> level.placedShape(pos, this) ?: knownShape()
        else -> knownShape()
    }

    // Without the world at hand: the shape seen last, or a guess from the material until then
    private fun knownShape(): VoxelShape =
        shape ?: if (data.block.getMaterial().isSolid) Shapes.block() else Shapes.empty()

    override fun getShape(level: BlockGetter, pos: BlockPos): VoxelShape = getCollisionShape(level, pos)

    override fun getDestroySpeed(level: BlockGetter, pos: BlockPos): Float = STRENGTH.getFloat(data.block)

    override fun canBeReplaced(): Boolean = data.block.getMaterial().isReplaceable

    override fun requiresCorrectToolForDrops(): Boolean = !data.block.getMaterial().isAlwaysDestroyable

    override fun hasBlockEntity(): Boolean = data.block.isTileEntity

    override fun isCollisionShapeFullBlock(level: BlockGetter, pos: BlockPos): Boolean =
        getCollisionShape(level, pos) === Shapes.block()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOptionalValue(property: Property<T>): T? {
        if (property === BlockStateProperties.SLAB_TYPE) {
            val game = data.block as? BlockStepAbstract ?: return null
            if (game.l()) return SlabType.DOUBLE as T
            return SlabType.valueOf(data.get(BlockStepAbstract.HALF).name) as T
        }
        val game = gameProperty(property) ?: return null
        val value = data.get(game as IBlockState<Comparable<Any>>)
        if (value is Boolean) return if (property.type == Boolean::class.javaObjectType) value as T else null
        val name = (value as? Enum<*>)?.name ?: return null
        return property.type.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> setValue(property: Property<T>, value: T): BlockState {
        val game = gameProperty(property) as? IBlockState<Comparable<Any>>
            ?: throw IllegalArgumentException("$block has no property ${property.name}")
        val wanted = if (value is Enum<*>) game.c().firstOrNull { (it as? Enum<*>)?.name == value.name } else value
        requireNotNull(wanted) { "$block has no ${property.name} $value" }
        return GameTypes.state(data.set(game, wanted as Comparable<Any>))
    }

    private fun gameProperty(property: Property<*>): IBlockState<*>? = data.a().firstOrNull { it.a() == property.name }

    /**
     * The collision boxes the server gives an entity at [x] [y] [z], relative to the block's corner. Call on the server
     * thread: 1.8 blocks keep their bounds in fields while they work them out.
     */
    fun shapeAt(world: World, x: Int, y: Int, z: Int): VoxelShape {
        if (!isPlaced) shape?.let { return it }
        val boxes = ArrayList<AxisAlignedBB>()
        val around = AxisAlignedBB(x - 1.0, y - 1.0, z - 1.0, x + 2.0, y + 2.0, z + 2.0)
        data.block.a(world, BlockPosition(x, y, z), data, around, boxes, null)
        val found = toShape(boxes, x, y, z)
        if (!isPlaced) shape = found
        return found
    }

    override fun toString(): String = "BlockState{${block.id} $data}"

    private companion object {
        val STRENGTH: java.lang.reflect.Field = McBlock::class.java.getDeclaredField("strength").apply { isAccessible = true }

        fun toShape(boxes: List<AxisAlignedBB>, x: Int, y: Int, z: Int): VoxelShape {
            if (boxes.isEmpty()) return Shapes.empty()
            if (boxes.size == 1) {
                val box = boxes[0]
                return Shapes.box(box.a - x, box.b - y, box.c - z, box.d - x, box.e - y, box.f - z)
            }
            val numbers = DoubleArray(boxes.size * 6)
            boxes.forEachIndexed { index, box ->
                val o = index * 6
                numbers[o] = box.a - x
                numbers[o + 1] = box.b - y
                numbers[o + 2] = box.c - z
                numbers[o + 3] = box.d - x
                numbers[o + 4] = box.e - y
                numbers[o + 5] = box.f - z
            }
            return VoxelShape(numbers)
        }

        fun fluid(data: IBlockData): FluidState {
            val block = data.block as? BlockFluids ?: return FluidState.EMPTY
            val type = if (block.getMaterial() == Material.LAVA) Fluid.LAVA else Fluid.WATER
            val level = data.get(BlockFluids.LEVEL)
            // Levels 8 and up are falling liquid, as full as a source
            return FluidState(type, level == 0, if (level >= FALLING) FULL else FULL - level)
        }

        const val FULL = 8
        const val FALLING = 8
    }
}
