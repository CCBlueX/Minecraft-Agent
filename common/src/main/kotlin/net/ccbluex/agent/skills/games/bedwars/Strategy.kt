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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.ExperimentalAgentApi

/**
 * How the agent leans in Bed Wars on top of its personality.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public enum class Strategy {

    /**
     * Whatever its traits and the round call for.
     */
    ADAPTIVE,

    /**
     * Goes for beds early and often: as aggressive and careless as a bot gets.
     */
    RUSH,

    /**
     * Stays home: covers and defends the bed, gathers and buys, never leaves for beds, players or ores.
     */
    HOLD_BASE,
}
