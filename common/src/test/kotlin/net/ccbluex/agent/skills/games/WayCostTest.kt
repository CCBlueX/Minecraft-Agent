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

package net.ccbluex.agent.skills.games

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The straight way between two islands: void columns, the longest void stretch, bridge blocks and the climb.
 *
 * @agent claude-opus-5-5
 */
class WayCostTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    /**
     * Two 5x5 islands at y = 0 with a 10 block gap between x = 3 and x = 12.
     */
    private fun islands(rightY: Int = 0) = TestWorld().fill(-2, 0, -2, 2, 0, 2).fill(13, rightY, -2, 17, rightY, 2)

    @Test
    fun plainGroundCostsOnlyItsLength() {
        val way = WayCost.of(TestWorld.flat().terrain(), TestWorld.feet(0, 1, 0), TestWorld.feet(20, 1, 0))
        assertEquals(0, way.voidColumns)
        assertEquals(0, way.bridgeBlocks)
        assertEquals(0.0, way.longestVoid)
        assertEquals(20 * WayCost.DISTANCE_COST, way.value, 1e-9)
    }

    @Test
    fun gapCountsAsVoidAndBridge() {
        val way = WayCost.of(islands().terrain(), TestWorld.feet(0, 1, 0), TestWorld.feet(15, 1, 0))
        assertEquals(10, way.bridgeBlocks)
        assertTrue(way.voidColumns in 9..11, "void columns ${way.voidColumns}")
        assertTrue(way.longestVoid in 9.0..11.0, "longest void ${way.longestVoid}")
        assertTrue(way.value > 15 * WayCost.DISTANCE_COST + 8 * WayCost.VOID_COST)
    }

    @Test
    fun climbCostsMoreThanTheSameWayDown() {
        val terrain = islands(rightY = 4).terrain()
        val up = WayCost.of(terrain, TestWorld.feet(0, 1, 0), TestWorld.feet(15, 5, 0))
        val down = WayCost.of(terrain, TestWorld.feet(15, 5, 0), TestWorld.feet(0, 1, 0))
        assertEquals(4.0, up.climb)
        assertEquals(0.0, down.climb)
        assertTrue(up.value > down.value)
    }

    @Test
    fun unloadedColumnsAreNoVoid() {
        val world = TestWorld { x, _ -> x < 5 }.fill(-2, 0, -2, 2, 0, 2)
        val way = WayCost.of(world.terrain(), TestWorld.feet(0, 1, 0), TestWorld.feet(15, 1, 0))
        assertTrue(way.voidColumns <= 3, "void columns ${way.voidColumns}")
    }

    @Test
    fun stepDownIsNoBridge() {
        val world = TestWorld().fill(-2, 1, -2, 2, 1, 2).fill(3, 0, -2, 8, 0, 2)
        val way = WayCost.of(world.terrain(), TestWorld.feet(0, 2, 0), TestWorld.feet(6, 1, 0))
        assertEquals(0, way.bridgeBlocks)
    }

    @Test
    fun knockedUpAboveGroundIsNoBridge() {
        val way = WayCost.of(TestWorld.flat().terrain(), Vec3(0.5, 2.2, 0.5), TestWorld.feet(4, 1, 0))
        assertEquals(0, way.bridgeBlocks)
    }
}
