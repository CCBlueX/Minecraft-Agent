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

package net.ccbluex.agent.skills.eat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a heal may take the hand off a fight. A bot chose "Eat golden_apple" at 8.2 of 20 health with an enemy a
 * couple of blocks away, three times, each time holding its attacks, and died.
 *
 * @agent claude-opus-5-5
 */
class EatingTest {

    private val health = 8.24 / 20

    @Test
    fun `a target in reach with less health is fought, not eaten away from`() {
        assertFalse(healsInFight(health, targetHealth = 0.3, targetDistance = 2.5))
        assertFalse(healsInFight(health, targetHealth = health, targetDistance = 2.5))
    }

    @Test
    fun `a trade the agent loses is healed out of`() {
        assertTrue(healsInFight(health, targetHealth = 0.9, targetDistance = 2.5))
    }

    @Test
    fun `a target out of reach leaves time to eat`() {
        assertTrue(healsInFight(health, targetHealth = 0.3, targetDistance = 10.0))
    }
}
