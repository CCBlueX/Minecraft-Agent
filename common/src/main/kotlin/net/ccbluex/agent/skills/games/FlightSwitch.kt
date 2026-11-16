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

package net.ccbluex.agent.skills.games

/**
 * The host's flight, such as a fly module, turned on and off by a game mode; while on, `Body.isFlying` must be true.
 *
 * @agent claude-opus-5-5
 */
public interface FlightSwitch {

    /**
     * Whether the flight runs right now; void rescue leaves a flying player alone.
     */
    public val isFlying: Boolean

    public fun fly(on: Boolean)
}
