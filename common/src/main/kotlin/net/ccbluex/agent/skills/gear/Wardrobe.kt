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

package net.ccbluex.agent.skills.gear

import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.HOTBAR_SLOTS
import net.ccbluex.agent.skills.INVENTORY_SLOTS
import net.ccbluex.agent.skills.SCRATCH_SLOT
import net.ccbluex.agent.skills.bestSlot
import net.ccbluex.agent.world.entity.Inventory

/**
 * The built-in [Gear]: every [CHECK_TICKS] it puts on the best piece the inventory has where it beats the worn one,
 * one swap per tick, and moves the best weapon into the hotbar. It waits while the hands or a container are busy.
 *
 * @agent claude-opus-5-5
 */
internal class Wardrobe : Gear {

    private var nextCheck = 0L
    private var dressing = false

    override fun tick(agent: Agent) {
        val body = agent.body
        if (!dressing && agent.ticks < nextCheck) return
        if (body.isUsingItem || body.containerMenu != null || agent.skills.eat?.wantsToEat == true) return
        nextCheck = agent.ticks + CHECK_TICKS

        val inventory = body.player.inventory
        dressing = wearBetterPiece(agent, inventory) || keepWeaponAtHand(agent, inventory)
    }

    /**
     * One swap towards a better piece; whether one was made.
     */
    private fun wearBetterPiece(agent: Agent, inventory: Inventory): Boolean {
        val player = agent.body.player
        for (slot in 0 until INVENTORY_SLOTS) {
            val stack = inventory.getItem(slot)
            val armorSlot = GearScore.armorSlot(stack) ?: continue
            if (GearScore.armorScore(stack) <= GearScore.armorScore(player.getItemBySlot(armorSlot))) continue
            val wornIndex = GearScore.wornIndex(armorSlot)
            if (slot < HOTBAR_SLOTS) {
                agent.body.swapWithHotbar(wornIndex, slot)
            } else {
                // Into the hotbar first; the next tick swaps it onto the body from there
                agent.body.swapWithHotbar(slot, spareHotbarSlot(inventory))
            }
            agent.logger.debug("Wearing {}", stack.item)
            return true
        }
        return false
    }

    private fun keepWeaponAtHand(agent: Agent, inventory: Inventory): Boolean {
        val best = inventory.bestSlot(GearScore::weaponScore)
        if (best < HOTBAR_SLOTS || best >= INVENTORY_SLOTS) return false
        agent.body.swapWithHotbar(best, spareHotbarSlot(inventory))
        return true
    }

    /**
     * An empty hotbar slot that is not selected, else the scratch slot, else the one next to it.
     */
    private fun spareHotbarSlot(inventory: Inventory): Int {
        val selected = inventory.selectedSlot
        return (0 until HOTBAR_SLOTS).firstOrNull { it != selected && inventory.getItem(it).isEmpty }
            ?: if (selected != SCRATCH_SLOT) SCRATCH_SLOT else SCRATCH_SLOT - 1
    }

    private companion object {
        const val CHECK_TICKS = 20
    }
}
