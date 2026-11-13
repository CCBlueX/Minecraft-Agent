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

package net.ccbluex.agent.pathfinder.glide

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.TestWorld.Companion.AIR
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.found
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.simulation.ElytraMotion
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * From LiquidBounce: [GlideRoutingTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/GlideRoutingTest.kt#L39-L127)
 *
 * @agent claude-opus-5-5
 */
class GlideRoutingTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val MAX_SLICES = 1000
        private val GLIDER = GlideAbilities(rockets = 0, rocketTicks = 0, glideTicks = Int.MAX_VALUE)
        private val ROCKETEER = GlideAbilities(rockets = 8, rocketTicks = 20, glideTicks = Int.MAX_VALUE)
    }

    /**
     * Ground at y = 0 from x -10 to 100, so feet stand at y = 1, and a stone tower on x -1..1, z -1..1 with its top
     * at [towerTop].
     */
    private fun towerWorld(towerTop: Int) = TestWorld()
        .fill(-10, 0, -40, 100, 0, 40)
        .fill(-1, 1, -1, 1, towerTop, 1)

    private fun glide(world: TestWorld, start: Vec3, goal: Vec3, abilities: GlideAbilities) =
        RouteSearch(world.terrain(), start, goal, 1.0, abilities).advanceToEnd(MAX_SLICES)

    @Test
    fun `goals close below a high take-off are glided to by circling down`() {
        val goal = feet(40, 1, 0)
        val path = found(glide(towerWorld(58), feet(0, 59, 0), goal, GLIDER))
        assertTrue(path.landing.distanceTo(goal) <= 3.0, "the glide lands at ${path.landing}, away from $goal")
    }

    @Test
    fun `ridges under the direct glide line are crossed high and circled down after`() {
        val world = towerWorld(40).fill(30, 1, -40, 36, 30, 40)
        val goal = feet(70, 1, 0)

        val path = found(glide(world, feet(0, 41, 0), goal, GLIDER))
        assertEquals(1.0, path.landing.y, 1.0E-6, "the glide lands at ${path.landing}")
        assertTrue(path.landing.distanceTo(goal) <= 3.0, "the glide lands at ${path.landing}, away from $goal")
    }

    @Test
    fun `slow touch-downs brushing a step at the goal still land`() {
        val world = towerWorld(40).fill(-10, 1, -40, 100, 1, 40).set(70, 1, 0, AIR)
        val goal = feet(70, 1, 0)

        val path = found(glide(world, feet(0, 41, 0), goal, GLIDER))
        val landing = path.landing
        assertTrue(
            landing.horizontalDistanceTo(goal) <= 3.0 && landing.y - goal.y <= 1.0,
            "the glide lands at $landing, away from $goal",
        )
    }

    @Test
    fun `walls in the way stop the glide`() {
        val world = towerWorld(40).fill(30, 1, -40, 30, 45, 40)

        assertIs<Outcome.Failed>(glide(world, feet(0, 41, 0), feet(70, 1, 0), GLIDER))
    }

    @Test
    fun `goals above are glided to only with rockets`() {
        val world = towerWorld(10).fill(56, 1, -8, 72, 19, 8)
        val start = feet(0, 11, 0)
        val goal = feet(64, 20, 0)

        assertIs<Outcome.Failed>(glide(world, start, goal, GLIDER))

        val path = found(glide(world, start, goal, ROCKETEER))
        assertTrue(path.rockets > 0, "the glide needs no rocket")
        assertTrue(path.landing.distanceTo(goal) <= 3.0, "the glide lands at ${path.landing}, away from $goal")
    }

    @Test
    fun `far too high above the goal it sinks without firing rockets`() {
        val goal = Vec3(0.5, 90.0, 0.5)
        val control = Landing(goal, Rockets(left = 8, burnTicks = 20))
        var position = Vec3(20.5, 250.0, 0.5)
        var deltaMovement = Vec3(-1.5, 0.0, 0.0)

        repeat(200) {
            val input = control.steer(position, deltaMovement)
            assertFalse(input.shouldFireRocket, "fired a rocket at $position, ${position.y - goal.y} above the goal")
            deltaMovement = ElytraMotion.updateFallFlyingMovement(deltaMovement, input.yRot, input.xRot, input.isBoosted)
            position = position.add(deltaMovement)
        }
        assertEquals(8, control.rockets.left)
        assertTrue(position.y < 250.0 - 40.0, "only sank to ${position.y}")
    }

}
