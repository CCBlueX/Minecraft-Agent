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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.world.Input
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A drop the walk planned onto a one-block ledge over the void: the keys still sprinted on past the ledge, so the void
 * rescue took the drop for a fall into the void, let go of the keys and the path mid-air, and the bot slid off the
 * ledge it landed on.
 *
 * @agent claude-opus-5-5
 */
class EdgeHoldTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val Y_ROT = 90.6f
    }

    /**
     * The floor the bot ran along at y 66, the ledge at (15, 65) it dropped onto and the far side, all over the void.
     */
    private val terrain = TestWorld()
        .fill(16, 66, -13, 20, 66, -9)
        .fill(15, 65, -13, 15, 65, -11)
        .fill(10, 66, -13, 13, 66, -9)
        .terrain()

    private val sprintForward = Input(true, false, false, false, false, false, true)

    private fun dropsIntoVoid(position: Vec3, motion: Vec3): Boolean {
        val state = PlayerMotion(terrain)
        state.reset(position, motion, onGround = false, isSprinting = true)
        return EdgeHold.simulateOnLand(state, Y_ROT, sprintForward, safeWalk = false, untilLanding = true) { feet ->
            terrain.isClear(terrain.box(feet).setMinY(feet.y - VoidSafety.VOID_DEPTH))
        }
    }

    @Test
    fun aDropOntoALedgeIsNoFallIntoTheVoid() {
        assertFalse(dropsIntoVoid(Vec3(15.393, 66.922, -10.491), Vec3(-0.153, -0.155, -0.002)))
    }

    @Test
    fun aDropThatMissesTheLedgeIs() {
        assertTrue(dropsIntoVoid(Vec3(14.5, 66.922, -10.5), Vec3(-0.153, -0.155, 0.0)))
    }
}
