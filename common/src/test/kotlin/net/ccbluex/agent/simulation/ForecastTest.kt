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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.math.DIRECTION_FORWARDS
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [Forecast] and [Forecasts] on a [TestLevel], against vanilla movement worked through by hand.
 *
 * @agent claude-opus-5-5
 */
class ForecastTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    // A standing player keeps one tick of gravity, so its first move already lands on the floor.
    private fun standing(level: TestLevel = TestLevel(TestWorld.flat()), at: Vec3 = TestWorld.feet(0, 1, 0)) =
        level.player(at).apply { deltaMovement = Vec3(0.0, -0.0784000015258789, 0.0) }

    @Test
    fun `tick 0 is the player handed over and later ticks are kept`() {
        val forecast = Forecast(SimulatedPlayer.fromPlayer(standing(), SimulatedInput(DIRECTION_FORWARDS), false))

        assertEquals(TestWorld.feet(0, 1, 0), forecast.getSnapshotAt(0).pos)
        val tenth = forecast.getSnapshotAt(10)
        assertSame(tenth, forecast.getSnapshotAt(10), "a tick is simulated once")
        assertEquals(forecast.getSnapshotsBetween(0..10), forecast.simulate().take(11).toList())
        assertEquals(forecast.getSnapshotsBetween(3..5), forecast.simulateBetween(3..5).toList())
        assertTrue(forecast.getSnapshotsBetween(0 until 0).isEmpty())
    }

    @Test
    fun `walking forwards moves along the view and stays on the ground`() {
        val forecast = Forecast(SimulatedPlayer.fromPlayer(standing(), SimulatedInput(DIRECTION_FORWARDS), false))

        val ticks = forecast.getSnapshotsBetween(1..20)
        assertTrue(ticks.all { it.onGround && it.pos.y == 1.0 }, "walks on the floor: $ticks")
        // Yaw 0 faces south (+z). Walking settles at 0.098 a tick of push and 0.546 ground drag: 0.2159 a tick.
        val last = ticks.last()
        assertEquals(0.5, last.pos.x, 1.0E-9)
        assertEquals(0.2159, last.pos.z - ticks[ticks.size - 2].pos.z, 1.0E-3)
        assertEquals(0.2159 * 0.546, last.deltaMovement.z, 1.0E-3)
        assertTrue(last.pos.z > 3.5, "walked off the start: ${last.pos}")
    }

    @Test
    fun `a forecast replays as a simulation`() {
        val forecast = Forecast(SimulatedPlayer.fromPlayer(standing(), SimulatedInput(DIRECTION_FORWARDS), false))
        val simulation = forecast.asSimulation()

        repeat(5) { simulation.tick() }
        assertEquals(forecast.getSnapshotAt(5).pos, simulation.pos)
    }

    @Test
    fun `ticks past a minute are refused`() {
        val forecast = Forecast(SimulatedPlayer.fromPlayer(standing(), SimulatedInput(Input.EMPTY), false))

        assertThrows<IllegalArgumentException> { forecast.getSnapshotsBetween(0..1200 + 1) }
        assertThrows<IllegalArgumentException> { forecast.getSnapshotAt(-1) }
    }

    @Test
    fun `forecasts of other players are made once until cleared`() {
        val level = TestLevel(TestWorld.flat())
        val other = standing(level)
        val forecasts = Forecasts()

        val first = forecasts.of(other)
        assertSame(first, forecasts.of(other))
        forecasts.clear()
        assertFalse(first === forecasts.of(other))
    }
}
