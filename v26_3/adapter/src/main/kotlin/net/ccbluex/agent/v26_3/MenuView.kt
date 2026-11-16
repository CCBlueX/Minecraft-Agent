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

import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.world.Slot
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu

/**
 * The slots of an open container as the agents see them, live.
 *
 * @agent claude-opus-5-5
 */
private class MenuView(private val handle: AbstractContainerMenu) : ContainerMenu {
    override val slots: List<Slot> = handle.slots.map { slot ->
        object : Slot {
            override val index: Int get() = slot.index
            override val item: ItemStack get() = slot.item.toAgent()
            override val isPlayerInventory: Boolean get() = slot.container is Inventory
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
public fun AbstractContainerMenu.toAgent(): ContainerMenu = MenuView(this)
