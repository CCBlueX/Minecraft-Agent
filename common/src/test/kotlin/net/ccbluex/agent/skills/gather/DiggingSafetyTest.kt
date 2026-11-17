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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.night.canDigIn
import net.ccbluex.agent.world.block.Blocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.world.level.block.Blocks as McBlocks

/**
 * Where digging is safe, finding blocks a player could see, and where a shelter can be dug: the checks that keep a
 * survival player out of lava, water and caves.
 *
 * @agent claude-opus-5-5
 */
class DiggingSafetyTest {

    init {
        MinecraftBootstrap.ensureInitialized()
    }

    /**
     * Solid stone from y = -10 to 0, feet stand at y = 1.
     */
    private fun ground() = TestWorld().fill(-20, -10, -20, 20, 0, 20)

    @Test
    fun `a staircase step into plain stone is safe`() {
        val world = ground()
        assertTrue(isSafeToDig(world, listOf(BlockPos(1, 1, 0), BlockPos(1, 0, 0), BlockPos(1, -1, 0)), BlockPos(1, -2, 0)))
    }

    @Test
    fun `a step next to lava or over a cave is not`() {
        val cells = listOf(BlockPos(1, 1, 0), BlockPos(1, 0, 0), BlockPos(1, -1, 0))
        assertFalse(isSafeToDig(ground().set(2, -1, 0, Blocks.LAVA.defaultBlockState()), cells, BlockPos(1, -2, 0)))
        assertFalse(isSafeToDig(ground().set(1, -2, 0, Blocks.AIR.defaultBlockState()), cells, BlockPos(1, -2, 0)))
        assertFalse(isSafeToDig(ground().set(1, 2, 0, Blocks.GRAVEL.defaultBlockState()), cells, BlockPos(1, -2, 0)))
    }

    @Test
    fun `a shelter is dug into firm ground, never into water or a cave`() {
        val top = BlockPos(0, 1, 0)
        assertTrue(canDigIn(ground(), top))
        assertFalse(canDigIn(ground().set(1, -1, 0, Blocks.WATER.defaultBlockState()), top))
        assertFalse(canDigIn(ground().set(0, -3, 0, Blocks.AIR.defaultBlockState()), top))
        assertFalse(canDigIn(ground().set(0, 0, 0, McBlocks.SAND.defaultBlockState()), top))
    }

    @Test
    fun `ores in a wall are found nearest first, buried ones not seen`() {
        val world = ground()
            .fill(5, 1, 0, 5, 3, 0, Blocks.AIR.defaultBlockState())
            .set(6, 1, 0, Blocks.IRON_ORE.defaultBlockState())
            .set(6, 2, 0, Blocks.IRON_ORE.defaultBlockState())
            .set(-3, -5, 0, Blocks.IRON_ORE.defaultBlockState())
            .set(2, 0, 0, Blocks.IRON_ORE.defaultBlockState())
        val found = world.nearestBlocks(BlockPos(0, 1, 0), 10, 8, 10) { pos, state ->
            state.`is`(Blocks.IRON_ORE) && world.isExposed(pos)
        }
        assertEquals(listOf(BlockPos(2, 0, 0), BlockPos(6, 1, 0), BlockPos(6, 2, 0)), found)
    }
}
