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

package net.ccbluex.agent.navigation

import net.minecraft.world.entity.player.Input
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The keys of a client body held at a ledge: safe walk stopped a sprinting player at the edge of a pit without
 * sneaking, and the server's anti-cheat set it back.
 *
 * @agent claude-opus-5-5
 */
internal class LedgeSneakTest {

    private val ledge = LedgeSneak()
    private val sprintForward = Input(true, false, false, false, false, false, true)

    @Test
    fun `at the ledge it sneaks and stops sprinting`() {
        val keys = ledge.keys(sprintForward, atLedge = true)
        assertTrue(keys.shift())
        assertFalse(keys.sprint())
        assertTrue(keys.forward())
    }

    @Test
    fun `away from a ledge the keys pass untouched`() {
        assertEquals(sprintForward, ledge.keys(sprintForward, atLedge = false))
    }

    @Test
    fun `the sneak is held a few ticks past the ledge, then let go`() {
        ledge.keys(sprintForward, atLedge = true)
        val held = (1..3).map { ledge.keys(sprintForward, atLedge = false).shift() }
        assertEquals(listOf(true, true, false), held)
    }
}
