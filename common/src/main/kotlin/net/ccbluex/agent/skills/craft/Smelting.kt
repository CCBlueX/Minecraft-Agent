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

import net.ccbluex.agent.world.FurnaceMenu
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.block.Blocks
import kotlin.math.ceil

/**
 * Smelts up to [count] of [input] in a furnace within 16 blocks, or one it places from the inventory, burning the
 * first of [fuels] it carries, and waits by it until done; how many results it took out, with any already waiting
 * there.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.smelt(input: Set<Item>, count: Int, fuels: List<Item>): Int {
    if (!openStation(Blocks.FURNACE, Items.FURNACE) { it is FurnaceMenu }) return 0
    val menu = body.containerMenu as? FurnaceMenu ?: return 0
    try {
        var taken = takeResult(menu)
        if (menu.slots[INPUT].item.isEmpty) moveInto(menu, input, INPUT, count)
        val smelting = menu.slots[INPUT].item.count
        if (smelting == 0) return taken
        val fuel = fuels.firstOrNull { countOf(setOf(it)) > 0 }
        val burning = menu.slots[FUEL].item.let { if (it.isEmpty) 0 else it.count * burnTicks(it.item) }
        if (fuel != null) {
            val needed = ceil((smelting * SMELT_TICKS - burning).toDouble() / burnTicks(fuel)).toInt()
            if (needed > 0) moveInto(menu, setOf(fuel), FUEL, needed)
        }
        // Some time over what the furnace takes, for a fuel that ran out between two items
        tickUntil { ticks ->
            takeMovement()
            menu.slots[INPUT].item.isEmpty || ticks >= smelting * SMELT_TICKS + 100 || !body.player.isAlive ||
                body.containerMenu !== menu
        }
        waitTicks(2)
        taken += takeResult(menu)
        return taken
    } finally {
        body.closeContainer()
    }
}

/**
 * Moves up to [amount] of [items] from the inventory into [target] of [menu]; how many went in.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.moveInto(menu: ContainerMenu, items: Set<Item>, target: Int, amount: Int): Int {
    var left = amount
    while (left > 0) {
        val source = carried(menu).firstOrNull { it.item.item in items } ?: break
        body.click(menu, source.index, 0, ContainerInput.PICKUP)
        if (menu.carried.count <= left) {
            left -= menu.carried.count
            body.click(menu, target, 0, ContainerInput.PICKUP)
        } else {
            repeat(left) { body.click(menu, target, 1, ContainerInput.PICKUP) }
            left = 0
        }
        putBack(menu, source)
        // A target that took nothing, as with a different item in it, would loop forever
        if (!menu.carried.isEmpty) break
    }
    return amount - left
}

private fun Agent.takeResult(menu: FurnaceMenu): Int {
    val result = menu.slots[RESULT].item
    if (result.isEmpty) return 0
    val count = result.count
    body.click(menu, RESULT, 0, ContainerInput.QUICK_MOVE)
    return count
}

/**
 * How long one of [fuel] burns, in ticks; the game's numbers for the common fuels.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun burnTicks(fuel: Item): Int {
    val id = fuel.id
    return when {
        fuel == Items.COAL || fuel == Items.CHARCOAL -> 1600
        fuel == Items.COAL_BLOCK -> 16000
        fuel == Items.LAVA_BUCKET -> 20000
        fuel == Items.BLAZE_ROD -> 2400
        fuel == Items.STICK -> 100
        id.endsWith("_planks") || id.endsWith("_log") || id.endsWith("_wood") -> 300
        else -> 200
    }
}

private const val INPUT = 0
private const val FUEL = 1
private const val RESULT = 2

/**
 * A furnace smelts one item in this many ticks.
 */
internal const val SMELT_TICKS = 200
