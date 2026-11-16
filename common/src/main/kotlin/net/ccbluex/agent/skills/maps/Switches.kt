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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.SectionPos
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.BlockKind

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
         * Reads the loaded chunks.
         */
        val CHUNKS = Switches { level, center, action ->
            for (cx in SectionPos.blockToSectionCoord(center.x - RANGE)..SectionPos.blockToSectionCoord(center.x + RANGE)) {
                for (cz in SectionPos.blockToSectionCoord(center.z - RANGE)..SectionPos.blockToSectionCoord(center.z + RANGE)) {
                    val found = level.findBlocks(cx, cz, center.y - RANGE, center.y + RANGE) {
                        it.`is`(BlockKind.BUTTON) || it.`is`(BlockKind.LEVER) || it.`is`(BlockKind.PRESSURE_PLATE)
                    }
                    for (pos in found) {
                        if (pos.distSqr(center) <= RANGE * RANGE) action(pos, level.getBlockState(pos).block)
                    }
                }
            }
        }
    }
}
