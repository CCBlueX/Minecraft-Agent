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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @agent claude-sonnet-5-5
 */
class BodyDeathTest {

    @Test
    fun `a body that ran out of health stays dead without a respawn`() {
        assertTrue(bodyStaysDead(respawns = false, health = 0.0))
    }

    @Test
    fun `a body without a player is dead`() {
        assertTrue(bodyStaysDead(respawns = false, health = null))
    }

    @Test
    fun `a faked death that keeps the body does not mark it dead`() {
        assertFalse(bodyStaysDead(respawns = false, health = 20.0))
    }

    @Test
    fun `a body that respawns is never dead for good`() {
        assertFalse(bodyStaysDead(respawns = true, health = 0.0))
    }
}
