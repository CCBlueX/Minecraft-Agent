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

package net.ccbluex.agent.v26_3.paper.body

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput

/**
 * The inventory clicks of a body, run as the server runs a client's container clicks: on the menu, then the changes
 * go out. Slots are the indices [Inventory] uses: 0 to 8 hotbar, 9 to 35 the rest, 36 to 39 armor feet to head, 40
 * offhand.
 *
 * @agent claude-opus-5-5
 */
internal class InventoryClicks(private val player: BotPlayer) {

    /**
     * Swaps the stack in [inventorySlot] with the hotbar's, as the number keys do over a slot.
     */
    fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int) {
        require(hotbarSlot in 0 until Inventory.SELECTION_SIZE) { "Not a hotbar slot: $hotbarSlot" }
        own(inventorySlot, hotbarSlot, ContainerInput.SWAP)
    }

    fun swapWithOffhand(inventorySlot: Int) {
        own(inventorySlot, Inventory.SLOT_OFFHAND, ContainerInput.SWAP)
    }

    fun drop(inventorySlot: Int, wholeStack: Boolean) {
        own(inventorySlot, if (wholeStack) 1 else 0, ContainerInput.THROW)
    }

    /**
     * A click on the open menu, the own inventory's when none is open.
     */
    fun click(menuSlot: Int, button: Int, type: ContainerInput) {
        val menu = player.containerMenu
        if (menuSlot !in menu.slots.indices) return
        menu.clicked(menuSlot, button, type, player)
        menu.broadcastChanges()
    }

    private fun own(inventorySlot: Int, button: Int, type: ContainerInput) {
        val menu = player.inventoryMenu
        menu.clicked(menuSlot(inventorySlot), button, type, player)
        menu.broadcastChanges()
    }

    private companion object {
        const val HOTBAR_START = 36
        const val FEET = 36
        const val HEAD = 39
        const val FEET_SLOT = 8
        const val OFFHAND_SLOT = 45

        // InventoryMenu lists the armor head first and the hotbar after the rest of the inventory
        fun menuSlot(inventorySlot: Int): Int = when (inventorySlot) {
            in 0 until Inventory.SELECTION_SIZE -> HOTBAR_START + inventorySlot
            in Inventory.SELECTION_SIZE until FEET -> inventorySlot
            in FEET..HEAD -> FEET_SLOT - (inventorySlot - FEET)
            Inventory.SLOT_OFFHAND -> OFFHAND_SLOT
            else -> throw IllegalArgumentException("Not an inventory slot: $inventorySlot")
        }
    }
}
