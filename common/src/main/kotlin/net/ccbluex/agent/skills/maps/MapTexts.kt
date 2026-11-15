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

package net.ccbluex.agent.skills.maps

/**
 * What the screen showed in the world a map is played in, kept by the host: the last title, subtitle and action bar
 * text still on screen, and the chat lines the server sent in this world. Without colour codes.
 *
 * @agent claude-sonnet-5-5
 */
public interface MapTexts {

    public val shownTitle: String?

    public val shownSubtitle: String?

    public val shownActionbar: String?

    /**
     * The server's chat lines of this world, oldest first.
     */
    public fun chat(): List<String>
}
