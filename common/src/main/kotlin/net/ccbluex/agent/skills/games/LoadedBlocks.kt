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

package net.ccbluex.agent.skills.games

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Predicate

/**
 * How the level senses look for blocks. Tests swap the walk over loaded chunks for one over a [Level] without chunks.
 *
 * @agent claude-sonnet-5-5
 */
internal fun interface LoadedBlocks {

    /**
     * The positions of the blocks accepted by [test] near [center].
     */
    fun find(level: Level, center: BlockPos, test: Predicate<BlockState>): List<BlockPos>

    companion object {
        private const val CHUNKS = 8

        /**
         * The blocks in the loaded chunks within [CHUNKS] chunks of the center; whole sections are skipped by their
         * palette before any block is read.
         */
        val IN_CHUNKS = LoadedBlocks { level, center, test ->
            val found = ArrayList<BlockPos>()
            val chunkX = SectionPos.blockToSectionCoord(center.x)
            val chunkZ = SectionPos.blockToSectionCoord(center.z)
            for (x in chunkX - CHUNKS..chunkX + CHUNKS) {
                for (z in chunkZ - CHUNKS..chunkZ + CHUNKS) {
                    level.chunkSource.getChunkNow(x, z)?.findBlocks(test) { pos, _ -> found += pos.immutable() }
                }
            }
            found
        }
    }
}
