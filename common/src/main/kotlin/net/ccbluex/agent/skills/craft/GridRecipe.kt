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

package net.ccbluex.agent.skills.craft

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack

/**
 * How [count] of [result] are crafted: [cells] of a grid [width] wide, row by row, each the items that may lie there
 * or null for an empty cell.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class GridRecipe(
    public val result: Item,
    public val count: Int,
    public val width: Int,
    public val cells: List<Set<Item>?>,
) {

    public val height: Int
        get() = cells.size / width

    /**
     * Whether it is too big for the own inventory's 2 by 2 grid.
     */
    public val needsTable: Boolean
        get() = width > 2 || height > 2

    /**
     * How many items each ingredient takes per craft; cells with the same items are one ingredient.
     */
    public val ingredients: Map<Set<Item>, Int>
        get() = cells.filterNotNull().groupingBy { it }.eachCount()

    /**
     * How many times it can be crafted from [stacks].
     */
    public fun timesFrom(stacks: List<ItemStack>): Int = ingredients.minOf { (items, perCraft) ->
        stacks.sumOf { if (it.item in items) it.count else 0 } / perCraft
    }

    override fun toString(): String = "${count}x $result"
}
