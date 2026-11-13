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

import net.minecraft.core.Holder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import java.util.EnumSet

/**
 * Duel kits as test fixtures: the items of each loadout (enchantments left out, they need a world's registries), the
 * skills the kit is played with, and the rules a host would pass for its mode. Kits live only in tests:
 * the built-in fight picks skills from the inventory.
 *
 * @agent claude-opus-5-5
 */
internal enum class Kits(
    val items: () -> List<ItemStack>,
    val skills: Set<Skill>,
    val rules: Rules = Rules(),
) {
    FIGHTER({ listOf(stack(Items.DIAMOND_SWORD)) }, emptySet()),
    SWORD({ listOf(stack(Items.DIAMOND_SWORD), stack(Items.GOLDEN_APPLE, 3)) }, setOf(Skill.GAPPLE)),
    AXE(
        {
            listOf(
                stack(Items.IRON_SWORD), stack(Items.DIAMOND_AXE), stack(Items.GOLDEN_APPLE, 3), stack(Items.SHIELD),
            )
        },
        setOf(Skill.SHIELD_BREAK, Skill.GAPPLE, Skill.GUARD),
        Rules(blastWeight = 0.2),
    ),
    MACE(
        {
            listOf(
                stack(Items.NETHERITE_SWORD), stack(Items.MACE), stack(Items.WIND_CHARGE, 64),
                stack(Items.GOLDEN_APPLE, 16), stack(Items.ENDER_PEARL, 16), stack(Items.NETHERITE_AXE),
                stack(Items.SHIELD), stack(Items.TOTEM_OF_UNDYING, 2),
            )
        },
        setOf(
            Skill.TOTEM, Skill.SHIELD_BREAK, Skill.GAPPLE, Skill.MACE_SMASH,
            Skill.GUARD, Skill.PEARL,
        ),
        Rules(blastWeight = 0.8),
    ),
    SPEAR(
        { listOf(stack(Items.DIAMOND_SPEAR), stack(Items.GOLDEN_APPLE, 3)) },
        setOf(Skill.GAPPLE, Skill.SPEAR_CHARGE),
        Rules(crits = false, blastWeight = 0.2),
    ),
    CRYSTAL(
        {
            listOf(
                stack(Items.NETHERITE_SWORD), stack(Items.END_CRYSTAL, 64), stack(Items.OBSIDIAN, 64),
                stack(Items.RESPAWN_ANCHOR, 64), stack(Items.GLOWSTONE, 64), stack(Items.GOLDEN_APPLE, 64),
                stack(Items.ENDER_PEARL, 16), stack(Items.TOTEM_OF_UNDYING, 6),
            )
        },
        setOf(Skill.TOTEM, Skill.GAPPLE, Skill.ANCHOR, Skill.CRYSTAL, Skill.PEARL),
        Rules(blastWeight = 1.0),
    ),
    DIAMOND_SMP(
        {
            listOf(
                stack(Items.DIAMOND_SWORD), stack(Items.DIAMOND_AXE), stack(Items.COBWEB, 16),
                stack(Items.OAK_PLANKS, 64), stack(Items.GOLDEN_APPLE, 6), stack(Items.WATER_BUCKET),
                stack(Items.SHIELD),
            )
        },
        setOf(
            Skill.WATER_CLUTCH, Skill.SHIELD_BREAK, Skill.PILLAR, Skill.GAPPLE,
            Skill.BLOCK_PEARL, Skill.GUARD, Skill.COBWEB,
        ),
        Rules(blastWeight = 0.4),
    ),
    UHC(
        {
            listOf(
                stack(Items.DIAMOND_SWORD), stack(Items.DIAMOND_AXE), stack(Items.BOW), stack(Items.ARROW, 16),
                stack(Items.COBWEB, 16), stack(Items.WATER_BUCKET), stack(Items.WATER_BUCKET),
                stack(Items.LAVA_BUCKET), stack(Items.LAVA_BUCKET), stack(Items.OAK_LOG, 64),
                stack(Items.GOLDEN_APPLE, 6), stack(Items.SHIELD),
            )
        },
        setOf(
            Skill.WATER_CLUTCH, Skill.WEB_ESCAPE, Skill.SHIELD_BREAK, Skill.GAPPLE,
            Skill.PILLAR, Skill.DRAIN, Skill.LAVA_TAP, Skill.GUARD, Skill.COBWEB,
            Skill.BOW,
        ),
        Rules(blastWeight = 0.6),
    ),
    POT(
        {
            listOf(
                stack(Items.DIAMOND_SWORD), splash(Potions.STRONG_HEALING, 16), splash(Potions.STRONG_STRENGTH, 2),
                splash(Potions.STRONG_SWIFTNESS, 2), stack(Items.COOKED_BEEF, 16),
            )
        },
        setOf(Skill.POT_HEAL, Skill.POT_BUFF),
        Rules(oldCombat = true, blastWeight = 0.4),
    ),
    SOUP(
        { listOf(stack(Items.DIAMOND_SWORD)) + List(35) { stack(Items.MUSHROOM_STEW) } },
        setOf(Skill.SOUP),
        Rules(oldCombat = true, instantSoup = true),
    ),
    CLASSIC(
        {
            listOf(
                stack(Items.IRON_SWORD), stack(Items.BOW), stack(Items.FISHING_ROD), stack(Items.ARROW, 16),
                stack(Items.GOLDEN_APPLE, 3),
            )
        },
        setOf(Skill.GAPPLE, Skill.BOW, Skill.ROD),
        Rules(blastWeight = 0.2),
    ),
    SUMO({ emptyList() }, emptySet(), Rules(crits = false, skills = EnumSet.noneOf(Skill::class.java))),
    SHIELDSTUN(
        { listOf(stack(Items.IRON_SWORD), stack(Items.SHIELD)) },
        setOf(Skill.GUARD),
        Rules(crits = false, attacks = false, alwaysGuard = true),
    ),
}

private fun stack(item: Item, count: Int = 1) = ItemStack(item, count)

private fun splash(potion: Holder<Potion>, count: Int): ItemStack =
    PotionContents.createItemStack(Items.SPLASH_POTION, potion).also { it.count = count }
