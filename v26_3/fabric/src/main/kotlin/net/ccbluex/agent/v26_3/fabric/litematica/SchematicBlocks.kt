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

package net.ccbluex.agent.v26_3.fabric.litematica

import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.block.BlockState
import net.minecraft.core.BlockPos as McBlockPos

/**
 * The non-air blocks in the boxes of every enabled placement, as Litematica's schematic world holds them placed,
 * rotated and mirrored. In a file of its own, so its class and Litematica's load only once it is called.
 *
 * @agent claude-opus-5-5
 */
internal fun schematicBlocks(): Map<BlockPos, BlockState> {
    val level = SchematicWorldHandler.getSchematicWorld() ?: return emptyMap()
    val blocks = HashMap<BlockPos, BlockState>()
    for (placement in DataManager.getSchematicPlacementManager().allSchematicsPlacements) {
        if (!placement.isEnabled) continue
        for (box in placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values) {
            for (pos in McBlockPos.betweenClosed(box.pos1 ?: continue, box.pos2 ?: continue)) {
                val state = level.getBlockState(pos)
                if (!state.isAir) blocks[pos.immutable().toAgent()] = state.toAgent()
            }
        }
    }
    return blocks
}
