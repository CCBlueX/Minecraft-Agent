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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.SectionPos
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.BlockState
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
         * The blocks in the loaded chunks within [CHUNKS] chunks of the center.
         */
        val IN_CHUNKS = LoadedBlocks { level, center, test ->
            val found = ArrayList<BlockPos>()
            val chunkX = SectionPos.blockToSectionCoord(center.x)
            val chunkZ = SectionPos.blockToSectionCoord(center.z)
            for (x in chunkX - CHUNKS..chunkX + CHUNKS) {
                for (z in chunkZ - CHUNKS..chunkZ + CHUNKS) {
                    found += level.findBlocks(x, z, level.minY, level.maxY, test::test)
                }
            }
            found
        }
    }
}
