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
package net.ccbluex.agent.skills.build

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.INVENTORY_SLOTS
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.count
import net.ccbluex.agent.skills.useBlock
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack

/**
 * The chests a build takes its items from, and the items they turned out not to have.
 *
 * @agent claude-opus-5-5
 */
internal class Supplies(private val agent: Agent, private val chests: Collection<BlockPos>, private val scaffold: Scaffold) {

    private val lacking = HashSet<Item>()

    fun mayHave(item: Item): Boolean = chests.isNotEmpty() && item !in lacking

    /**
     * Takes items from the chests, nearest first: [first], then the others of [upcoming], the items the blocks left
     * take in build order, as many of each as are left to place and the inventory holds. Items carried that nothing
     * left takes go back to make room, and a stack of blocks to stand on comes along when none is carried. [first]
     * is lacking from then on when no chest gave any.
     */
    suspend fun restock(first: Item, upcoming: List<Item>) {
        val inventory = agent.body.player.inventory
        val left = upcoming.groupingBy { it }.eachCount()
        val wanted = LinkedHashMap<Item, Int>()
        for (item in listOf(first) + upcoming) {
            if (item in wanted || item in lacking) continue
            val count = maxOf(left[item] ?: 1, 1) - inventory.count(ItemMatch.of(item))
            if (count > 0) wanted[item] = count
        }
        val position = agent.body.player.position()
        for (chest in chests.sortedBy { it.distToCenterSqr(position) }) {
            if (wanted.isEmpty()) break
            take(chest, wanted, left)
        }
        if (inventory.count(ItemMatch.of(first)) == 0) lacking += first
    }

    private suspend fun take(chest: BlockPos, wanted: MutableMap<Item, Int>, left: Map<Item, Int>) {
        val body = agent.body
        val inventory = body.player.inventory
        if (!agent.useBlock(chest) { body.containerMenu != null }) return
        var spare = !scaffold.hasSpare()
        try {
            while (true) {
                val menu = body.containerMenu ?: return
                val order = wanted.keys.toList()
                val slot = menu.slots
                    .filter { !it.isPlayerInventory && (it.item.item in wanted || spare && scaffold.isSpare(it.item)) }
                    .minByOrNull { order.indexOf(it.item.item).takeIf { i -> i >= 0 } ?: order.size }
                    ?: return
                val item = slot.item.item
                if (!hasRoom(slot.item)) {
                    val unneeded = menu.slots.firstOrNull {
                        it.isPlayerInventory && it.containerSlot < INVENTORY_SLOTS && !it.item.isEmpty &&
                            it.item.item !in left && !it.item.isDamageableItem && !scaffold.isSpare(it.item)
                    } ?: return
                    val back = ItemMatch.of(unneeded.item.item)
                    val before = inventory.count(back)
                    body.clickContainer(unneeded.index, 0, ContainerInput.QUICK_MOVE)
                    waitTicks(2)
                    // A full chest takes nothing back; trying again would never end
                    if (inventory.count(back) >= before) return
                    continue
                }
                val carried = inventory.count(ItemMatch.of(item))
                body.takeFromContainer(slot.index)
                waitTicks(2)
                val got = inventory.count(ItemMatch.of(item)) - carried
                if (got <= 0) return
                val still = wanted[item]
                if (still == null) {
                    spare = false
                } else if (still <= got) {
                    wanted -= item
                } else {
                    wanted[item] = still - got
                }
            }
        } finally {
            body.closeContainer()
        }
    }

    private fun hasRoom(stack: ItemStack): Boolean {
        val inventory = agent.body.player.inventory
        return (0 until INVENTORY_SLOTS).any {
            val carried = inventory.getItem(it)
            carried.isEmpty || carried.item == stack.item && carried.count < carried.maxStackSize
        }
    }
}
