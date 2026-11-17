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
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.Blocks
import net.minecraft.world.level.block.Block
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.world.level.block.Blocks as McBlocks

/**
 * An animal near a fence, gate or wall is someone's livestock in a pen, which a hunt leaves alone.
 *
 * @agent claude-opus-5-5
 */
class LivestockTest {

    init {
        TestInventories.bootstrap()
    }

    @Test
    fun `an animal by a fence, gate or wall is penned`() {
        val at = BlockPos(0, 64, 0)
        assertFalse(isPenned(Placed(emptyMap()), at))
        assertTrue(isPenned(Placed(mapOf(BlockPos(4, 64, 0) to state(McBlocks.OAK_FENCE))), at))
        assertTrue(isPenned(Placed(mapOf(BlockPos(0, 64, 5) to state(McBlocks.OAK_FENCE_GATE))), at))
        assertTrue(isPenned(Placed(mapOf(BlockPos(0, 65, -3) to state(McBlocks.COBBLESTONE_WALL))), at))
    }

    @Test
    fun `a fence farther off or far above is no pen`() {
        val at = BlockPos(0, 64, 0)
        assertFalse(isPenned(Placed(mapOf(BlockPos(8, 64, 0) to state(McBlocks.OAK_FENCE))), at))
        assertFalse(isPenned(Placed(mapOf(BlockPos(2, 67, 0) to state(McBlocks.OAK_FENCE))), at))
    }

    private fun state(block: Block): BlockState = block.defaultBlockState().toAgent()

    /**
     * Air but for [states].
     */
    private class Placed(private val states: Map<BlockPos, BlockState>) : BlockGetter {
        override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultBlockState()
        override val height: Int get() = 384
        override val minY: Int get() = -64
    }
}
