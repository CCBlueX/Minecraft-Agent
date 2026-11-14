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
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * [SimulatedArrow] shot east at a stone wall at x 10, against AbstractArrow worked through by hand: each tick moves by
 * the velocity, which then keeps 99% and loses 0.05 to gravity.
 *
 * @agent claude-opus-5-5
 */
class SimulatedArrowTest {

    /**
     * A floor at y 0 and a wall at x 10 up to y 5.
     */
    private fun walled() = TestWorld.flat().fill(10, 1, -5, 10, 5, 5)

    private val start = Vec3(0.5, 2.5, 0.5)
    private val east = Vec3(3.0, 0.0, 0.0)

    private fun flyUntilHit(arrow: SimulatedArrow): HitResult {
        repeat(MAX_TICKS) {
            arrow.tick()?.let { return it }
        }
        error("no hit after $MAX_TICKS ticks")
    }

    @Test
    fun `a tick moves by the deltaMovement, then drag and gravity`() {
        val arrow = SimulatedArrow(walled(), null, start, east)

        assertNull(arrow.tick())
        assertEquals(Vec3(3.5, 2.5, 0.5), arrow.pos)
        assertEquals(2.97, arrow.deltaMovement.x, 1.0E-12)
        assertEquals(-0.05, arrow.deltaMovement.y, 1.0E-9)
    }

    @Test
    fun `the arrow sticks in the wall`() {
        val arrow = SimulatedArrow(walled(), null, start, east)

        val hit = assertIs<BlockHitResult>(flyUntilHit(arrow))
        assertEquals(10.0, hit.location.x, 1.0E-9)
        assertTrue(arrow.isInGround)
        assertEquals(hit.location, arrow.pos)
        assertNull(arrow.tick(), "stays where it hit")
        assertEquals(hit.location, arrow.pos)
    }

    @Test
    fun `a player in the way is hit before the wall`() {
        val level = TestLevel(walled())
        val target = level.player(TestWorld.feet(6, 1, 0))

        val hit = assertIs<EntityHitResult>(flyUntilHit(SimulatedArrow(level, start, east)))
        assertSame(target, hit.entity)
        assertEquals(target.boundingBox.minX, hit.location.x, 1.0E-9)
    }

    @Test
    fun `without entity hits it flies through the player`() {
        val level = TestLevel(walled())
        level.player(TestWorld.feet(6, 1, 0))

        assertIs<BlockHitResult>(flyUntilHit(SimulatedArrow(level, start, east, collideEntities = false)))
    }

    @Test
    fun `a player behind the wall is not hit`() {
        val level = TestLevel(walled())
        level.player(TestWorld.feet(11, 1, 0))

        assertIs<BlockHitResult>(flyUntilHit(SimulatedArrow(level, start, east)))
    }

    private companion object {
        const val MAX_TICKS = 20

        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
