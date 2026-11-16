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

package net.ccbluex.agent.skills.loot

import net.ccbluex.agent.skills.CARRIED_SLOTS
import net.ccbluex.agent.skills.buildingBlocks
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.skills.isBuildingBlock
import net.ccbluex.agent.skills.itemId

import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * Which items make the agent better off: better armor or weapon, blocks, golden apples, pearls, and food, tools and
 * things to shoot it lacks, each up to its cap.
 *
 * @property blocks building blocks wanted at most
 * @property arrows arrows wanted at most
 * @property throwables snowballs and eggs wanted at most
 * @agent claude-opus-5-5
 */
public data class Wanted @JvmOverloads constructor(
    val blocks: Int = 64,
    val arrows: Int = 32,
    val throwables: Int = 32,
) {

    public fun wants(player: Player, stack: ItemStack): Boolean = wants(player.inventory, stack)

    internal fun wants(inventory: Inventory, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (GearScore.armorSlot(stack) != null || GearScore.weaponScore(stack) > 0.0) {
            return GearScore.isUpgrade(inventory, stack)
        }
        return when {
            isBuildingBlock(stack) -> inventory.buildingBlocks() < blocks
            stack.`is`(Items.GOLDEN_APPLE) || stack.`is`(Items.ENCHANTED_GOLDEN_APPLE) -> true
            stack.`is`(Items.ENDER_PEARL) -> true
            (stack.food != null) -> count(inventory) { (it.food != null) } == 0
            stack.itemId.endsWith(PICKAXE) -> count(inventory) { it.itemId.endsWith(PICKAXE) } == 0
            stack.itemId.endsWith(SHOVEL) -> count(inventory) { it.itemId.endsWith(SHOVEL) } == 0
            stack.`is`(Items.BOW) || stack.`is`(Items.FISHING_ROD) -> count(inventory, stack.item) == 0
            stack.`is`(Items.ARROW) -> count(inventory, Items.ARROW) < arrows
            isThrowable(stack) -> count(inventory, ::isThrowable) < throwables
            else -> false
        }
    }

    private fun count(inventory: Inventory, item: Item) = count(inventory) { it.`is`(item) }

    private fun count(inventory: Inventory, match: (ItemStack) -> Boolean): Int = CARRIED_SLOTS.sumOf { slot ->
        inventory.getItem(slot).let { if (match(it)) it.count else 0 }
    }

    public companion object {
        @JvmField
        public val DEFAULT: Wanted = Wanted()

        private const val PICKAXE = "_pickaxe"
        private const val SHOVEL = "_shovel"

        private fun isThrowable(stack: ItemStack) = stack.`is`(Items.SNOWBALL) || stack.`is`(Items.EGG)
    }
}
