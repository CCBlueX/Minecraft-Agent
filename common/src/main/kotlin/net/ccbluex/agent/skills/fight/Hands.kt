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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.gear.GearScore
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/**
 * What the fight holds, over the body's real inventory: an item from the hotbar is selected, one from the rest of
 * the inventory is swapped onto [SCRATCH_SLOT] first. The selection is held every tick until [release].
 *
 * @agent claude-opus-5-5
 */
internal class Hands(private val body: AgentBody) {

    private val inventory: Inventory
        get() = body.player.inventory

    /**
     * The hotbar slot the fight holds, null while it leaves the selection alone.
     */
    private var held: Int? = null

    /**
     * Whether the fight holds a slot: not before it first took its weapon, nor once it stood down.
     */
    val holding: Boolean
        get() = held != null

    val mainHand: ItemStack
        get() = body.player.mainHandItem

    val offHand: ItemStack
        get() = body.player.offhandItem

    /**
     * The inventory slot of the first stack [matches], hotbar first; null without one.
     */
    fun find(matches: (ItemStack) -> Boolean): Int? =
        (0 until Inventory.INVENTORY_SIZE).firstOrNull { slot ->
            inventory.getItem(slot).let { !it.isEmpty && matches(it) }
        }

    fun has(matches: (ItemStack) -> Boolean): Boolean = find(matches) != null

    fun count(matches: (ItemStack) -> Boolean): Int =
        (0 until Inventory.INVENTORY_SIZE).sumOf { slot ->
            inventory.getItem(slot).let { if (!it.isEmpty && matches(it)) it.count else 0 }
        }

    fun stack(slot: Int): ItemStack = inventory.getItem(slot)

    /**
     * Holds the first stack that [matches] in the main hand; false without one.
     */
    fun hold(matches: (ItemStack) -> Boolean): Boolean {
        if (!mainHand.isEmpty && matches(mainHand)) {
            held = inventory.selectedSlot
            return true
        }
        val slot = find(matches) ?: return false
        hold(slot)
        return true
    }

    fun hold(slot: Int) {
        val hotbar = if (Inventory.isHotbarSlot(slot)) slot else SCRATCH_SLOT.also { body.swapWithHotbar(slot, it) }
        held = hotbar
        body.selectHotbarSlot(hotbar)
    }

    /**
     * The weapon held between skills: the best scored of the highest [weaponRank]; null without any.
     */
    fun primary(): Int? = (0 until Inventory.INVENTORY_SIZE)
        .filter { inventory.getItem(it).weaponRank > 0 }
        .maxWithOrNull(
            compareBy({ inventory.getItem(it).weaponRank }, { GearScore.weaponScore(inventory.getItem(it)) }),
        )

    fun holdPrimary() {
        val weapon = primary()
        if (weapon == null || weapon == inventory.selectedSlot ||
            ItemStack.matches(mainHand, inventory.getItem(weapon))
        ) {
            held = inventory.selectedSlot
            body.selectHotbarSlot(inventory.selectedSlot)
            return
        }
        hold(weapon)
    }

    /**
     * Keeps the held slot selected; once a tick.
     */
    fun tick() {
        held?.let(body::selectHotbarSlot)
    }

    fun release() {
        if (held != null) body.releaseHotbarSlot()
        held = null
    }

    /**
     * Uses the item in the main hand, as a right click into the air does.
     */
    fun use(): Boolean = body.useItem(InteractionHand.MAIN_HAND)

    companion object {
        /**
         * Where items from outside the hotbar go to be held.
         */
        const val SCRATCH_SLOT = 8
    }
}
