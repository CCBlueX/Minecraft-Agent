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

package net.ccbluex.agent.skills.farm

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.core.Direction
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
class CropsTest {

    @BeforeTest
    fun bootstrap() = MinecraftBootstrap.ensureInitialized()

    @Test
    fun `wheat is ripe only grown full`() {
        val world = TestWorld()
            .set(0, 0, 0, Blocks.FARMLAND.defaultBlockState())
            .set(0, 1, 0, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE))
            .set(1, 0, 0, Blocks.FARMLAND.defaultBlockState())
            .set(1, 1, 0, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3))
        assertTrue(world.isRipe(0, 1, 0))
        assertFalse(world.isRipe(1, 1, 0))
    }

    @Test
    fun `sugar cane is cut above its bottom block only`() {
        val world = TestWorld().fill(0, 1, 0, 0, 3, 0, Blocks.SUGAR_CANE.defaultBlockState())
        assertFalse(world.isRipe(0, 1, 0))
        assertTrue(world.isRipe(0, 2, 0))
        assertFalse(world.isRipe(0, 3, 0))
    }

    @Test
    fun `a melon is ripe when a stem grew it, not on its own`() {
        val stem = Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST)
        val world = TestWorld()
            .set(0, 1, 0, stem)
            .set(1, 1, 0, Blocks.MELON.defaultBlockState())
            .set(5, 1, 0, Blocks.MELON.defaultBlockState())
        assertTrue(world.isRipe(1, 1, 0))
        assertFalse(world.isRipe(5, 1, 0))
    }

    private fun TestWorld.isRipe(x: Int, y: Int, z: Int): Boolean {
        val pos = BlockPos(x, y, z)
        return Crops.isRipe(this, pos, getBlockState(pos))
    }
}
