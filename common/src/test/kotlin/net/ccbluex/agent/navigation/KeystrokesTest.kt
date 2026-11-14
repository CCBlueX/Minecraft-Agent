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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
internal class KeystrokesTest {

    private val keystrokes = Keystrokes()

    private fun keys(
        forward: Boolean = false,
        backward: Boolean = false,
        left: Boolean = false,
        right: Boolean = false,
        jump: Boolean = false,
    ) = Input(forward, backward, left, right, jump, false, false)

    private fun shaped(input: Input, exact: Boolean = false) = keystrokes.press(input, exact, MIN_PRESS, MIN_RELEASE)

    @Test
    fun `without minimums the keys pass untouched`() {
        val inputs = listOf(keys(forward = true), keys(), keys(backward = true, jump = true), keys(forward = true))
        for (input in inputs) {
            assertSame(input, keystrokes.press(input, false, 0, 0))
        }
    }

    @Test
    fun `a one tick tap lasts the least press`() {
        val forward = listOf(keys(forward = true), keys(), keys(), keys(), keys()).map { shaped(it).forward() }
        assertEquals(listOf(true, true, true, false, false), forward)
    }

    @Test
    fun `a press right after a release waits for the least release`() {
        repeat(MIN_PRESS) { shaped(keys(left = true)) }
        val left = listOf(keys(), keys(left = true), keys(left = true), keys(left = true)).map { shaped(it).left() }
        assertEquals(listOf(false, false, true, true), left)
    }

    @Test
    fun `a jump is held but never delayed`() {
        shaped(keys(jump = true))
        repeat(MIN_PRESS - 1) { assertTrue(shaped(keys()).jump(), "The jump is held for its least press") }
        assertFalse(shaped(keys()).jump())
        assertTrue(shaped(keys(jump = true)).jump(), "A jump goes at once, even right after a release")
    }

    @Test
    fun `opposite keys are never down together`() {
        shaped(keys(forward = true))
        repeat(MIN_PRESS + MIN_RELEASE + 2) {
            val keys = shaped(keys(backward = true))
            assertFalse(keys.forward() && keys.backward(), "Forward and backward down together on tick $it")
        }
        assertTrue(shaped(keys(backward = true)).backward(), "Backward goes down in the end")
    }

    @Test
    fun `exact ticks pass and still count`() {
        val tap = keys(forward = true)
        assertSame(tap, shaped(tap, exact = true))
        assertFalse(shaped(keys(), exact = true).forward(), "An exact release goes at once")
        assertFalse(shaped(keys(forward = true)).forward(), "The exact release counts for the next press")
    }

    private companion object {
        const val MIN_PRESS = 3
        const val MIN_RELEASE = 2
    }
}
