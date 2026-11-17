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
import net.ccbluex.agent.skills.gather.Prey
import net.ccbluex.agent.skills.gather.Resource
import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The next step towards items, planned from the inventory alone, as after a restart at any point of the way.
 *
 * @agent claude-opus-5-5
 */
class ObtainingTest {

    init {
        TestInventories.bootstrap()
    }

    private fun next(item: Item, vararg carried: Pair<Item, Int>, tableNear: Boolean = false, furnaceNear: Boolean = false) =
        nextStep(setOf(item), 1, carried.toMap(), tableNear, furnaceNear)

    private fun crafts(step: Step?): Item = assertIs<Step.Craft>(step).recipe.result

    @Test
    fun `nothing to do for what it carries`() {
        assertNull(next(Items.CRAFTING_TABLE, Items.CRAFTING_TABLE to 1))
    }

    @Test
    fun `a crafting table starts from logs, which become the planks of their wood`() {
        assertEquals(Step.Gather(Resource.LOGS, 6), next(Items.CRAFTING_TABLE))
        assertEquals(Item.of("birch_planks"), crafts(next(Items.CRAFTING_TABLE, Item.of("birch_log") to 3)))
        assertEquals(Items.CRAFTING_TABLE, crafts(next(Items.CRAFTING_TABLE, Item.of("oak_planks") to 4)))
    }

    @Test
    fun `a wooden pickaxe needs sticks first, then the table it carries`() {
        assertEquals(Items.STICK, crafts(next(Items.WOODEN_PICKAXE, Items.CRAFTING_TABLE to 1, Item.of("oak_planks") to 5)))
        assertEquals(
            Items.WOODEN_PICKAXE,
            crafts(next(Items.WOODEN_PICKAXE, Items.CRAFTING_TABLE to 1, Item.of("oak_planks") to 3, Items.STICK to 2)),
        )
    }

    @Test
    fun `stone needs a pickaxe`() {
        assertEquals(Step.Gather(Resource.STONE, 16), next(Items.COBBLESTONE, Items.WOODEN_PICKAXE to 1))
        assertEquals(Items.STICK, crafts(next(Items.COBBLESTONE, Items.CRAFTING_TABLE to 1, Item.of("oak_planks") to 5)))
    }

    @Test
    fun `a wooden pickaxe cannot mine iron, so the stone pickaxe comes first`() {
        val carried = arrayOf(Items.WOODEN_PICKAXE to 1, Items.COBBLESTONE to 3, Items.STICK to 2, Items.CRAFTING_TABLE to 1)
        assertEquals(Items.STONE_PICKAXE, crafts(next(Items.IRON_INGOT, *carried)))
        assertEquals(Step.Gather(Resource.IRON, 3), next(Items.IRON_INGOT, Items.STONE_PICKAXE to 1))
    }

    @Test
    fun `an ingot needs raw iron, a furnace and fuel, in that order`() {
        val pickaxe = Items.STONE_PICKAXE to 1
        assertEquals(Items.FURNACE, crafts(next(Items.IRON_INGOT, pickaxe, Items.RAW_IRON to 3, Items.COBBLESTONE to 9, Items.CRAFTING_TABLE to 1)))
        assertEquals(Step.Gather(Resource.LOGS, 6), next(Items.IRON_INGOT, pickaxe, Items.RAW_IRON to 3, furnaceNear = true))
        val smelt = assertIs<Step.Smelt>(next(Items.IRON_INGOT, pickaxe, Items.RAW_IRON to 3, Items.COAL to 1, Items.OAK_LOG to 1, furnaceNear = true))
        assertEquals(3, smelt.count)
        assertEquals(listOf(Items.COAL, Items.OAK_LOG), smelt.fuels)
    }

    @Test
    fun `a bed needs wool, which sheep give`() {
        assertEquals(Step.Hunt(Prey.WOOL), next(Item.of("white_bed"), Item.of("oak_planks") to 3, Items.CRAFTING_TABLE to 1))
    }

    @Test
    fun `an item no recipe makes is out of reach`() {
        assertIs<Step.Stuck>(next(Items.ELYTRA))
    }
}
