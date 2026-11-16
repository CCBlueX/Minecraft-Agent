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

import org.bukkit.plugin.Plugin

/**
 * The way in for other plugins: they list the MinecraftAgent plugin under `depend` in their plugin.yml, or bundle
 * the library and [start] it from their own plugin. The same on every server platform.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public object MinecraftAgent {

    @Volatile
    internal var bots: Bots? = null

    /**
     * The bots of this server, while Minecraft Agent runs.
     */
    @JvmStatic
    public fun bots(): Bots = checkNotNull(bots) { "Minecraft Agent is not started" }

    /**
     * Starts the bots on behalf of [plugin], which owns their tasks and listeners. A plugin bundling the library
     * calls it in `onEnable` and [stop] in `onDisable`.
     */
    @JvmStatic
    public fun start(plugin: Plugin) {
        check(bots == null) { "Minecraft Agent is already started" }
        val bots = Bots(plugin, Platform.current)
        bots.start()
        this.bots = bots
    }

    /**
     * Stops what [start] started and takes every bot away.
     */
    @JvmStatic
    public fun stop() {
        bots?.stop()
        bots = null
    }
}
