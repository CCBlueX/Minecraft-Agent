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

package net.ccbluex.agent.v26_3

import com.google.common.collect.MapMaker
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.world.CraftingMenu
import net.ccbluex.agent.world.FurnaceMenu
import net.ccbluex.agent.world.Slot
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.AbstractCraftingMenu
import net.minecraft.world.inventory.AbstractFurnaceMenu
import java.util.concurrent.ConcurrentMap
import net.minecraft.world.inventory.Slot as McSlot

/**
 * The slots of an open container as the agents see them, live.
 *
 * @agent claude-opus-5-5
 */
private open class MenuView(private val handle: AbstractContainerMenu) : ContainerMenu {
    override val slots: List<Slot> = handle.slots.map(::SlotView)
    override val carried: ItemStack get() = handle.carried.toAgent()
}

private class SlotView(private val slot: McSlot) : Slot {
    override val index: Int get() = slot.index
    override val item: ItemStack get() = slot.item.toAgent()
    override val isPlayerInventory: Boolean get() = slot.container is Inventory
    override val containerSlot: Int get() = slot.containerSlot
}

private class CraftingView(private val menu: AbstractCraftingMenu) : MenuView(menu), CraftingMenu {
    override val resultSlot: Slot get() = slots[menu.resultSlot.index]
    override val inputGridSlots: List<Slot> get() = menu.inputGridSlots.map { slots[it.index] }
    override val gridWidth: Int get() = menu.gridWidth
    override val gridHeight: Int get() = menu.gridHeight
}

private class FurnaceView(menu: AbstractFurnaceMenu) : MenuView(menu), FurnaceMenu

private val menus: ConcurrentMap<AbstractContainerMenu, ContainerMenu> = MapMaker().weakKeys().makeMap()

/**
 * The library's view of [this]; the same instance every time, so menus compare with `===`.
 *
 * @agent claude-opus-5-5
 */
public fun AbstractContainerMenu.toAgent(): ContainerMenu = menus.computeIfAbsent(this) { menu ->
    when (menu) {
        is AbstractCraftingMenu -> CraftingView(menu)
        is AbstractFurnaceMenu -> FurnaceView(menu)
        else -> MenuView(menu)
    }
}
