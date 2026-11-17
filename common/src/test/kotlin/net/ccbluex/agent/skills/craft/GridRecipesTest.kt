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

import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recipes a survival player starts with, as the game has them: every wood's logs give its own planks, a bed
 * takes wool of one colour, tools lie on the grid the game expects.
 *
 * @agent claude-opus-5-5
 */
class GridRecipesTest {

    init {
        TestInventories.bootstrap()
    }

    @Test
    fun `every plank comes from the logs of its own wood`() {
        for (plank in GridRecipes.planks - Items.BAMBOO_PLANKS) {
            val recipe = GridRecipes.of(plank).single()
            val logs = recipe.cells.single().orEmpty()
            assertTrue(logs.isNotEmpty() && logs.all { it in GridRecipes.logs }, "$plank from $logs")
            assertEquals(4, recipe.count)
        }
        assertEquals(Item.of("spruce_planks"), GridRecipes.all.single { Item.of("stripped_spruce_log") in it.cells.first().orEmpty() }.result)
    }

    @Test
    fun `a bed takes three wool of its colour and any planks`() {
        val red = Items.WOOL[DyeColor.RED.ordinal]
        val recipe = GridRecipes.of(Item.of("red_bed")).single()
        assertEquals(setOf(red), recipe.cells.first())
        assertTrue(recipe.needsTable)
        val mixed = listOf(
            TestInventories.stack(red, 2), TestInventories.stack(Items.WOOL[DyeColor.WHITE.ordinal]),
            TestInventories.stack(Item.of("oak_planks"), 3),
        )
        assertEquals(0, recipe.timesFrom(mixed))
        assertEquals(1, recipe.timesFrom(mixed + TestInventories.stack(red)))
    }

    @Test
    fun `a pickaxe has its head in the top row and fits only a table`() {
        val recipe = GridRecipes.of(Items.STONE_PICKAXE).single()
        assertEquals(3, recipe.width)
        assertTrue(recipe.needsTable)
        assertEquals(listOf(Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE), recipe.cells.take(3).map { cell -> Items.COBBLESTONE.takeIf { it in cell.orEmpty() } })
        assertEquals(setOf(Items.STICK), recipe.cells[4])
        assertEquals(mapOf(GridRecipes.planks to 4), GridRecipes.of(Items.CRAFTING_TABLE).single().ingredients)
    }
}
