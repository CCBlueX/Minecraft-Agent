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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who an under-equipped agent keeps away from. One evaded two players six times at the center chests, and each time
 * one of them paused by a chest it went back to loot next to them; it retreated at 10.5 health only late and died.
 *
 * @agent claude-opus-5-5
 */
class EngagementTest {

    @Test
    fun `a player pausing by a chest four blocks away is still kept away from`() {
        assertTrue(isThreat(distance = 4.5, approach = 0.0, evading = true))
        assertTrue(isThreat(distance = 7.5, approach = -0.1, evading = true))
    }

    @Test
    fun `keeping away ends once the player is well out of reach`() {
        assertFalse(isThreat(distance = 9.0, approach = 0.2, evading = true))
    }

    @Test
    fun `before keeping away only a player coming closer or standing close counts`() {
        assertFalse(isThreat(distance = 4.5, approach = 0.0, evading = false))
        assertTrue(isThreat(distance = 4.5, approach = 0.1, evading = false))
        assertTrue(isThreat(distance = 2.5, approach = 0.0, evading = false))
    }
}
