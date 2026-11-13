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

import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.TestInventories
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Duel kits played by the inventory-driven fight: every kit's skills come out of the catalogue under its rules, and
 * every kit's items are told apart the way its skills need.
 *
 * @agent claude-opus-5-5
 */
class KitsTest {

    @BeforeTest
    fun bootstrap() = TestInventories.bootstrap()

    @Test
    fun `the catalogue holds every kit's skills under its rules`() {
        for (kit in Kits.entries) {
            val kinds = skillsFor(kit.rules).map { it.kind }.toSet()
            assertTrue(kinds.containsAll(kit.skills), "$kit misses ${kit.skills - kinds}")
        }
    }

    @Test
    fun `the catalogue leaves out what the rules forbid`() {
        assertTrue(skillsFor(Kits.SUMO.rules).isEmpty())
        val noPearls = Rules(skills = Rules.DEFAULT.skills - Skill.PEARL)
        assertFalse(skillsFor(noPearls).any { it.kind == Skill.PEARL })
    }

    @Test
    fun `the catalogue runs in skill priority order`() {
        val priorities = SkillRunner(skillsFor(Rules.DEFAULT)).skills.associate { it.kind to it.priority }
        assertEquals(100, priorities[Skill.TOTEM])
        assertEquals(90, priorities[Skill.WATER_CLUTCH])
        assertEquals(80, priorities[Skill.SHIELD_BREAK])
        assertEquals(75, priorities[Skill.POT_HEAL])
        assertEquals(70, priorities[Skill.GAPPLE])
        assertEquals(40, priorities[Skill.GUARD])
        assertEquals(20, priorities[Skill.BOW])
    }

    @Test
    fun `kit weights mix the skill use towards blasts`() {
        val crystal = SkillLevel.NORMAL.use(Kits.CRYSTAL.rules.blastWeight)
        assertEquals(SkillLevel.NORMAL.blast, crystal)
        val sword = SkillLevel.NORMAL.use(Kits.SWORD.rules.blastWeight)
        assertEquals(SkillLevel.NORMAL.melee, sword)
        val mace = SkillLevel.NORMAL.use(Kits.MACE.rules.blastWeight)
        assertEquals(0.65 + (0.45 - 0.65) * 0.8, mace.skill, 1e-9)
    }

    @Test
    fun `weapons rank swords and spears over axes and maces over shovels`() {
        assertEquals(3, ItemStack(Items.DIAMOND_SWORD).weaponRank)
        assertEquals(3, ItemStack(Items.NETHERITE_SPEAR).weaponRank)
        assertEquals(2, ItemStack(Items.DIAMOND_AXE).weaponRank)
        assertEquals(2, ItemStack(Items.MACE).weaponRank)
        assertEquals(1, ItemStack(Items.DIAMOND_SHOVEL).weaponRank)
        assertEquals(0, ItemStack(Items.DIAMOND_PICKAXE).weaponRank)
    }

    @Test
    fun `kit items are told apart for their skills`() {
        val pot = Kits.POT.items()
        assertEquals(16, pot.filter { it.splashes(MobEffects.INSTANT_HEALTH) }.sumOf { it.count })
        assertTrue(pot.any { it.splashes(MobEffects.STRENGTH) })
        assertTrue(pot.any { it.splashes(MobEffects.SPEED) })
        assertFalse(pot.any { it.isGoldenApple })

        val smp = Kits.DIAMOND_SMP.items()
        assertTrue(smp.single { it.isBuildingBlock }.`is`(Items.OAK_PLANKS))
        assertTrue(smp.any { it.isGoldenApple })

        assertFalse(ItemStack(Items.OBSIDIAN).isBuildingBlock, "obsidian is kept for crystals")
        assertFalse(ItemStack(Items.SAND).isBuildingBlock, "sand falls")
        assertFalse(ItemStack(Items.COBWEB).isBuildingBlock)
        assertTrue(ItemStack(Items.WOOL.pick(DyeColor.WHITE)).isBuildingBlock)
        assertTrue(ItemStack(Items.ENCHANTED_GOLDEN_APPLE).isGoldenApple)
    }

    @Test
    fun `throwables are the minigame ones`() {
        assertTrue(Ranged.throwable(ItemStack(Items.SNOWBALL)))
        assertTrue(Ranged.throwable(ItemStack(Items.BROWN_EGG)))
        assertTrue(Ranged.throwable(ItemStack(Items.WIND_CHARGE)))
        assertFalse(Ranged.throwable(ItemStack(Items.ENDER_PEARL)))
    }
}
