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
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [PositionExtrapolation] and the keys [SimulatedInput.guess] reads from a player's last move.
 *
 * @agent claude-opus-5-5
 */
class PositionExtrapolationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val feet = TestWorld.feet(0, 1, 0)

    @Test
    fun `a constant position never moves`() {
        assertEquals(feet, PositionExtrapolation.constant(feet).getPositionInTicks(12.5))
    }

    @Test
    fun `an entity keeps the move of its last tick`() {
        val level = TestLevel(TestWorld.flat())
        val stand = ArmorStand(level, feet.x, feet.y, feet.z)
        stand.snapTo(feet)
        stand.setPos(feet.add(0.2, 0.0, -0.1))

        val later = PositionExtrapolation.of(stand, Forecasts()).getPositionInTicks(2.5)
        assertEquals(0.5 + 0.2 * 3.5, later.x, 1.0E-9)
        assertEquals(0.5 - 0.1 * 3.5, later.z, 1.0E-9)
    }

    @Test
    fun `a forecast is read at the nearest tick up to tick 30`() {
        val player = TestLevel(TestWorld.flat()).player(feet)
        val forecast = Forecast(SimulatedPlayer.fromPlayer(player, SimulatedInput(DIRECTION_FORWARDS), false))
        val extrapolation = PositionExtrapolation.of(forecast)

        assertEquals(forecast.getSnapshotAt(2).pos, extrapolation.getPositionInTicks(2.4))
        assertEquals(forecast.getSnapshotAt(0).pos, extrapolation.getPositionInTicks(-3.0))
        assertEquals(forecast.getSnapshotAt(30).pos, extrapolation.getPositionInTicks(45.0))
    }

    @Test
    fun `a player is extrapolated by its forecast`() {
        val player = TestLevel(TestWorld.flat()).player(feet)
        player.setPos(feet.add(0.0, 0.0, 0.2))
        val forecasts = Forecasts()

        val extrapolation = PositionExtrapolation.of(player, forecasts)
        assertEquals(forecasts.of(player).getSnapshotAt(5).pos, extrapolation.getPositionInTicks(5.0))
    }

    @Test
    fun `the keys of a player are guessed from its last move`() {
        val player = TestLevel(TestWorld.flat()).player(feet)
        assertEquals(Input.EMPTY, SimulatedInput.guess(player).keys, "stands still")

        // Yaw 0 faces south (+z): a sprint's 0.28 a tick that way is forwards.
        player.setPos(feet.add(0.0, 0.0, 0.28))
        val sprinting = SimulatedInput.guess(player).keys
        assertTrue(sprinting.forward && sprinting.sprint)
        assertFalse(sprinting.backward || sprinting.left || sprinting.right || sprinting.jump)

        player.setOnGround(false)
        player.setPos(feet.add(0.0, 0.0, -0.1))
        val walkingBack = SimulatedInput.guess(player).keys
        assertTrue(walkingBack.backward && walkingBack.jump)
        assertFalse(walkingBack.sprint)
    }
}
