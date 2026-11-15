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
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.found
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.ElytraMotion
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Glides through caves in a closed loop with the elytra simulation: routes searched with [AirwaySearch], flown with
 * [Passage], every tick checked for a block touched.
 *
 * @agent claude-opus-5-5
 */
class PassageTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val CLEARANCE = 1.0
        private const val RADIUS = 2.0

        /**
         * Blocks from the goal within which a landing comes closer to the floor than the clearance.
         */
        private const val LANDING_ROOM = 8.0
    }

    /**
     * The inside of a cave at one z: from [left] to [right] and [floor] to [ceiling], every block included.
     */
    private class Section(val left: Int, val right: Int, val floor: Int, val ceiling: Int)

    /**
     * Stone three blocks thick around the sections from [z1] to [z2], closed at both ends and where a section is
     * smaller than the one next to it.
     */
    private fun cave(z1: Int, z2: Int, section: (Int) -> Section): TestWorld {
        val world = TestWorld()
        for (z in z1 - 3..z2 + 3) {
            val inside = section(z.coerceIn(z1, z2))
            val isEnd = z < z1 || z > z2
            for (outer in listOf(section((z - 1).coerceIn(z1, z2)), inside, section((z + 1).coerceIn(z1, z2)))) {
                for (x in outer.left - 3..outer.right + 3) {
                    for (y in outer.floor - 3..outer.ceiling + 3) {
                        val isInside = x in inside.left..inside.right && y in inside.floor..inside.ceiling
                        if (isEnd || !isInside) {
                            world.set(x, y, z, TestWorld.STONE)
                        }
                    }
                }
            }
        }
        return world
    }

    /**
     * The result of a flight: how it ended, its ticks, the length flown and the least room kept to any block.
     */
    private class Flight(val end: Vec3, val ticks: Int, val length: Double, val leastRoom: Double, val touched: Vec3?)

    /**
     * Flies from [start] at [motion] to the goal [goals] gives at every tick until within [RADIUS] of the last one, or
     * touching down; searches the route again every 40 ticks, on drifting off it and for a new goal.
     */
    private fun fly(
        world: TestWorld,
        start: Vec3,
        motion: Vec3,
        maxTicks: Int,
        landsAtTarget: Boolean,
        goals: (tick: Int) -> Vec3,
    ): Flight {
        val terrain = world.terrain()
        var position = start
        var deltaMovement = motion
        var goal = goals(0)
        var passage = Passage(goal, Rockets(0, 0), CLEARANCE, landsAtTarget) { terrain }
        var searchedAt = Int.MIN_VALUE / 2
        var length = 0.0
        var leastRoom = Double.MAX_VALUE
        var steerNanos = 0L

        for (tick in 0 until maxTicks) {
            val next = goals(tick)
            if (next != goal) {
                goal = next
                passage = Passage(goal, Rockets(0, 0), CLEARANCE, landsAtTarget) { terrain }
                searchedAt = Int.MIN_VALUE / 2
            }
            if (tick - searchedAt >= 40 || passage.drift > 4.0 || passage.remaining < 32.0 && tick - searchedAt >= 10) {
                passage.follow(search(terrain, position, goal))
                searchedAt = tick
            }

            val began = System.nanoTime()
            val controls = passage.steer(position, deltaMovement)
            steerNanos += System.nanoTime() - began
            deltaMovement = ElytraMotion.updateFallFlyingMovement(deltaMovement, controls.yRot, controls.xRot, false)
            val to = position.add(deltaMovement)

            for (step in 1..4) {
                val point = position.add(deltaMovement.scale(step / 4.0))
                if (!terrain.isClear(Passage.box(point, 0.0))) {
                    val landed = landsAtTarget && point.horizontalDistanceTo(goal) <= 4.0 &&
                        point.y - goal.y in -1.0..1.5 && deltaMovement.y >= Landing.SAFE_SINK &&
                        terrain.isClear(Passage.box(point.add(0.0, 0.5, 0.0), 0.0))
                    if (landed) {
                        return report(Flight(point, tick, length, leastRoom, null), steerNanos)
                    }
                    println("touched at $point moving $deltaMovement after $tick ticks")
                    return Flight(point, tick, length, leastRoom, point)
                }
            }
            if (to.distanceTo(goal) > LANDING_ROOM) {
                leastRoom = min(leastRoom, room(terrain, to))
            }
            length += deltaMovement.length()
            position = to
            if (!landsAtTarget && position.distanceTo(goal) <= RADIUS && goals(tick + 1) == goal) {
                return report(Flight(position, tick, length, leastRoom, null), steerNanos)
            }
        }
        return report(Flight(position, maxTicks, length, leastRoom, null), steerNanos)
    }

    private fun report(flight: Flight, steerNanos: Long): Flight {
        println(
            "flew %.0f blocks in %d ticks, least room %.2f, %d µs a tick".format(
                flight.length, flight.ticks, flight.leastRoom, steerNanos / (flight.ticks + 1) / 1000,
            ),
        )
        return flight
    }

    private fun search(terrain: Terrain, start: Vec3, goal: Vec3): Route {
        val result = AirwaySearch(
            terrain, start, NearDestination(goal, RADIUS + CLEARANCE + 1.0), CLEARANCE,
            Limits(512.0, maxVisitedNodes = 60_000, visitedNodesPerSlice = 60_000, maxComputeNanos = Long.MAX_VALUE),
        ).advanceToEnd()
        if (result == Outcome.AlreadyThere) {
            return Route(start, listOf(start, goal), canReach = true)
        }
        return found(result)
    }

    /**
     * The most room up to 3 blocks the body at [feet] has on every side, in tenths.
     */
    private fun room(terrain: Terrain, feet: Vec3): Double {
        var margin = 0.0
        while (margin < 3.0 && terrain.isClear(Passage.box(feet, margin + 0.1))) {
            margin += 0.1
        }
        return margin
    }

    /**
     * No block touched, and where there is room, [CLEARANCE] kept but for [slack].
     */
    private fun assertClear(flight: Flight, slack: Double = 0.05) {
        assertEquals(null, flight.touched, "touched a block")
        assertTrue(flight.leastRoom >= CLEARANCE - slack, "came within ${flight.leastRoom} of a block")
    }

    @Test
    fun `bending caves with pillars are glided through without touching anything and landed in at the end`() {
        // Floor from y 100 down to 60 over 320 blocks, bending 20 blocks to either side
        val world = cave(0, 320) { z ->
            val center = (20 * sin(z * 2 * PI / 320)).toInt()
            val floor = 100 - z / 8
            Section(center - 9, center + 9, floor, floor + 19)
        }
        world.fill(12, 80, 120, 15, 110, 123)
        world.fill(-20, 60, 220, -17, 100, 223)
        val start = Vec3(0.5, 115.0, 3.5)
        val goal = Vec3(0.5, 61.0, 316.5)

        val flight = fly(world, start, Vec3(0.0, -0.3, 0.3), 1200, landsAtTarget = true) { goal }
        assertClear(flight)
        assertTrue(flight.end.horizontalDistanceTo(goal) <= 4.0, "came down at ${flight.end}, away from $goal")
    }

    @Test
    fun `narrowings with room for little more than the clearance are glided through`() {
        val world = cave(0, 200) { z ->
            val floor = 100 - z / 8
            if (z in 90..110) Section(-2, 3, floor + 6, floor + 11) else Section(-14, 15, floor, floor + 24)
        }
        val goal = Vec3(0.5, 82.0, 190.5)

        val flight = fly(world, Vec3(0.5, 115.0, 3.5), Vec3(0.0, -0.2, 0.8), 600, landsAtTarget = false) { goal }
        assertClear(flight)
        assertTrue(flight.end.distanceTo(goal) <= RADIUS, "stopped at ${flight.end}, away from $goal")
    }

    @Test
    fun `low ceilings are passed under`() {
        val world = cave(0, 240) { z ->
            val floor = 100 - z / 8
            Section(-12, 12, floor, floor + if (z in 100..140) 7 else 30)
        }
        val goal = Vec3(0.5, 75.0, 230.5)

        val flight = fly(world, Vec3(0.5, 125.0, 3.5), Vec3(0.0, 0.0, 1.2), 600, landsAtTarget = false) { goal }
        assertClear(flight)
        assertTrue(flight.end.distanceTo(goal) <= RADIUS, "stopped at ${flight.end}, away from $goal")
    }

    @Test
    fun `a fall in a shaft is taken over and turned into the tunnel`() {
        // A shaft 20 blocks wide and 60 deep opening into a tunnel along +z at its bottom
        val world = cave(0, 200) { z ->
            if (z < 20) Section(-10, 10, 40, 100) else Section(-10, 10, 40 - (z - 20) / 8, 60 - (z - 20) / 8)
        }
        val goal = Vec3(0.5, 30.0, 190.5)

        val flight = fly(world, Vec3(0.5, 95.0, 10.5), Vec3(0.0, -0.8, 0.0), 600, landsAtTarget = false) { goal }
        // Pulling out of the fall at the bottom of the shaft takes some of the clearance
        assertClear(flight, slack = 0.9)
        assertTrue(flight.end.distanceTo(goal) <= RADIUS, "stopped at ${flight.end}, away from $goal")
    }

    @Test
    fun `goals switched during a glide are flown on to`() {
        val world = cave(0, 260) { z -> Section(-40, 40, 20, 120) }
        val first = Vec3(-20.5, 90.0, 200.5)
        val second = Vec3(25.5, 70.0, 220.5)

        val flight = fly(world, Vec3(0.5, 110.0, 3.5), Vec3(0.0, 0.0, 1.0), 600, landsAtTarget = false) { tick ->
            if (tick < 60) first else second
        }
        assertClear(flight)
        assertTrue(flight.end.distanceTo(second) <= RADIUS, "stopped at ${flight.end}, away from $second")
    }

}
