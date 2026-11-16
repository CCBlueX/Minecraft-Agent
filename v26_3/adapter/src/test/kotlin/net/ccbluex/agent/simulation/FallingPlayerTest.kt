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

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.Input
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * [FallingPlayer] over a stone floor at y 0, against LivingEntity.travel worked through by hand: each tick moves by
 * the motion, which then loses 0.08 to gravity and 2% to drag.
 *
 * @agent claude-opus-5-5
 */
class FallingPlayerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    /**
     * A player at rest in the air with its feet at [height] over the floor at (0, 0, 0).
     */
    private fun falling(height: Double): Player =
        TestLevel(TestWorld.flat()).player(Vec3(0.5, height, 0.5).toAgent()).apply { setOnGround(false) }

    private fun still(player: Player) = FallingPlayer(player, SimulatedInput(Input.EMPTY), continuesTick = false)

    @Test
    fun `a fall of nine blocks lands in the seventeenth tick on the block below`() {
        val landing = assertNotNull(still(falling(10.0)).findCollision(40))

        assertEquals(BlockPos(0, 0, 0), landing.pos)
        assertEquals(16, landing.tick)
        assertEquals(1.41564676387082, landing.positionBeforeMovement.y, 1.0E-6)
    }

    @Test
    fun `no landing within too few ticks`() {
        assertNull(still(falling(10.0)).findCollision(10))
    }

    @Test
    fun `the motion handed over replaces the player's`() {
        // A jump's 0.42 up first rises, so the same fall takes longer.
        val jumping = FallingPlayer(falling(10.0), SimulatedInput(Input.EMPTY), false, motion = Vec3(0.0, 0.42, 0.0))
        assertTrue(assertNotNull(jumping.findCollision(60)).tick > 16)
    }

    @Test
    fun `the blocks the feet pass through before landing`() {
        val fall = still(falling(10.0))

        assertTrue(fall.canReachBeforeLanding(BlockPos(0, 5, 0), 40))
        assertFalse(fall.canReachBeforeLanding(BlockPos(3, 5, 0), 40))
        assertFalse(fall.canReachBeforeLanding(BlockPos(0, 5, 0), 5), "not that far down yet")
    }

    @Test
    fun `the path of a fall deeper than allowed ends once past it`() {
        val path = assertNotNull(still(falling(10.0)).fallPath(3f, 40))

        assertTrue(path.last().y < 7.0, "fell past 3 blocks: ${path.last()}")
        assertTrue(path.dropLast(1).all { it.y >= 7.0 - 1.0E-9 }, "ends at the first tick past it: $path")
    }

    @Test
    fun `no path for a fall that lands within the allowed distance`() {
        assertNull(still(falling(3.0)).fallPath(3f, 40))
    }
}
