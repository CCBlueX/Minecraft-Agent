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

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.craft.GridRecipes
import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.BlockState

/**
 * A natural block a player mines for what it drops, never one a player could have placed: the pickaxe [tier] it needs
 * (0 none, 1 wood, 2 stone, 3 iron), the height it is mined at when none is in sight ([depth], null on the surface),
 * how many are mined in one go when some are needed ([batch]), and the items it gives ([drops]).
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public enum class Resource(public val tier: Int, public val depth: Int?, public val batch: Int) {
    LOGS(0, null, 6),
    STONE(1, null, 16),
    COAL(1, 40, 8),
    IRON(2, 16, 3),
    DIAMOND(3, -54, 1),
    ;

    public fun matches(state: BlockState): Boolean = when (this) {
        LOGS -> state.`is`(BlockTags.OVERWORLD_NATURAL_LOGS)
        else -> state.block in blocks
    }

    private val blocks: Set<Block> by lazy {
        when (this) {
            LOGS -> emptySet()
            STONE -> setOf(Blocks.STONE, Blocks.DEEPSLATE)
            COAL -> setOf(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)
            IRON -> setOf(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)
            DIAMOND -> setOf(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
        }
    }

    public val drops: Set<Item> by lazy {
        when (this) {
            LOGS -> GridRecipes.logs
            STONE -> setOf(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE)
            COAL -> setOf(Items.COAL)
            IRON -> setOf(Items.RAW_IRON)
            DIAMOND -> setOf(Items.DIAMOND)
        }
    }

    public companion object {

        /**
         * The [tier] of the resources [item] mines when it is a pickaxe: 1 wood or gold, 2 stone, 3 iron, 4 diamond or
         * netherite; 0 for anything else.
         */
        @JvmStatic
        public fun pickaxeTier(item: Item): Int = when (item) {
            Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE -> 1
            Items.STONE_PICKAXE -> 2
            Items.IRON_PICKAXE -> 3
            Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE -> 4
            else -> 0
        }
    }
}
