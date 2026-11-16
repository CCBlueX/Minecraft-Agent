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

package net.ccbluex.agent.bukkit

import org.bukkit.Location
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.ServiceLoader
import net.ccbluex.agent.world.entity.Entity as AgentEntity

/**
 * What differs between the server platforms bots run on: the body, the game's entities, keeping chunks loaded. Each
 * platform module names its implementation in `META-INF/services`, and the one on the classpath runs.
 *
 * @agent claude-opus-5-5
 */
internal interface Platform {

    /**
     * Defines the game types and registers the platform's listeners for [bots].
     */
    fun start(plugin: Plugin, bots: Bots)

    fun spawn(plugin: Plugin, location: Location, name: String, skin: BotSkin?): BotBody

    fun toAgent(entity: org.bukkit.entity.Entity): AgentEntity

    /**
     * Keeps the chunks around [location] loaded for [bot] until [release]; a platform without chunk tickets keeps
     * none.
     */
    fun hold(bot: Bot, location: Location)

    fun release(bot: Bot)

    /**
     * Puts [item] into [slot] of [entity]'s equipment, as far as the game version has the slot.
     */
    fun equip(entity: org.bukkit.entity.Player, slot: EquipmentSlot, item: ItemStack)

    companion object {
        val current: Platform by lazy {
            ServiceLoader.load(Platform::class.java, Platform::class.java.classLoader).firstOrNull()
                ?: error("No server platform module on the classpath")
        }
    }
}
