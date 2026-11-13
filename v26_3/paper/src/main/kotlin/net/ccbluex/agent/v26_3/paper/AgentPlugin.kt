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

import org.bukkit.plugin.java.JavaPlugin

/**
 * The MinecraftAgent plugin: [Bots] for other plugins and the `/agent` command.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class AgentPlugin : JavaPlugin() {

    override fun onEnable() {
        val bots = Bots(this)
        bots.start()
        MinecraftAgent.bots = bots
        val command = AgentCommand(bots, Skins(this))
        getCommand("agent")?.let {
            it.setExecutor(command)
            it.tabCompleter = command
        }
    }

    override fun onDisable() {
        MinecraftAgent.bots?.stop()
        MinecraftAgent.bots = null
    }
}
