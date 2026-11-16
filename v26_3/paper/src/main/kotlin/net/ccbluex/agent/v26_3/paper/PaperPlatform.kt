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

package net.ccbluex.agent.v26_3.paper

import net.ccbluex.agent.bukkit.Bot
import net.ccbluex.agent.bukkit.BotBody
import net.ccbluex.agent.bukkit.BotSkin
import net.ccbluex.agent.bukkit.Bots
import net.ccbluex.agent.bukkit.Platform
import net.ccbluex.agent.v26_3.paper.body.BlockFeed
import net.ccbluex.agent.v26_3.paper.body.BotEvents
import net.ccbluex.agent.v26_3.paper.body.spawnBotPlayer
import net.ccbluex.agent.v26_3.defineGameTypes
import net.ccbluex.agent.v26_3.toAgent
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * Paper 26.3: bodies of [PaperBody], chunk tickets for minigame bots.
 *
 * @agent claude-opus-5-5
 */
internal class PaperPlatform : Platform {

    private var chunkHolds: ChunkHolds? = null

    override fun start(plugin: Plugin, bots: Bots) {
        defineGameTypes()
        chunkHolds = ChunkHolds(plugin)
        val pluginManager = plugin.server.pluginManager
        pluginManager.registerEvents(BotEvents(bots), plugin)
        pluginManager.registerEvents(BlockFeed(bots), plugin)
    }

    override fun spawn(plugin: Plugin, location: Location, name: String, skin: BotSkin?): BotBody =
        PaperBody(spawnBotPlayer(plugin, location, name, skin))

    override fun toAgent(entity: Entity): net.ccbluex.agent.world.entity.Entity = (entity as CraftEntity).handle.toAgent()

    override fun hold(bot: Bot, location: Location) {
        chunkHolds?.hold(bot, location.world, location.blockX shr CHUNK_SHIFT, location.blockZ shr CHUNK_SHIFT)
    }

    override fun release(bot: Bot) {
        chunkHolds?.release(bot)
    }

    override fun equip(entity: Player, slot: EquipmentSlot, item: ItemStack) {
        entity.equipment.setItem(slot, item)
    }

    private companion object {
        const val CHUNK_SHIFT = 4
    }
}
