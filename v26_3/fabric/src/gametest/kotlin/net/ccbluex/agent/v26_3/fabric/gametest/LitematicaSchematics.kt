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

package net.ccbluex.agent.v26_3.fabric.gametest

import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.schematic.LitematicaSchematic
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.selection.AreaSelection
import fi.dy.masa.litematica.selection.Box
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

// In a file of its own, so Litematica's classes load only in runs that have it

/**
 * Makes a schematic of [blocks] in memory and places it where they are, enabled, as a player loads and places one.
 *
 * @agent claude-opus-5-5
 */
internal fun placeSchematic(blocks: Map<BlockPos, BlockState>) {
    val min = blocks.keys.reduce(BlockPos::min)
    val max = blocks.keys.reduce(BlockPos::max)
    val area = AreaSelection()
    area.addSubRegionBox(Box(BlockPos.ZERO, max.subtract(min), "main"), false)
    area.explicitOrigin = BlockPos.ZERO
    val schematic = LitematicaSchematic.createEmptySchematic(area, "Minecraft Agent")
    val container = checkNotNull(schematic.getSubRegionContainer("main")) { "The empty schematic has no region" }
    for ((pos, state) in blocks) {
        val at = pos.subtract(min)
        container.set(at.x, at.y, at.z, state)
    }
    DataManager.getSchematicPlacementManager()
        .addSchematicPlacement(SchematicPlacement.createFor(schematic, min, "pyramid", true, true), false)
}

/**
 * Removes every placement again.
 *
 * @agent claude-opus-5-5
 */
internal fun removeSchematics() {
    val manager = DataManager.getSchematicPlacementManager()
    manager.allSchematicsPlacements.toList().forEach { manager.removeSchematicPlacement(it, false) }
}
