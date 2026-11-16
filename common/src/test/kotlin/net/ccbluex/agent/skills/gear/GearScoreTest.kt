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

package net.ccbluex.agent.skills.gear

import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.TestInventories.stack
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gear scores by tier tables, combined and scaled by health.
 *
 * @agent claude-opus-5-5
 */
class GearScoreTest {

    private val inventory = TestInventories.of()

    @Test
    fun weaponsScoreByTier() {
        assertEquals(4.0, GearScore.weaponScore(stack(Items.WOODEN_SWORD)))
        assertEquals(5.0, GearScore.weaponScore(stack(Items.STONE_SWORD)))
        assertEquals(6.0, GearScore.weaponScore(stack(Items.IRON_SWORD)))
        assertEquals(8.0, GearScore.weaponScore(stack(Items.NETHERITE_SWORD)))
        assertEquals(6.5, GearScore.weaponScore(stack(Items.DIAMOND_AXE)))
        assertEquals(0.0, GearScore.weaponScore(stack(Items.DIAMOND_PICKAXE)))
    }

    @Test
    fun armorScoresBySlotAndTier() {
        assertEquals(1.0, GearScore.armorScore(stack(Items.LEATHER_HELMET)))
        assertEquals(3.0, GearScore.armorScore(stack(Items.LEATHER_CHESTPLATE)))
        assertEquals(2.0, GearScore.armorScore(stack(Items.CHAINMAIL_HELMET)))
        assertEquals(6.0, GearScore.armorScore(stack(Items.IRON_CHESTPLATE)))
        assertEquals(6.0, GearScore.armorScore(stack(Items.DIAMOND_LEGGINGS)))
        assertEquals(3.5, GearScore.armorScore(stack(Items.NETHERITE_BOOTS)))
        assertEquals(EquipmentSlot.FEET, GearScore.armorSlot(stack(Items.IRON_BOOTS)))
        assertNull(GearScore.armorSlot(stack(Items.ELYTRA)))
        assertNull(GearScore.armorSlot(stack(Items.CARVED_PUMPKIN)))
    }

    @Test
    fun fullIronIsGood() {
        val armor = listOf(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)
            .sumOf { GearScore.armorScore(stack(it)) }
        val gear = GearScore(armor, GearScore.weaponScore(stack(Items.IRON_SWORD)), 1.0)
        assertEquals(GearScore.GOOD, gear.equipment)
        assertEquals(0.0, gear.weakness)
        assertEquals(1.0, GearScore(0.0, 0.0, 1.0).weakness)
    }

    @Test
    fun missingHealthHalvesTheScoreAtMost() {
        assertEquals(12.0, GearScore(12.0, 0.0, 1.0).score)
        assertEquals(6.0, GearScore(12.0, 0.0, 0.0).score)
        assertEquals(9.0, GearScore(12.0, 0.0, 0.5).score)
    }

    @Test
    fun upgradesBeatWornAndCarriedGear() {
        inventory.setItem(0, stack(Items.STONE_SWORD))
        inventory.setItem(1, stack(Items.IRON_CHESTPLATE))
        inventory.setItem(GearScore.wornIndex(EquipmentSlot.HEAD), stack(Items.DIAMOND_HELMET))

        assertTrue(GearScore.isUpgrade(inventory, stack(Items.IRON_SWORD)))
        assertFalse(GearScore.isUpgrade(inventory, stack(Items.WOODEN_SWORD)))
        assertFalse(GearScore.isUpgrade(inventory, stack(Items.CHAINMAIL_CHESTPLATE)))
        assertTrue(GearScore.isUpgrade(inventory, stack(Items.DIAMOND_CHESTPLATE)))
        assertFalse(GearScore.isUpgrade(inventory, stack(Items.IRON_HELMET)))
        assertTrue(GearScore.isUpgrade(inventory, stack(Items.LEATHER_BOOTS)))
    }
}
