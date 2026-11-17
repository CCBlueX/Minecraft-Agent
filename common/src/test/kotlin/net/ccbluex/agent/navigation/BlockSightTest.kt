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
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.Entity
import net.minecraft.world.level.block.Blocks as McBlocks
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which points of a block a player can look at: never one behind another block, as the game's pick hits the nearer
 * block first; a block only partly hidden is aimed at where it shows.
 *
 * @agent claude-opus-5-5
 */
class BlockSightTest {

    init {
        MinecraftBootstrap.ensureInitialized()
    }

    private val eye = Vec3(0.5, 2.62, 0.5)
    private val target = BlockPos(3, 1, 0)
    private val ahead = ViewRotation(-90f, 0f)
    private val collision: Entity? = null

    @Test
    fun `a block behind a wall has no point in sight`() {
        val world = TestWorld.flat().set(target.x, target.y, target.z, TestWorld.STONE).fill(2, 1, -3, 2, 5, 3)
        assertNull(world.seenPoint(eye, target, 4.5, null, ahead, collision, 0f))
    }

    @Test
    fun `a block behind a low wall is aimed at over it, never through it`() {
        val world = TestWorld.flat().set(target.x, target.y, target.z, TestWorld.STONE).fill(2, 1, -3, 2, 1, 3)
        val point = assertNotNull(world.seenPoint(eye, target, 4.5, null, ahead, collision, 0f))
        assertTrue(world.isSeen(eye, point, target, null, collision))
        assertTrue(world.isSeen(eye, point, target, Direction.UP, collision))
        // The face towards the player lies behind the wall
        assertNull(world.seenPoint(eye, target, 4.5, Direction.WEST, ahead, collision, 0f))
    }

    @Test
    fun `a block in the open is seen, out of reach it is not`() {
        val world = TestWorld.flat().set(target.x, target.y, target.z, TestWorld.STONE)
        assertNotNull(world.seenPoint(eye, target, 4.5, Direction.WEST, ahead, collision, 0f))
        assertNull(world.seenPoint(eye, target, 2.0, null, ahead, collision, 0f))
    }

    @Test
    fun `from a pond, a block between farmland is aimed at where looks a turn step off still meet it`() {
        // Farmland lies lower than grass, so the grass block shows a sliver of its sides past its top
        val world = TestWorld()
            .fill(-2, 0, -2, 2, 0, 2, McBlocks.GRASS_BLOCK.defaultBlockState())
            .set(0, -1, 0, TestWorld.STONE)
            .set(0, 0, 0, McBlocks.WATER.defaultBlockState())
        for ((x, z) in listOf(0 to -1, 1 to 0, -1 to 0, 0 to 1, 1 to -1, -1 to -1)) {
            world.set(x, 0, z, McBlocks.FARMLAND.defaultBlockState())
        }
        val grass = BlockPos(1, 0, 1)
        val inPond = Vec3(0.5156, 1.62, 0.3807)
        val step = 0.15f
        val point = assertNotNull(world.seenPoint(inPond, grass, 4.2, Direction.UP, ViewRotation(-205.65f, 26.9f), collision, step))
        val look = ViewRotation.fromVector(point.subtract(inPond))
        val length = inPond.distanceTo(point) + 1.0
        for ((yRot, xRot) in listOf(step / 2 to 0f, -step / 2 to 0f, 0f to step / 2, 0f to -step / 2)) {
            val off = inPond.add(ViewRotation(look.yRot + yRot, look.xRot + xRot).viewVector.scale(length))
            assertTrue(world.isSeen(inPond, off, grass, Direction.UP, collision), "A look $yRot/$xRot off misses the top")
        }
    }
}
