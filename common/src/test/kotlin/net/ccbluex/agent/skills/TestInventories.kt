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

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.world.item.Item
import net.ccbluex.agent.world.item.Item as AgentItem
import net.minecraft.world.item.ItemStack as McItemStack

/**
 * Item stacks and inventories without a world or a player, for tests of what an agent holds.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
object TestInventories {

    init {
        MinecraftBootstrap.ensureInitialized()
        // Item stacks read their item's default components, which a server binds when it loads its data
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup()).forEach { it.apply() }
    }

    /**
     * Makes item stacks usable; the first use of this object does it, this only names it.
     */
    fun bootstrap() {}

    /**
     * An inventory holding [stacks] from slot 0 on, with the hotbar's first slot selected.
     */
    fun of(vararg stacks: ItemStack): TestInventory = TestInventory().apply {
        stacks.forEachIndexed { slot, stack -> setItem(slot, stack) }
    }

    fun stack(item: Item, count: Int = 1): ItemStack = McItemStack(item, count).toAgent()

    fun stack(item: AgentItem, count: Int = 1): ItemStack = stack(item.toMinecraft(), count)
}

/**
 * The game's player inventory layout: 36 slots, four armour slots, the offhand.
 *
 * @agent claude-opus-5-5
 */
class TestInventory : Inventory {
    private val items = Array(41) { ItemStack.EMPTY }

    override val containerSize: Int get() = items.size

    override fun getItem(slot: Int): ItemStack = items[slot]

    override val selectedSlot: Int get() = 0

    fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
    }
}
