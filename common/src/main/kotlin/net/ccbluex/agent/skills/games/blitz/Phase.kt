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

package net.ccbluex.agent.skills.games.blitz

/**
 * Where a [Blitz] round is for the agent.
 *
 * @agent claude-opus-5-5
 */
internal enum class Phase(val label: String) {
    IDLE("Waiting"),
    START("Counting down"),
    CENTER("Looting the center"),

    /**
     * Standing so the gear and eat abilities sort the loot and feed it.
     */
    GEAR("Gearing up"),
    HUNT("Hunting"),

    /**
     * The deathmatch countdown after its teleport, while attacks do nothing.
     */
    PREPARE("Preparing for the deathmatch"),
    DEATHMATCH("Deathmatch"),
    END("Round over"),
    ;

    val isPlaying: Boolean
        get() = this in CENTER..DEATHMATCH
}
