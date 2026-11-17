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

import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.BlockState

/**
 * Which blocks the walk may break when [Options.canBreak] allows breaking at all, as on a server shared with players
 * whose builds are theirs. Route searches ask it from their own thread.
 *
 * @agent claude-opus-5-5
 */
public fun interface BreakableBlocks {

    public fun isBreakable(state: BlockState): Boolean

    public companion object {

        /**
         * Every block.
         */
        @JvmField
        public val ANY: BreakableBlocks = BreakableBlocks { true }

        /**
         * The blocks the world generates as ground, stone, ores and trees, which anyone on a shared server may dig;
         * whatever else stands may be a player's build or farm, so the walk goes around it.
         */
        @JvmField
        public val NATURAL: BreakableBlocks = BreakableBlocks { state ->
            state.block in NATURAL_BLOCKS || NATURAL_TAGS.any { state.`is`(it) }
        }
    }
}

// Lazy, as Options() makes the companion before a game may have bootstrapped the blocks
private val NATURAL_TAGS by lazy {
    listOf(
        BlockTags.SUBSTRATE_OVERWORLD, BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER, BlockTags.SAND,
        BlockTags.SNOW, BlockTags.ORES, BlockTags.LEAVES, BlockTags.OVERWORLD_NATURAL_LOGS,
    )
}

private val NATURAL_BLOCKS by lazy {
    setOf(Blocks.GRAVEL, Blocks.CLAY, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK)
}
