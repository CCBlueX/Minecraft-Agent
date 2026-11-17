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

import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.Inventory

/**
 * Eats on the way, as [Options.canEat] lets it: once hungry the plain food that fills the most, then on until fed.
 * Only on the ground or riding, and never while the walk places or digs blocks, which need the hand: a meal started
 * before lets go of the food for them.
 *
 * @agent claude-opus-5-5
 */
internal class Snacking(private val context: NavigationContext) {

    /**
     * The hotbar slot of the food being eaten, -1 while not eating.
     */
    private var hotbar = -1
    private var feeding = false

    fun tick(isBuilding: Boolean) {
        val body = context.body
        val player = body.player
        if (hotbar >= 0) {
            if (player.isUsingItem && !isBuilding) {
                body.selectHotbarSlot(hotbar)
                return
            }
            stop()
        }
        val hunger = player.foodData.foodLevel
        if (hunger >= FED) feeding = false
        if (!context.options().canEat || hunger > HUNGRY && !feeding) return
        if (isBuilding || player.isUsingItem || player.isInWater || !player.onGround() && !player.isPassenger) return
        val inventory = player.inventory
        val slot = inventory.bestFoodSlot()
        if (slot < 0) return
        if (slot == Inventory.SLOT_OFFHAND) {
            if (body.useItem(InteractionHand.OFF_HAND)) feeding = true
            return
        }
        val held = if (Inventory.isHotbarSlot(slot)) {
            slot
        } else {
            inventory.suitableHotbarSlot.also { body.swapWithHotbar(slot, it) }
        }
        body.selectHotbarSlot(held)
        if (body.useItem(InteractionHand.MAIN_HAND)) {
            hotbar = held
            feeding = true
        }
    }

    /**
     * Lets go of the food's hotbar slot, which ends a meal not finished yet.
     */
    fun stop() {
        if (hotbar < 0) return
        hotbar = -1
        context.body.releaseHotbarSlot()
    }
}
