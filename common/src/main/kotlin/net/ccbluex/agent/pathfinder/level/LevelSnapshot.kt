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

package net.ccbluex.agent.pathfinder.level

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.material.FluidState

/**
 * Block states copied from a world, safe to read from any thread.
 *
 * From LiquidBounce: [WorldSnapshot.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/world/WorldSnapshot.kt#L32-L70)
 *
 * @agent claude-opus-5-5
 */
public class LevelSnapshot internal constructor(
    private val minY: Int,
    private val height: Int,
    private val sections: Long2ObjectOpenHashMap<PalettedContainer<BlockState>?>,
    private val borderMinX: Double,
    private val borderMaxX: Double,
    private val borderMinZ: Double,
    private val borderMaxZ: Double,
) : LevelBlocks {

    override fun isKnown(x: Int, y: Int, z: Int): Boolean =
        y >= minY && y < minY + height && sections.containsKey(sectionKey(x, y, z))

    override fun isInsideBorder(x: Double, z: Double): Boolean =
        x >= borderMinX && x < borderMaxX && z >= borderMinZ && z < borderMaxZ

    override fun getBlockState(pos: BlockPos): BlockState {
        if (pos.y < minY || pos.y >= minY + height) {
            return VOID_AIR
        }

        val key = sectionKey(pos.x, pos.y, pos.z)
        val states = sections.get(key) ?: return if (sections.containsKey(key)) AIR else VOID_AIR
        return states.get(pos.x and 15, pos.y and 15, pos.z and 15)
    }

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    override fun getHeight(): Int = height

    override fun getMinY(): Int = minY

    private companion object {
        val AIR: BlockState = Blocks.AIR.defaultBlockState()
        val VOID_AIR: BlockState = Blocks.VOID_AIR.defaultBlockState()

        fun sectionKey(x: Int, y: Int, z: Int) = SectionPos.asLong(x shr 4, y shr 4, z shr 4)
    }

}
