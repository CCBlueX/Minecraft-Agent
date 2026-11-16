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

import net.ccbluex.agent.bukkit.Bot
import net.ccbluex.agent.bukkit.BotBody
import net.ccbluex.agent.bukkit.BotSkin
import net.ccbluex.agent.bukkit.Bots
import net.ccbluex.agent.bukkit.Platform
import net.ccbluex.agent.v1_8_8.spigot.body.BotEvents
import net.ccbluex.agent.v1_8_8.spigot.body.spawnBotPlayer
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * Spigot 1.8.8: bodies of [SpigotBody]. 1.8 has no chunk tickets, so minigame bots keep no chunks loaded.
 *
 * @agent claude-opus-5-5
 */
internal class SpigotPlatform : Platform {

    override fun start(plugin: Plugin, bots: Bots) {
        defineGameTypes()
        plugin.server.pluginManager.registerEvents(BotEvents(bots), plugin)
    }

    override fun spawn(plugin: Plugin, location: Location, name: String, skin: BotSkin?): BotBody =
        SpigotBody(spawnBotPlayer(plugin, location, name, skin))

    override fun toAgent(entity: Entity): net.ccbluex.agent.world.entity.Entity = (entity as CraftEntity).handle.toAgent()

    override fun hold(bot: Bot, location: Location) {}

    override fun release(bot: Bot) {}

    override fun equip(entity: Player, slot: EquipmentSlot, item: ItemStack) {
        val equipment = entity.equipment
        when (slot) {
            EquipmentSlot.HAND -> equipment.itemInHand = item
            EquipmentSlot.HEAD -> equipment.helmet = item
            EquipmentSlot.CHEST -> equipment.chestplate = item
            EquipmentSlot.LEGS -> equipment.leggings = item
            EquipmentSlot.FEET -> equipment.boots = item
        }
    }
}
