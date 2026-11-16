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

package net.ccbluex.agent.pathfinder

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.TestWorld.Companion.AIR
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.StageDestination
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Goals beyond the path range or in unknown blocks are walked to in stages.
 *
 * From LiquidBounce: [WalkStageTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/WalkStageTest.kt#L42-L126)
 */
class WalkStageTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val RANGE = 32.0
        private const val MAX_SLICES = 1000
        private val LIMITS = Limits(maxLength = RANGE, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        private val START = feet(0, 1, 0)

        /**
         * A stone floor at y = 0 from x -10 to 200 and z -40 to 40.
         */
        private fun field(known: (x: Int, z: Int) -> Boolean = { _, _ -> true }) =
            TestWorld(known).fill(-10, 0, -40, 200, 0, 40)
    }

    private val abilities = walkAbilities()

    private fun stage(world: TestWorld, goal: BlockPos): Outcome<Route> {
        val terrain = world.terrain()
        val stageGoal = StageDestination(NearDestination(Vec3.atBottomCenterOf(goal), 1.0), START, RANGE, terrain)
        return RouteSearch(terrain, START, stageGoal, abilities, LIMITS).advanceToEnd(MAX_SLICES)
    }

    private fun stageEnd(world: TestWorld, goal: BlockPos): BlockPos =
        found(stage(world, goal)).steps.last().to

    private fun horizontalDistance(pos: BlockPos, to: Vec3) = Vec3.atCenterOf(pos).horizontalDistanceTo(to)

    @Test
    fun `goals beyond the range or not known are walked in stages`() {
        val world = field { x, _ -> x < 100 }

        assertFalse(StageDestination.isNeeded(world, START, feet(20, 1, 0), RANGE))
        assertTrue(StageDestination.isNeeded(world, START, feet(40, 1, 0), RANGE))
        assertTrue(StageDestination.isNeeded(field { x, _ -> x < 10 }, START, feet(20, 1, 0), RANGE))
    }

    @Test
    fun `a far goal gets a stage at the range in its direction`() {
        val end = stageEnd(field(), BlockPos(150, 1, 0))

        assertTrue(horizontalDistance(end, START) >= RANGE)
        assertTrue(horizontalDistance(end, START) < RANGE + 2.0)
        assertTrue(abs(end.z) <= 1, "stage at $end is off the way to the goal")
    }

    @Test
    fun `a stage lies past a wall on the way`() {
        val world = field().fill(10, 1, -30, 10, 3, 10)
        val end = stageEnd(world, BlockPos(150, 1, 0))

        assertTrue(end.x > 10, "stage at $end is still before the wall")
        assertTrue(horizontalDistance(end, START) >= RANGE)
    }

    @Test
    fun `a stage ends where the known blocks end`() {
        val end = stageEnd(field { x, _ -> x < 20 }, BlockPos(25, 1, 0))

        assertEquals(19, end.x)
        assertTrue(abs(end.z) <= 1, "stage at $end is off the way to the goal")
    }

    @Test
    fun `no stage is found when closed in`() {
        val world = field()
            .fill(-3, 1, -3, 1, 3, 3)
            .fill(-2, 1, -2, 0, 3, 2, AIR)

        assertIs<Outcome.Failed>(stage(world, BlockPos(150, 1, 0)))
    }

    @Test
    fun `stages never lead away from the goal`() {
        // A closed tube from x -3 to 60: its far end is past the range but farther from the goal than the start.
        val world = field()
            .fill(-3, 1, -2, 60, 3, 2)
            .fill(-2, 1, -1, 59, 3, 1, AIR)

        val path = found(stage(world, BlockPos(-150, 1, 0)))
        assertFalse(path.canReach)
        assertTrue(path.steps.last().to.x < 0, "walked away from the goal to ${path.steps.last().to}")
    }

}
