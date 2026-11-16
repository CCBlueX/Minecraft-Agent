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

package net.ccbluex.agent.v1_8_8.spigot.body

import org.bukkit.craftbukkit.v1_8_R3.CraftServer
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer
import org.bukkit.metadata.MetadataValue
import org.bukkit.plugin.Plugin

/**
 * CraftPlayer keys metadata by player name, which an online player of the bot's name would share; this keys it by
 * entity, as for any other entity.
 *
 * @agent claude-opus-5-5
 */
internal class BotCraftPlayer(server: CraftServer, player: BotPlayer) : CraftPlayer(server, player) {

    override fun setMetadata(key: String, value: MetadataValue) = server.entityMetadata.setMetadata(this, key, value)

    override fun getMetadata(key: String): List<MetadataValue> = server.entityMetadata.getMetadata(this, key)

    override fun hasMetadata(key: String): Boolean = server.entityMetadata.hasMetadata(this, key)

    override fun removeMetadata(key: String, plugin: Plugin) = server.entityMetadata.removeMetadata(this, key, plugin)
}
