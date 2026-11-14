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

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Where a flight sets down: over ground, never at a goal out over the void.
 *
 * @agent claude-opus-5-5
 */
class CrossingTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    /**
     * A 5x5 island at y = 0, its edge at x = 2.
     */
    private val island = TestWorld().fill(-2, 0, -2, 2, 0, 2).terrain()

    @Test
    fun goalOverTheVoidLandsAtTheNearestEdge() {
        assertEquals(feet(2, 1, 0), landingSpot(island, feet(4, 1, 0)))
    }

    @Test
    fun goalOnGroundLandsThere() {
        assertEquals(feet(1, 1, 1), landingSpot(island, feet(1, 1, 1)))
    }

    @Test
    fun goalAboveTheIslandLandsOnIt() {
        assertEquals(feet(0, 1, 0), landingSpot(island, feet(0, 3, 0)))
    }

    @Test
    fun goalFarFromGroundHasNoLandingSpot() {
        assertNull(landingSpot(island, feet(12, 1, 0)))
    }
}
