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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
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
        var continued = 0
        var stopped = false

        override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
            used = hit
            return true
        }

        override fun startDestroy(pos: BlockPos, face: Direction): Boolean {
            dug = pos
            return true
        }

        override fun continueDestroy(pos: BlockPos, face: Direction): Boolean {
            continued++
            return true
        }

        override fun stopDestroy() {
            stopped = true
        }
    }

    private fun host(world: TestWorld, at: Vec3 = point): Host {
        val player = TestLevel(world).player(TestWorld.feet(0, 1, 0))
        val rotation = ViewRotation.fromVector(at.subtract(player.eyePosition))
        player.yRot = rotation.yRot
        player.xRot = rotation.xRot
        return Host(TestBody(player))
    }

    private fun click(host: Host, options: Options) =
        TickBody(host) { options }.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(point, Direction.WEST, against, false))

    /**
     * Looking from the eyes at [at] on the west face of [against], with [between] in front of it: whether a place
     * against that face and a dig of it go out. Both or neither do.
     */
    private fun clicksPast(between: BlockState, at: Vec3): Boolean {
        val world = TestWorld.flat().set(3, 1, 0, TestWorld.STONE).set(2, 1, 0, between)
        val host = host(world, at)
        val body = TickBody(host) { Options() }
        val placed = body.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(at, Direction.WEST, against, false))
        val dug = body.startDestroy(against, Direction.WEST)
        assertEquals(placed, dug, "A place and a dig of the same face disagree past $between")
        return placed
    }

    @Test
    fun `a partial block hides the face where its outline is and shows it through its empty part`() {
        // Bottom slab: through its lower half, or over it through the empty upper half
        val slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
        assertFalse(clicksPast(slab, Vec3(3.0, 1.2, 0.5)))
        assertTrue(clicksPast(slab, Vec3(3.0, 1.7, 0.5)))
        // Stairs up to the north: through the step on the north half, over the low south half
        val stairs = Blocks.STONE_STAIRS.defaultBlockState()
        assertFalse(clicksPast(stairs, Vec3(3.0, 1.7, 0.2)))
        assertTrue(clicksPast(stairs, Vec3(3.0, 1.7, 0.8)))
        // A fence and a glass pane alone are a post in the middle: through it, or past it at the side
        for (post in listOf(Blocks.OAK_FENCE.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState())) {
            assertFalse(clicksPast(post, Vec3(3.0, 1.5, 0.5)))
            assertTrue(clicksPast(post, Vec3(3.0, 1.5, 0.95)))
        }
    }

    @Test
    fun `a dig stops once a block comes between`() {
        val world = TestWorld.flat().set(3, 1, 0, TestWorld.STONE)
        val host = host(world)
        val body = TickBody(host) { Options() }
        assertTrue(body.startDestroy(against, Direction.WEST))
        assertTrue(body.continueDestroy(against, Direction.WEST))
        world.set(2, 1, 0, TestWorld.STONE)
        assertFalse(body.continueDestroy(against, Direction.WEST))
        assertEquals(1, host.continued)
        assertTrue(host.stopped)
    }

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
