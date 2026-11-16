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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @agent claude-opus-5-5
 */
internal class PendingTest {

    private fun tried(action: String, effect: String, follows: Boolean = false, lines: Int = 0) =
        Pending(action, KnownMap.Point(0.0, 0.0, 0.0), null, null, effect, 1.0, follows).also { it.lines = lines }

    @Test
    fun `a switch that only answers in chat is dropped, the button that turned on the teleporter is kept`() {
        val music = tried("use", "chat", lines = 1)
        val button = tried("interact", "entity appeared")
        val teleporter = tried("step into", "teleport", follows = true)
        assertEquals(listOf(button, teleporter), Pending.keep(listOf(music, button, teleporter)))
    }

    @Test
    fun `a briefing the way on waited for is kept, a short greeting and a lever are not`() {
        val greeting = tried("walk up to", "chat", lines = 12)
        val briefing = tried("interact", "chat", lines = 85)
        val lever = tried("use", "blocks set")
        val elevator = tried("interact", "effect")
        assertEquals(listOf(briefing, elevator), Pending.keep(listOf(greeting, briefing, lever, elevator)))
    }

    @Test
    fun `nothing tried keeps nothing`() {
        assertEquals(emptyList(), Pending.keep(emptyList()))
    }
}
