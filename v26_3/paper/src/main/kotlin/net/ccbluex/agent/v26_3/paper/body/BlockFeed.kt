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

package net.ccbluex.agent.v26_3.paper.body

import net.ccbluex.agent.v26_3.paper.Bots
import net.ccbluex.agent.pathfinder.level.SectionCache
import net.minecraft.core.BlockPos
import org.bukkit.block.Block
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * Tells the route searches about block changes, as a client learns of them from the server's block updates: the
 * section copies they search drop the changed sections, and the navigators of bots nearby search again.
 *
 * @agent claude-opus-5-5
 */
internal class BlockFeed(private val bots: Bots) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event is BlockMultiPlaceEvent) {
            event.replacedBlockStates.forEach { changed(it.block) }
        } else {
            changed(event.block)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFade(event: BlockFadeEvent) = changed(event.block)

    // Also spreading blocks and blocks an entity forms, such as frosted ice
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onForm(event: BlockFormEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGrow(event: BlockGrowEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFlow(event: BlockFromToEvent) = changed(event.toBlock)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDecay(event: LeavesDecayEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityChange(event: EntityChangeBlockEvent) = changed(event.block)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        changed(event.block)
        event.blockList().forEach(::changed)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().forEach(::changed)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        changed(event.block.getRelative(event.direction))
        for (block in event.blocks) {
            changed(block)
            changed(block.getRelative(event.direction))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        changed(event.block.getRelative(event.direction.oppositeFace))
        for (block in event.blocks) {
            changed(block)
            changed(block.getRelative(event.direction))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onStructureGrow(event: StructureGrowEvent) {
        event.blocks.forEach { changed(it.block) }
    }

    // Loads and unloads change what a search may copy; the copies of an unloaded world must go
    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        SectionCache.columnChanged((event.world as CraftWorld).handle, event.chunk.x, event.chunk.z)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        SectionCache.columnChanged((event.world as CraftWorld).handle, event.chunk.x, event.chunk.z)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldUnload(event: WorldUnloadEvent) {
        SectionCache.forget((event.world as CraftWorld).handle)
    }

    private fun changed(block: Block) {
        val pos = BlockPos(block.x, block.y, block.z)
        SectionCache.blockChanged((block.world as CraftWorld).handle, pos)
        bots.blockChanged(block.world, pos)
    }
}
