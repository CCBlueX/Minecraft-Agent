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

package net.ccbluex.agent.skills.loot

import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.TestInventories.stack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a chest is looted for, up to the caps.
 *
 * @agent claude-opus-5-5
 */
class WantedTest {

    private val wanted = Wanted.DEFAULT

    @Test
    fun blocksUpToTheCap() {
        val few = TestInventories.of(stack(Items.OAK_PLANKS, 63))
        assertTrue(wanted.wants(few, stack(Items.STONE, 16)))
        val enough = TestInventories.of(stack(Items.OAK_PLANKS, 64))
        assertFalse(wanted.wants(enough, stack(Items.STONE, 16)))
        assertFalse(wanted.wants(few, stack(Items.CHEST)))
    }

    @Test
    fun arrowsAndThrowablesUpToTheirCaps() {
        val inventory = TestInventories.of(stack(Items.ARROW, 31), stack(Items.SNOWBALL, 16), stack(Items.EGG, 16))
        assertTrue(wanted.wants(inventory, stack(Items.ARROW, 8)))
        assertFalse(wanted.wants(inventory, stack(Items.SNOWBALL, 4)))
        assertTrue(Wanted(throwables = 33).wants(inventory, stack(Items.EGG)))
    }

    @Test
    fun oneOfEachToolAndFoodOnlyWithoutFood() {
        val inventory = TestInventories.of(stack(Items.WOODEN_PICKAXE), stack(Items.BOW))
        assertFalse(wanted.wants(inventory, stack(Items.DIAMOND_PICKAXE)))
        assertTrue(wanted.wants(inventory, stack(Items.IRON_SHOVEL)))
        assertFalse(wanted.wants(inventory, stack(Items.BOW)))
        assertTrue(wanted.wants(inventory, stack(Items.FISHING_ROD)))
        assertTrue(wanted.wants(inventory, stack(Items.BREAD)))
        inventory.setItem(5, stack(Items.COOKED_BEEF, 3))
        assertFalse(wanted.wants(inventory, stack(Items.BREAD)))
    }

    @Test
    fun alwaysGoldenApplesAndPearls() {
        val inventory = TestInventories.of(stack(Items.GOLDEN_APPLE, 10), stack(Items.ENDER_PEARL, 10))
        assertTrue(wanted.wants(inventory, stack(Items.GOLDEN_APPLE)))
        assertTrue(wanted.wants(inventory, stack(Items.ENDER_PEARL)))
        assertFalse(wanted.wants(inventory, stack(Items.DIRT, 0)))
    }

    @Test
    fun gearOnlyWhenBetter() {
        val inventory = TestInventories.of(stack(Items.IRON_SWORD), stack(Items.IRON_BOOTS))
        assertFalse(wanted.wants(inventory, stack(Items.STONE_SWORD)))
        assertTrue(wanted.wants(inventory, stack(Items.DIAMOND_SWORD)))
        assertFalse(wanted.wants(inventory, stack(Items.CHAINMAIL_BOOTS)))
        assertTrue(wanted.wants(inventory, stack(Items.GOLDEN_HELMET)))
    }
}
