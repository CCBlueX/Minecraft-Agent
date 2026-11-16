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

package net.ccbluex.agent.navigation.teleport

import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.navigation.TestBody
import net.ccbluex.agent.navigation.Teleporter
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.found
import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.teleport.Route
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @agent claude-sonnet-5-5
 */
class TeleportsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val START = feet(0, 1, 0)
        private val LIMITS = Limits(maxLength = 80.0)
    }

    private class Recorder : Teleporter {
        val sent = ArrayList<Pair<Vec3, Boolean>>()

        override fun send(position: Vec3, onGround: Boolean) {
            sent += position to onGround
        }

        override fun land(position: Vec3) = Unit
    }

    private class TeleportBody(body: TestBody, override val teleporter: Teleporter) : Body by body

    private val owner = Owner { true }

    private fun body(feet: Vec3, recorder: Recorder): Body {
        val player = TestLevel(TestWorld.flat()).player(feet)
        return TeleportBody(TestBody(player), recorder)
    }

    private fun route(to: Int, stepLength: Double = 5.0): Route {
        val terrain = TestWorld.flat().terrain()
        return Route.plan(terrain, listOf(START, feet(to, 1, 0)), stepLength)!!
    }

    /**
     * How many packets each tick sends until the route is sent.
     */
    private fun packetsPerTick(route: Route, options: Options): List<Int> {
        val recorder = Recorder()
        val teleports = Teleports(body(route.start, recorder))
        assertIs<Teleport>(teleports.dispatch(owner, route, Teleport.Finish.HOLD, options))
        val sizes = ArrayList<Int>()
        while (teleports.isMoving && sizes.size < 50) {
            val before = recorder.sent.size
            teleports.tick()
            sizes += recorder.sent.size - before
        }
        return sizes.filter { it > 0 }
    }

    @Test
    fun `batches send their packets per tick`() {
        assertEquals(listOf(1, 1, 1), packetsPerTick(route(15), Options(pacing = Pacing.Batches(1))))
        assertEquals(listOf(2, 1), packetsPerTick(route(15), Options(pacing = Pacing.Batches(2))))
    }

    @Test
    fun `burst sends every packet in one tick`() {
        assertEquals(listOf(3), packetsPerTick(route(15), Options(pacing = Pacing.Burst)))
    }

    @Test
    fun `instant sends one packet`() {
        val terrain = TestWorld.flat().terrain()
        val direct = found(Pacing.Instant.search(terrain, START, listOf(BlockPos(8, 1, 0)), LIMITS, 5.0).advanceToEnd())
        assertEquals(1, direct.steps.size)
        assertEquals(listOf(1), packetsPerTick(direct, Options(pacing = Pacing.Instant)))
    }

    @Test
    fun `burst plans only routes the server takes in a tick`() {
        val terrain = TestWorld.flat().terrain()
        val goal = listOf(BlockPos(15, 1, 0))
        val fits = found(Pacing.Burst.search(terrain, START, goal, LIMITS, 5.0).advanceToEnd())
        assertTrue(fits.fitsOneTick)

        val tooLong = listOf(BlockPos(40, 1, 0))
        val outcome = Pacing.Burst.search(terrain, START, tooLong, LIMITS, 5.0).advanceToEnd()
        assertEquals(Outcome.Failed(Failure.TOO_FAR), outcome)
        assertTrue(found(Pacing.Batches().search(terrain, START, tooLong, LIMITS, 5.0).advanceToEnd()).steps.size > 5)
    }

    @Test
    fun `instant fails when the move is blocked or too long`() {
        val clear = TestWorld.flat()
        val goal = listOf(BlockPos(8, 1, 0))
        assertIs<Outcome.Found<Route>>(Pacing.Instant.search(clear.terrain(), START, goal, LIMITS, 5.0).advanceToEnd())

        val walled = TestWorld.flat().fill(4, 1, -3, 4, 4, 3)
        assertEquals(
            Outcome.Failed(Failure.NO_PATH),
            Pacing.Instant.search(walled.terrain(), START, goal, LIMITS, 5.0).advanceToEnd(),
        )
        assertEquals(
            Outcome.Failed(Failure.TOO_FAR),
            Pacing.Instant.search(clear.terrain(), START, listOf(BlockPos(12, 1, 0)), LIMITS, 5.0).advanceToEnd(),
        )
    }

    @Test
    fun `ground is claimed only on descent`() {
        val top = Vec3(0.5, 10.0, 0.5)
        val steps = listOf(
            Route.Step(Vec3(3.5, 10.0, 0.5), false, 0),
            Route.Step(Vec3(6.5, 12.0, 0.5), false, 0),
            Route.Step(Vec3(8.5, 9.0, 0.5), false, 0),
        )
        val route = Route(listOf(top, Vec3(8.5, 9.0, 0.5)), steps)

        fun sentGround(claim: Boolean): List<Boolean> {
            val recorder = Recorder()
            val teleports = Teleports(body(top, recorder))
            val options = Options(pacing = Pacing.Burst, shouldClaimGroundOnDescent = claim)
            teleports.dispatch(owner, route, Teleport.Finish.HOLD, options)
            teleports.tick()
            return recorder.sent.map { it.second }
        }

        assertEquals(listOf(false, false, true), sentGround(claim = true))
        assertEquals(listOf(false, false, false), sentGround(claim = false))
    }

    @Test
    fun `syncPosition hands the server back to the client`() {
        val recorder = Recorder()
        val body = body(START, recorder)
        val teleports = Teleports(body)
        teleports.dispatch(owner, route(15), Teleport.Finish.HOLD, Options(pacing = Pacing.Burst))
        teleports.tick()
        assertEquals(feet(15, 1, 0), teleports.serverPosition)
        assertTrue(teleports.rewritesMovement)

        teleports.syncPosition()
        assertEquals(START to body.player.onGround(), recorder.sent.last())
        assertNull(teleports.serverPosition)
        assertFalse(teleports.rewritesMovement)
    }

}
