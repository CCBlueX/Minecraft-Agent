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

import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items

/**
 * The vanilla recipes a survival player makes on the way from the first log to iron gear, as clients do not get the
 * server's recipes. Items are looked up by their ids, so woods a version adds are known too.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public object GridRecipes {

    public val all: List<GridRecipe> by lazy(::build)

    /**
     * The recipes that make [item].
     */
    @JvmStatic
    public fun of(item: Item): List<GridRecipe> = all.filter { it.result == item }

    /**
     * Every plank, the wood ingredient of tools, tables and chests.
     */
    public val planks: Set<Item> by lazy { itemsEndingIn("_planks") }

    /**
     * Every log, wood and stem a plank is made of.
     */
    public val logs: Set<Item> by lazy { all.filter { it.result in planks }.flatMapTo(HashSet()) { it.cells.first().orEmpty() } }

    private fun build(): List<GridRecipe> {
        val recipes = ArrayList<GridRecipe>()
        for (plank in planks) {
            val wood = plank.id.removePrefix("minecraft:").removeSuffix("_planks")
            val sources = listOf("_log", "_wood", "_stem", "_hyphae").flatMap { listOf("$wood$it", "stripped_$wood$it") }
                .mapNotNullTo(HashSet(), ::item)
            if (sources.isNotEmpty()) recipes += GridRecipe(plank, 4, 1, listOf(sources))
        }
        item("bamboo_block")?.let { block ->
            recipes += GridRecipe(Items.BAMBOO_PLANKS, 2, 1, listOf(setOfNotNull(block, item("stripped_bamboo_block"))))
        }
        val stick = setOf(Items.STICK)
        val stone = setOf(Items.COBBLESTONE, Items.BLACKSTONE, Items.COBBLED_DEEPSLATE)
        val iron = setOf(Items.IRON_INGOT)
        recipes += shaped(Items.STICK, 4, listOf("#", "#"), '#' to planks)
        recipes += shaped(Items.CRAFTING_TABLE, 1, listOf("##", "##"), '#' to planks)
        recipes += shaped(Items.CHEST, 1, listOf("###", "# #", "###"), '#' to planks)
        recipes += shaped(Items.FURNACE, 1, listOf("###", "# #", "###"), '#' to stone)
        recipes += shaped(Items.TORCH, 4, listOf("X", "#"), 'X' to setOf(Items.COAL, Items.CHARCOAL), '#' to stick)
        recipes += shaped(Items.BUCKET, 1, listOf("# #", " # "), '#' to iron)
        recipes += shaped(Items.SHIELD, 1, listOf("W#W", "WWW", " W "), 'W' to planks, '#' to iron)
        recipes += shaped(Items.FLINT_AND_STEEL, 1, listOf("I", "F"), 'I' to iron, 'F' to setOf(Items.FLINT))
        recipes += shaped(Items.BREAD, 1, listOf("###"), '#' to setOf(Items.WHEAT))
        recipes += tools(planks, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD)
        recipes += tools(stone, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_SWORD)
        recipes += tools(iron, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_SWORD)
        recipes += tools(setOf(Items.DIAMOND), Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD)
        recipes += shaped(Items.IRON_HELMET, 1, listOf("XXX", "X X"), 'X' to iron)
        recipes += shaped(Items.IRON_CHESTPLATE, 1, listOf("X X", "XXX", "XXX"), 'X' to iron)
        recipes += shaped(Items.IRON_LEGGINGS, 1, listOf("XXX", "X X", "X X"), 'X' to iron)
        recipes += shaped(Items.IRON_BOOTS, 1, listOf("X X", "X X"), 'X' to iron)
        for (color in DyeColor.entries) {
            val wool = item("${color.name.lowercase()}_wool") ?: continue
            val bed = item("${color.name.lowercase()}_bed") ?: continue
            recipes += shaped(bed, 1, listOf("###", "XXX"), '#' to setOf(wool), 'X' to planks)
        }
        return recipes
    }

    private fun tools(material: Set<Item>, pickaxe: Item, axe: Item, shovel: Item, sword: Item): List<GridRecipe> {
        val stick = setOf(Items.STICK)
        return listOf(
            shaped(pickaxe, 1, listOf("XXX", " # ", " # "), 'X' to material, '#' to stick),
            shaped(axe, 1, listOf("XX", "X#", " #"), 'X' to material, '#' to stick),
            shaped(shovel, 1, listOf("X", "#", "#"), 'X' to material, '#' to stick),
            shaped(sword, 1, listOf("X", "X", "#"), 'X' to material, '#' to stick),
        )
    }

    private fun shaped(result: Item, count: Int, rows: List<String>, vararg key: Pair<Char, Set<Item>>): GridRecipe {
        val keys = key.toMap()
        val width = rows.maxOf { it.length }
        val cells = rows.flatMap { row -> (0 until width).map { keys[row.getOrElse(it) { ' ' }] } }
        return GridRecipe(result, count, width, cells)
    }

    private fun item(id: String): Item? = Item.of(id).takeIf { it.isDefined }

    private fun itemsEndingIn(suffix: String): Set<Item> =
        Item.all().filterTo(HashSet()) { it.id.startsWith("minecraft:") && it.id.endsWith(suffix) }
}
