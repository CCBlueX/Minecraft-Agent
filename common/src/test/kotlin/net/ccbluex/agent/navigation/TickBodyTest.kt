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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clicks go out only on the face the rotation the server has hits first: a face behind a wall is refused, unless the
 * options allow clicking through blocks; an entity behind a wall is not hit.
 *
 * @agent claude-opus-5-5
 */
class TickBodyTest {

    init {
        MinecraftBootstrap.ensureInitialized()
    }

    private val against = BlockPos(3, 1, 0)
    private val point = Vec3(3.0, 1.5, 0.5)

    private class Host(body: Body) : Body by body {
        var used: BlockHitResult? = null
        var dug: BlockPos? = null

        override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
            used = hit
            return true
        }

        override fun startDestroy(pos: BlockPos, face: Direction): Boolean {
            dug = pos
            return true
        }
    }

    private fun host(world: TestWorld): Host {
        val player = TestLevel(world).player(TestWorld.feet(0, 1, 0))
        val rotation = ViewRotation.fromVector(point.subtract(player.eyePosition))
        player.yRot = rotation.yRot
        player.xRot = rotation.xRot
        return Host(TestBody(player))
    }

    private fun click(host: Host, options: Options) =
        TickBody(host) { options }.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(point, Direction.WEST, against, false))

    @Test
    fun `a face in sight is clicked where the look hits it`() {
        val host = host(TestWorld.flat().set(3, 1, 0, TestWorld.STONE))
        assertTrue(click(host, Options()))
        val used = host.used
        assertEquals(against, used?.blockPos)
        assertEquals(Direction.WEST, used?.direction)
    }

    @Test
    fun `a face behind a wall is refused unless clicks through blocks are allowed`() {
        val world = TestWorld.flat().set(3, 1, 0, TestWorld.STONE).fill(2, 1, -3, 2, 5, 3)
        val host = host(world)
        assertFalse(click(host, Options()))
        assertNull(host.used)
        assertFalse(TickBody(host) { Options() }.startDestroy(against, Direction.WEST))
        assertNull(host.dug)

        assertTrue(click(host, Options(canClickThroughBlocks = true)))
        assertEquals(against, host.used?.blockPos)
    }

    @Test
    fun `a face the look does not hit is refused`() {
        val host = host(TestWorld.flat().set(3, 1, 0, TestWorld.STONE))
        val top = BlockHitResult(point, Direction.UP, against, false)
        assertFalse(TickBody(host) { Options() }.useItemOn(InteractionHand.MAIN_HAND, top))
    }

    @Test
    fun `an entity behind a wall is not seen`() {
        val level = TestLevel(TestWorld.flat().fill(2, 1, -3, 2, 5, 3))
        val player = level.player(TestWorld.feet(0, 1, 0), -90f)
        val other = level.player(TestWorld.feet(3, 1, 0))
        assertFalse(TestBody(player).sees(other))

        val open = TestLevel(TestWorld.flat())
        assertTrue(TestBody(open.player(TestWorld.feet(0, 1, 0), -90f)).sees(open.player(TestWorld.feet(3, 1, 0))))
    }
}
