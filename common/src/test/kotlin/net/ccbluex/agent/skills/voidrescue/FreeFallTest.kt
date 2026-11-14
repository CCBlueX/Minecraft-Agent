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

package net.ccbluex.agent.skills.voidrescue

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Free fall against a fly module holding the player: gravity speeds a fall up every tick, a flight never does.
 *
 * @agent claude-opus-5-5
 */
class FreeFallTest {

    /**
     * Feeds [ticks] heights and tells whether each one counted as a free fall.
     */
    private fun heights(start: Double, ticks: Int, next: (tick: Int, y: Double) -> Double): List<Boolean> {
        val fall = FreeFall()
        var y = start
        return (0 until ticks).map { tick ->
            fall.tick(y)
            val falling = fall.isFalling
            y = next(tick, y)
            falling
        }
    }

    @Test
    fun vanillaFallCountsWithinFourTicks() {
        var speed = 0.0
        val falling = heights(71.0, 10) { _, y ->
            speed = (speed - GRAVITY) * DRAG
            y + speed
        }
        assertFalse(falling[0])
        assertTrue(falling[4], "falling $falling")
        assertTrue(falling.drop(4).all { it }, "falling $falling")
    }

    @Test
    fun hoveringWithTheVanillaFlyBumpIsNoFall() {
        // LiquidBounce's vanilla fly drops 0.04 every 40 ticks against the vanilla fly check
        val falling = heights(71.0, 200) { tick, y -> if (tick % 40 == 0) y - 0.04 else y }
        assertTrue(falling.none { it })
    }

    @Test
    fun sinkingAtTheFlySpeedIsNoFall() {
        val falling = heights(71.0, 40) { _, y -> y - 0.44 }
        assertTrue(falling.none { it })
    }

    @Test
    fun risingIsNoFall() {
        val falling = heights(71.0, 40) { _, y -> y + 0.44 }
        assertTrue(falling.none { it })
    }

    @Test
    fun aFallThatStopsCountsNoLonger() {
        var speed = 0.0
        val falling = heights(71.0, 12) { tick, y ->
            speed = if (tick < 6) (speed - GRAVITY) * DRAG else 0.0
            y + speed
        }
        assertTrue(falling[6])
        assertFalse(falling.last())
    }

    private companion object {
        const val GRAVITY = 0.08
        const val DRAG = 0.98
    }
}
