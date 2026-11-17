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

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.gather.Prey
import net.ccbluex.agent.skills.gather.Resource
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The next step to carry [count] of [items], counted together, from the items [carried] and whether a crafting table
 * and a furnace stand near; null when it carries them already. The recipes of [GridRecipes] break it down: an iron
 * pickaxe needs ingots, ingots need raw iron, a furnace and fuel, raw iron a stone pickaxe, and so on down to logs.
 * Asks nothing of the world, so a plan made after a restart goes on from what the inventory holds.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun nextStep(items: Set<Item>, count: Int, carried: Map<Item, Int>, tableNear: Boolean, furnaceNear: Boolean): Step? =
    Obtaining(carried, tableNear, furnaceNear).obtain(items, count, 0)

/**
 * One plan over a snapshot of the inventory.
 *
 * @agent claude-opus-5-5
 */
private class Obtaining(private val carried: Map<Item, Int>, private val tableNear: Boolean, private val furnaceNear: Boolean) {

    private val pickaxeTier = carried.filterValues { it > 0 }.keys.maxOfOrNull(Resource::pickaxeTier) ?: 0

    fun obtain(items: Set<Item>, count: Int, depth: Int): Step? {
        val have = count(items)
        if (have >= count) return null
        // Deeper than any recipe chain goes, so a recipe loop
        if (depth > 8) return Step.Stuck("no way to get ${names(items)}")
        val missing = count - have
        Resource.entries.firstOrNull { resource -> items.any { it in resource.drops } }?.let { resource ->
            return pickaxe(resource.tier, depth) ?: Step.Gather(resource, max(missing, resource.batch))
        }
        return when {
            Items.IRON_INGOT in items -> smelt(setOf(Items.RAW_IRON), missing, depth)
            items.any { it in Items.WOOL } -> Step.Hunt(Prey.WOOL)
            else -> craft(items, missing, depth)
        }
    }

    private fun count(item: Item): Int = carried[item] ?: 0

    private fun count(items: Set<Item>): Int = items.sumOf(::count)

    private fun pickaxe(tier: Int, depth: Int): Step? = if (pickaxeTier >= tier) {
        null
    } else {
        val pickaxe = listOf(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE)[tier - 1]
        obtain(setOf(pickaxe), 1, depth + 1)
    }

    private fun smelt(input: Set<Item>, count: Int, depth: Int): Step {
        obtain(input, count, depth + 1)?.let { return it }
        if (!furnaceNear) obtain(setOf(Items.FURNACE), 1, depth + 1)?.let { return it }
        // All of it in one go, as the furnace is there now
        val amount = max(count, count(input))
        val fuels = fuelsFor(amount)
            ?: return obtain(GridRecipes.logs, ceil(amount * SMELT_TICKS.toDouble() / burnTicks(Items.OAK_LOG)).toInt(), depth + 1)
                ?: Step.Stuck("no fuel")
        return Step.Smelt(input, amount, fuels)
    }

    /**
     * The carried fuels that smelt [count] items, best first; null when they do not last.
     */
    private fun fuelsFor(count: Int): List<Item>? {
        val fuels = carried.keys
            .filter { (it == Items.COAL || it == Items.CHARCOAL || it in GridRecipes.logs || it in GridRecipes.planks) && count(it) > 0 }
            .sortedByDescending(::burnTicks)
        return fuels.takeIf { fuels.sumOf { burnTicks(it) * count(it) } >= count * SMELT_TICKS }
    }

    private fun craft(items: Set<Item>, missing: Int, depth: Int): Step {
        fun progress(recipe: GridRecipe) =
            recipe.ingredients.entries.sumOf { (set, per) -> min(count(set), per) } / recipe.cells.count { it != null }.toDouble()
        // One it can craft now, the cheapest; else the one it has most of, such as the planks of the logs it carries
        val recipe = items.flatMap(GridRecipes::of).sortedWith(
            compareByDescending<GridRecipe> { progress(it) >= 1.0 }.thenByDescending(::progress).thenBy { it.cells.count { cell -> cell != null } },
        ).firstOrNull() ?: return Step.Stuck("no way to get ${names(items)}")
        val times = (missing + recipe.count - 1) / recipe.count
        for ((set, perCraft) in recipe.ingredients) {
            obtain(set, perCraft * times, depth + 1)?.let { return it }
        }
        if (recipe.needsTable && !tableNear) obtain(setOf(Items.CRAFTING_TABLE), 1, depth + 1)?.let { return it }
        return Step.Craft(recipe, times)
    }

    private fun names(items: Set<Item>) = items.take(3).joinToString("/") { it.id.removePrefix("minecraft:") }
}
