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

import net.ccbluex.agent.world.Slot
import net.ccbluex.agent.world.CraftingMenu
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.gather.nearestBlocks
import net.ccbluex.agent.skills.placeBlock
import net.ccbluex.agent.skills.useBlock
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.math.AABB

/**
 * Crafts [recipe] up to [times] times, in the own inventory's grid or at a crafting table: one within 16 blocks, or
 * one it places from the inventory. Lays the ingredients out with clicks as a player does; how many times it crafted.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.craft(recipe: GridRecipe, times: Int = 1): Int {
    val player = body.player
    if (recipe.needsTable) {
        if (!openStation(Blocks.CRAFTING_TABLE, Items.CRAFTING_TABLE) { it is CraftingMenu }) return 0
    } else if (body.containerMenu != null) {
        body.closeContainer()
    }
    val menu = (body.containerMenu ?: body.inventoryMenu) as? CraftingMenu ?: return 0
    var crafted = 0
    try {
        clearGrid(menu)
        waitTicks(3)
        while (crafted < times && body.player.isAlive) {
            // Clients work out no crafting result, so only the result the server shows counts
            if (tickConditional(10) { menu.resultSlot.item.isEmpty }) break
            if (!lay(menu, recipe)) break
            if (tickConditional(20) { menu.resultSlot.item.`is`(recipe.result) }) break
            if (!take(menu)) break
            crafted++
            waitTicks(2)
        }
    } finally {
        clearGrid(menu)
        body.closeContainer()
    }
    return crafted
}

/**
 * Puts one craft's ingredients into the grid of [menu], top left; false when some are missing.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.lay(menu: CraftingMenu, recipe: GridRecipe): Boolean {
    if (recipe.width > menu.gridWidth || recipe.height > menu.gridHeight) return false
    if (recipe.timesFrom(carried(menu).map { it.item }) < 1) return false
    val grid = menu.inputGridSlots
    var source: Slot? = null
    for ((index, cell) in recipe.cells.withIndex()) {
        if (cell == null) continue
        val target = grid[index / recipe.width * menu.gridWidth + index % recipe.width]
        if (menu.carried.isEmpty || menu.carried.item !in cell) {
            source?.let { putBack(menu, it) }
            val next = carried(menu).firstOrNull { it.item.item in cell } ?: return false
            source = next
            body.click(menu, next.index, 0, ContainerInput.PICKUP)
        }
        body.click(menu, target.index, 1, ContainerInput.PICKUP)
    }
    source?.let { putBack(menu, it) }
    return true
}

/**
 * The slots of [menu] that hold the player's main inventory with something in them.
 *
 * @agent claude-opus-5-5
 */
internal fun carried(menu: ContainerMenu): List<Slot> =
    menu.slots.filter { it.isPlayerInventory && it.containerSlot < 36 && !it.item.isEmpty }

/**
 * Drops what the cursor holds back into [slot].
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.putBack(menu: ContainerMenu, slot: Slot) {
    if (!menu.carried.isEmpty) body.click(menu, slot.index, 0, ContainerInput.PICKUP)
}

/**
 * Takes the result onto the cursor and puts it into the inventory, onto the same item or an empty slot. A shift
 * click would do it in one, but the client predicts it from a result it never updates and crafts on in its own view.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.take(menu: CraftingMenu): Boolean {
    body.click(menu, menu.resultSlot.index, 0, ContainerInput.PICKUP)
    val result = menu.carried
    if (result.isEmpty) return false
    val slots = menu.slots.filter { it.isPlayerInventory && it.containerSlot < 36 }
    val target = slots.firstOrNull {
        ItemStack.isSameItemSameComponents(it.item, result) && it.item.count + result.count <= it.item.maxStackSize
    } ?: slots.firstOrNull { it.item.isEmpty }
    // With no room it goes on the ground, never back onto the result, which would craft again
    body.click(menu, target?.index ?: ContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ContainerInput.PICKUP)
    return target != null
}

/**
 * Moves whatever the grid holds back into the inventory; every slot is clicked, as the client's view of the grid
 * can lag behind the server's.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.clearGrid(menu: CraftingMenu) {
    for (slot in menu.inputGridSlots) body.click(menu, slot.index, 0, ContainerInput.QUICK_MOVE)
}

/**
 * A click on [menu]: the open container, or the own inventory while none is open.
 *
 * @agent claude-opus-5-5
 */
internal fun AgentBody.click(menu: ContainerMenu, slot: Int, button: Int, type: ContainerInput) {
    if (menu === inventoryMenu) clickInventory(slot, button, type) else clickContainer(slot, button, type)
}

/**
 * Opens [block] within 16 blocks, placing one of [item] next to the player when there is none, until the menu
 * passes [isOpen].
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.openStation(block: Block, item: Item, isOpen: (ContainerMenu) -> Boolean): Boolean {
    body.containerMenu?.let { if (isOpen(it)) return true else body.closeContainer() }
    val feet = body.player.blockPosition()
    val station = body.level.nearestBlocks(feet, 16, 8, 1) { _, state -> state.`is`(block) }.firstOrNull()
        ?: placeNearby(item)
        ?: return false
    return useBlock(station) { body.containerMenu?.let(isOpen) == true }
}

/**
 * Places [item] from the inventory on the ground next to the player; where, or null when it carries none or there is
 * no room.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.placeNearby(item: Item): BlockPos? {
    val slot = (0 until 36).firstOrNull { body.player.inventory.getItem(it).`is`(item) } ?: return null
    for (spot in groundSpots()) {
        if (placeBlock(slot, spot)) return spot
    }
    return null
}

/**
 * Free cells on solid ground one or two blocks around the player, nearest first.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.groundSpots(): List<BlockPos> {
    val level = body.level
    val feet = body.player.blockPosition()
    return (-2..2).flatMap { dx -> (-2..2).map { dz -> feet.offset(dx, 0, dz) } }
        .filter { it != feet && it != feet.above() }
        .flatMap { listOf(it, it.below(), it.above()) }
        .filter { spot ->
            val below = spot.below()
            level.getBlockState(spot).canBeReplaced() && level.getFluidState(spot).isEmpty &&
                level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) &&
                !body.player.boundingBox.intersects(AABB(spot))
        }
        .sortedBy { it.distSqr(feet) }
}

/**
 * The carried stacks of [items], counted.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.countOf(items: Set<Item>): Int =
    (0 until 36).sumOf { slot -> body.player.inventory.getItem(slot).let { if (it.item in items) it.count else 0 } }
