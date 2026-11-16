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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.EmptyBlockGetter
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.item.ItemStack

/**
 * Which item stacks the walk places as blocks, and which of them it only takes when nothing else is at hand.
 *
 * @agent claude-opus-5-5
 */
public fun interface PlaceableBlocks {

    public fun isPlaceable(stack: ItemStack): Boolean

    /**
     * Placeable, but worse to walk on, such as ice or slime.
     */
    public fun isUnfavourable(stack: ItemStack): Boolean = false

    public companion object {

        /**
         * Full solid blocks without a block entity, gravity or a use of their own; slippery or slow ones last.
         */
        @JvmStatic
        public val DEFAULT: PlaceableBlocks = object : PlaceableBlocks {
            override fun isPlaceable(stack: ItemStack): Boolean {
                val block = stack.item.block ?: return false
                val state = block.defaultBlockState()
                return !block.`is`(BlockKind.ENTITY_BLOCK) && !block.`is`(BlockKind.FALLING) &&
                    !block.`is`(BlockKind.INTERACTIVE) &&
                    state.isCollisionShapeFullBlock(EmptyBlockGetter, BlockPos.ZERO)
            }

            override fun isUnfavourable(stack: ItemStack): Boolean {
                val block = stack.item.block ?: return false
                return block.friction != VANILLA_FRICTION || block.speedFactor != 1f || block.jumpFactor != 1f
            }
        }

        private const val VANILLA_FRICTION = 0.6f
    }
}
