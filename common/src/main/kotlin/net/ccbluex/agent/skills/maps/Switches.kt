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

package net.ccbluex.agent.skills.maps

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.LeverBlock
import kotlin.math.abs

/**
 * Where the buttons, levers and pressure plates around a place are.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal fun interface Switches {

    /**
     * Calls [action] for every button, lever and pressure plate within [RANGE] blocks of [center].
     */
    fun forEach(level: Level, center: BlockPos, action: (BlockPos, Block) -> Unit)

    companion object {

        const val RANGE = 48

        /**
         * Reads the loaded chunks, and of these only the sections whose palette holds one.
         */
        val CHUNKS = Switches { level, center, action ->
            fun isSwitch(block: Block) = block is ButtonBlock || block is LeverBlock || block is BasePressurePlateBlock
            for (cx in SectionPos.blockToSectionCoord(center.x - RANGE)..SectionPos.blockToSectionCoord(center.x + RANGE)) {
                for (cz in SectionPos.blockToSectionCoord(center.z - RANGE)..SectionPos.blockToSectionCoord(center.z + RANGE)) {
                    val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
                    for ((index, section) in chunk.sections.withIndex()) {
                        val sectionY = chunk.getSectionYFromSectionIndex(index)
                        if (abs(SectionPos.sectionToBlockCoord(sectionY) + 8 - center.y) > RANGE + 8 || !section.maybeHas { isSwitch(it.block) }) {
                            continue
                        }
                        for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                            val block = section.getBlockState(x, y, z).block
                            if (!isSwitch(block)) continue
                            val pos = BlockPos(SectionPos.sectionToBlockCoord(cx) + x, SectionPos.sectionToBlockCoord(sectionY) + y, SectionPos.sectionToBlockCoord(cz) + z)
                            if (pos.distSqr(center) <= RANGE * RANGE) action(pos, block)
                        }
                    }
                }
            }
        }
    }
}
