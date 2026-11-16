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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.EmptyBlockGetter
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.VoxelShape
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [PlayerMotion] on slime and beds, held against Entity.move, Entity.restituteMovementAfterCollisions, Block.fallOn and
 * SlimeBlock worked through by hand.
 *
 * From LiquidBounce: [PlayerMotionTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotionTest.kt#L34-L140)
 *
 * @agent claude-opus-5-5
 */
class PlayerMotionTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val AIR = Blocks.AIR.defaultBlockState().toAgent()
        private val SLIME = Blocks.SLIME_BLOCK.defaultBlockState().toAgent()
        private val BED = Blocks.BED.red().defaultBlockState().toAgent()
        private const val BOUNCE_TICKS = 60
        private const val WALK_TICKS = 40
    }

    /**
     * A slime floor at y 0, so the feet rest at y 1.
     */
    private fun slime() = Grid().fill(-20, 0, -20, 20, 0, 20, SLIME)

    /**
     * A stone floor at y 0 with a bed on it, so the feet rest at y 1.5625 on the bed.
     */
    private fun bed() = Grid.flat().set(0, 1, 0, BED)

    /**
     * Drops the player from rest at [height] and returns the highest the feet get after the first landing.
     */
    private fun bounceHeight(grid: Grid, height: Double, sneak: Boolean = false): Double {
        val motion = PlayerMotion(grid)
        motion.reset(Vec3(0.5, height, 0.5), Vec3.ZERO, onGround = false)
        var landed = false
        var peak = 0.0
        repeat(BOUNCE_TICKS) {
            motion.tick(0f, 0, 0, jump = false, sprint = false, sneak = sneak)
            landed = landed || motion.onGround
            if (landed) {
                peak = maxOf(peak, motion.y)
            }
        }
        return peak
    }

    @Test
    fun `a fall onto slime bounces back up`() {
        // 17 ticks of falling end 0.5 into the 18th; the rebound gets 0.98 drag for the part of it moved that tick.
        assertEquals(7.5065, bounceHeight(slime(), 11.0), 1.0E-3)
        assertEquals(5.2417, bounceHeight(slime(), 7.0), 1.0E-3)
    }

    @Test
    fun `sneaking onto slime does not bounce`() {
        assertEquals(1.0, bounceHeight(slime(), 11.0, sneak = true), 1.0E-9)
    }

    @Test
    fun `a fall onto a bed bounces back three quarters of the speed`() {
        // Entity.getOnPosLegacy, 0.2 below the feet at 1.5625, is the bed itself and not the floor under it.
        assertEquals(5.1062, bounceHeight(bed(), 11.0), 1.0E-3)
        assertEquals(3.7540, bounceHeight(bed(), 7.0), 1.0E-3)
    }

    @Test
    fun `sneaking onto a bed does not bounce`() {
        assertEquals(1.5625, bounceHeight(bed(), 11.0, sneak = true), 1.0E-9)
    }

    @Test
    fun `a fall onto carpet on slime bounces as onto the slime`() {
        val grid = slime().fill(-20, 1, -20, 20, 1, 20, Blocks.CARPET.red().defaultBlockState().toAgent())
        assertEquals(7.5364, bounceHeight(grid, 11.0), 1.0E-3)
    }

    @Test
    fun `a fall onto a bed hurts half`() {
        val motion = PlayerMotion(bed())
        motion.reset(Vec3(0.5, 11.0, 0.5), Vec3.ZERO, onGround = false)
        while (!motion.onGround) {
            motion.tick(0f, 0, 0, jump = false, sprint = false)
        }

        assertEquals((11.0 - 1.5625) / 2, motion.landingFall, 1.0E-9)
    }

    @Test
    fun `a fall onto slime does not hurt`() {
        val onSlime = PlayerMotion(slime())
        onSlime.reset(Vec3(0.5, 11.0, 0.5), Vec3.ZERO, onGround = false)
        val onStone = PlayerMotion(Grid.flat())
        onStone.reset(Vec3(0.5, 11.0, 0.5), Vec3.ZERO, onGround = false)
        while (!onSlime.onGround || !onStone.onGround) {
            if (!onSlime.onGround) onSlime.tick(0f, 0, 0, jump = false, sprint = false)
            if (!onStone.onGround) onStone.tick(0f, 0, 0, jump = false, sprint = false)
        }

        assertEquals(0.0, onSlime.landingFall)
        assertEquals(10.0, onStone.landingFall, 1.0E-9)
    }

    @Test
    fun `walking on slime is slowed after gravity pulls the feet down`() {
        // SlimeBlock.stepOn scales by 0.4 + |motionY| * 0.2, and motionY is the -0.0784 of standing after the move.
        val motion = PlayerMotion(slime())
        motion.reset(Vec3(0.5, 1.0, 0.5), PlayerMotion.RESTING, onGround = true)
        repeat(WALK_TICKS) {
            motion.tick(0f, 1, 0, jump = false, sprint = false)
        }
        val from = motion.z
        motion.tick(0f, 1, 0, jump = false, sprint = false)

        val accelerate = 0.98 * 0.1 * 0.21600002f / (0.8f * 0.8f * 0.8f)
        val keep = 0.8f * 0.91f * (0.4 + 0.0784 * 0.2)
        assertEquals(accelerate / (1 - keep), motion.z - from, 1.0E-5)
        assertTrue(motion.onGround)
    }

    /**
     * Blocks set by hand, air everywhere else, with the shapes the blocks have on their own.
     *
     * @agent claude-opus-5-5
     */
    private class Grid : BlockGrid {

        private val blocks = HashMap<BlockPos, BlockState>()

        fun fill(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, state: BlockState) = apply {
            for (pos in BlockPos.betweenClosed(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2))) {
                blocks[pos.immutable()] = state
            }
        }

        fun set(x: Int, y: Int, z: Int, state: BlockState) = fill(x, y, z, x, y, z, state)

        override fun state(x: Int, y: Int, z: Int): BlockState =
            blocks[BlockPos(x, y, z)] ?: AIR

        override fun shape(x: Int, y: Int, z: Int): VoxelShape =
            state(x, y, z).getCollisionShape(EmptyBlockGetter, BlockPos(x, y, z))

        companion object {
            /**
             * A stone floor at y 0 from -40 to 40, so the feet stand at y 1.
             */
            fun flat() = Grid().fill(-40, 0, -40, 40, 0, 40, Blocks.STONE.defaultBlockState().toAgent())
        }
    }
}
