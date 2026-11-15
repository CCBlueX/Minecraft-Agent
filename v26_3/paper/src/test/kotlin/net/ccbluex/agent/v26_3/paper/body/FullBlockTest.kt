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

package net.ccbluex.agent.v26_3.paper.body

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
class FullBlockTest {

    @Test
    fun `a hit the shield took whole is fully blocked`() {
        assertTrue(isFullyBlocked(damage = 0f, blocking = -6.0))
    }

    @Test
    fun `a hit that got past the shield in part is not`() {
        assertFalse(isFullyBlocked(damage = 1.5f, blocking = -4.5))
    }

    @Test
    fun `a hit without a shield is not`() {
        assertFalse(isFullyBlocked(damage = 6f, blocking = 0.0))
    }

    @Test
    fun `a hit that did no damage without a shield is not`() {
        assertFalse(isFullyBlocked(damage = 0f, blocking = 0.0))
    }
}
