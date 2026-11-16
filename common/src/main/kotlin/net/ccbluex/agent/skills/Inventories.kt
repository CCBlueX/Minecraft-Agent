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

package net.ccbluex.agent.skills

import net.ccbluex.agent.navigation.PlaceableBlocks
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.item.ItemStack

/**
 * Main inventory slots: hotbar 0 to 8, then 9 to 35.
 */
internal const val INVENTORY_SLOTS = 36
internal const val OFFHAND_SLOT = 40
internal const val HOTBAR_SLOTS = 9

/**
 * The hotbar slot items from the rest of the inventory are swapped into to be held.
 */
internal const val SCRATCH_SLOT = 8

/**
 * Slots that hold items the agent carries: the main inventory and the offhand.
 */
internal val CARRIED_SLOTS: IntArray = IntArray(INVENTORY_SLOTS) { it } + OFFHAND_SLOT

/**
 * @agent claude-opus-5-5
 */
internal fun Inventory.count(match: ItemMatch): Int =
    CARRIED_SLOTS.sumOf { slot -> getItem(slot).let { if (match.matches(it)) it.count else 0 } }

/**
 * The first slot holding a match, hotbar first; -1 for none.
 */
internal fun Inventory.slotOf(match: ItemMatch): Int = CARRIED_SLOTS.firstOrNull { match.matches(getItem(it)) } ?: -1

/**
 * The slot whose stack scores best by [score], ignoring stacks scoring 0 or less; -1 for none.
 */
internal fun Inventory.bestSlot(score: (ItemStack) -> Double): Int {
    var best = -1
    var bestScore = 0.0
    for (slot in CARRIED_SLOTS) {
        val value = score(getItem(slot))
        if (value > bestScore) {
            best = slot
            bestScore = value
        }
    }
    return best
}

/**
 * Whether the stack is a block to bridge and build with.
 */
internal fun isBuildingBlock(stack: ItemStack): Boolean = !stack.isEmpty && PlaceableBlocks.DEFAULT.isPlaceable(stack)

internal fun Inventory.buildingBlocks(): Int = count(::isBuildingBlock)

/**
 * Selects the item in inventory [slot] in the main hand: a hotbar slot directly, another one after swapping it
 * into [SCRATCH_SLOT]. The body keeps it selected until [AgentBody.releaseHotbarSlot]; the hotbar slot it is in.
 *
 * @agent claude-opus-5-5
 */
internal fun AgentBody.hold(slot: Int): Int {
    val hotbar = if (slot in 0 until HOTBAR_SLOTS) {
        slot
    } else {
        swapWithHotbar(slot, SCRATCH_SLOT)
        SCRATCH_SLOT
    }
    selectHotbarSlot(hotbar)
    return hotbar
}
