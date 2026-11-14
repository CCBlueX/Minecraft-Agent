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
package net.ccbluex.agent.skills.games.skywars

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Backing off to heal holds against an enemy that keeps its distance: an agent decided to retreat at hp 9, then picked
 * an enemy 12 blocks away as a shot and walked back to it, dying 140 ticks later. Only an enemy that catches up is
 * fought, until it falls back out of melee.
 *
 * @agent claude-opus-5-5
 */
class RetreatTest {

    @Test
    fun fightsOnlyWhoCatchesUpWhileBackingOff() {
        var fighting = false
        val fought = listOf(12.0, 8.0, 4.0, 2.5, 4.5, 6.0, 4.0).map { distance ->
            Threats.corners(distance, fighting).also { fighting = it }
        }
        assertEquals(listOf(false, false, false, true, true, false, false), fought)
    }
}
