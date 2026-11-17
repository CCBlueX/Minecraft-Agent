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
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * Hunger at or below which a player eats; a player with nothing to eat goes for food then.
 */
@InternalAgentApi
public const val HUNGRY: Int = 14

/**
 * Hunger up to which a player goes on eating once it got to [HUNGRY].
 */
@InternalAgentApi
public const val FED: Int = 18

/**
 * Food that hurts, poisons or teleports.
 */
private val BAD_FOOD = setOf(
    Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.CHICKEN,
    Items.SUSPICIOUS_STEW,
)

/**
 * Whether [stack] is food to eat when hungry: no golden apple, which is kept for healing, and nothing that hurts,
 * poisons or teleports.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun isPlainFood(stack: ItemStack): Boolean =
    stack.food != null && !stack.`is`(Items.GOLDEN_APPLE) &&
        !stack.`is`(Items.ENCHANTED_GOLDEN_APPLE) && stack.item !in BAD_FOOD

/**
 * The main inventory or offhand slot with the plain food that fills the most hunger; -1 without any.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun Inventory.bestFoodSlot(): Int {
    var best = -1
    var nutrition = 0
    for (slot in (0 until Inventory.INVENTORY_SIZE) + Inventory.SLOT_OFFHAND) {
        val stack = getItem(slot)
        if (!isPlainFood(stack)) continue
        val value = stack.food?.nutrition ?: 0
        if (value > nutrition) {
            best = slot
            nutrition = value
        }
    }
    return best
}
