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

package net.ccbluex.agent.pathfinder.flight

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.TestWorld.Companion.AIR
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.found
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * From LiquidBounce: [FlightRoutingTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/FlightRoutingTest.kt#L38-L96)
 *
 * @agent claude-opus-5-5
 */
class FlightRoutingTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val LIMITS = Limits(maxLength = 64.0, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        private const val MAX_SLICES = 1000

        private val CREATIVE = FlyAbilities(Acceleration.MOMENTUM, 1.089, 0.375, isFlying = false)
    }

    private fun fly(world: TestWorld, start: Vec3, goal: Vec3): Route {
        val search = RouteSearch(world.terrain(), start, NearDestination(goal, 1.0), CREATIVE, LIMITS)
        val path = found(search.advanceToEnd(MAX_SLICES))
        assertTrue(path.canReach, "the flight should reach the goal")
        assertTrue(path.end.distanceTo(goal) <= 1.0, "the flight ends at ${path.end}, away from $goal")

        val terrain = world.terrain()
        for ((from, to) in path.points.zipWithNext()) {
            assertTrue(isClearLine(terrain, from, to), "the line from $from to $to is blocked")
        }
        return path
    }

    private fun Route.length() = points.zipWithNext().sumOf { (from, to) -> from.distanceTo(to) }

    @Test
    fun `canyons are flown straight across`() {
        val world = TestWorld.flat().fill(5, 0, -40, 14, 0, 40, AIR)
        val start = feet(0, 1, 0)
        val goal = feet(20, 1, 0)

        val path = fly(world, start, goal)
        assertEquals(2, path.points.size, "a clear line needs no corner: ${path.points}")
    }

    @Test
    fun `tall walls are flown over along few straight lines`() {
        val world = TestWorld.flat().fill(10, 1, -64, 10, 12, 64)
        val start = feet(0, 1, 0)
        val goal = feet(20, 1, 0)

        val path = fly(world, start, goal)
        assertTrue(path.points.any { it.y >= 13.0 }, "the route stays below the top of the wall: ${path.points}")
        assertTrue(path.points.size <= 6, "the route turns too often: ${path.points}")
        // Up the wall, over it and down again, with little more than the detour the wall forces.
        assertTrue(path.length() <= 1.25 * (2.0 * 12.0 + 20.0), "the route is ${path.length()} blocks long")
    }

    @Test
    fun `hazards are flown over`() {
        val world = TestWorld.flat().fill(5, 1, -40, 15, 1, 40, Blocks.FIRE.defaultBlockState())

        // Every line of the route is checked to keep out of the fire.
        val path = fly(world, feet(0, 1, 0), feet(20, 1, 0))
        assertTrue(path.points.size > 2, "the straight line through the fire is taken")
    }

}
