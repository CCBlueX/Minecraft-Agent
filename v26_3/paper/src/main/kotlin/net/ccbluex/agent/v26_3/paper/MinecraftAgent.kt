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

/**
 * The way in for other plugins, which list MinecraftAgent under `depend` in their plugin.yml.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public object MinecraftAgent {

    @Volatile
    internal var bots: Bots? = null

    /**
     * The bots of this server, while the MinecraftAgent plugin is enabled.
     */
    @JvmStatic
    public fun bots(): Bots = checkNotNull(bots) { "The MinecraftAgent plugin is not enabled" }
}
