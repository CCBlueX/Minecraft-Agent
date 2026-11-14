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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.skills.TestInventories
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weapons under 1.9 rules, as CubeCraft SkyWars loot hands them out: hit damage times attack speed.
 *
 * @agent claude-opus-5-5
 */
class WeaponsTest {

    @BeforeTest
    fun bootstrap() = TestInventories.bootstrap()

    @Test
    fun `damage per second follows the vanilla weapon stats`() {
        assertEquals(6.4, ItemStack(Items.WOODEN_SWORD).damagePerSecond, 1e-6)
        assertEquals(11.2, ItemStack(Items.DIAMOND_SWORD).damagePerSecond, 1e-6)
        assertEquals(9.0, ItemStack(Items.DIAMOND_AXE).damagePerSecond, 1e-6)
        assertEquals(4.0, ItemStack(Items.STICK).damagePerSecond, 1e-6)
    }

    @Test
    fun `a stone axe beats a wooden sword, an iron sword beats a diamond axe`() {
        assertTrue(ItemStack(Items.STONE_AXE).damagePerSecond > ItemStack(Items.WOODEN_SWORD).damagePerSecond)
        assertTrue(ItemStack(Items.IRON_SWORD).damagePerSecond > ItemStack(Items.DIAMOND_AXE).damagePerSecond)
    }
}
