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

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.block.BlockState

/**
 * Whether [stack] is a tool made for [state], one the block drops its item for: a pickaxe for stone, an axe for logs,
 * a shovel for dirt. Any other tool wears out digging it for little or nothing, as a sword on leaves or a pickaxe on
 * dirt does.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun isToolFor(stack: ItemStack, state: BlockState): Boolean =
    stack.isToolCorrectForDrops(state)

/**
 * Whether digging with [stack] in hand costs it nothing; only tools wear from digging.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun isWearFree(stack: ItemStack): Boolean = !stack.isTool
