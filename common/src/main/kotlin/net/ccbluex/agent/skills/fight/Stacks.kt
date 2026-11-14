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

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock

// What the fight tells items apart by. Item sets instead of tags: tags are bound only once a world loads.

private val SWORDS = setOf(
    Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD,
    Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
)
private val AXES = setOf(
    Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE,
    Items.NETHERITE_AXE,
)
private val SPEARS = setOf(
    Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.GOLDEN_SPEAR, Items.IRON_SPEAR,
    Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR,
)
private val SHOVELS = setOf(
    Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.COPPER_SHOVEL, Items.GOLDEN_SHOVEL, Items.IRON_SHOVEL,
    Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
)

/**
 * Blocks that are no use for building: blasts, light and what a skill places on purpose.
 */
private val NOT_FOR_BUILDING = setOf(Blocks.OBSIDIAN, Blocks.RESPAWN_ANCHOR, Blocks.GLOWSTONE, Blocks.TNT)

internal val ItemStack.isSword: Boolean get() = item in SWORDS
internal val ItemStack.isAxe: Boolean get() = item in AXES
internal val ItemStack.isSpear: Boolean get() = item in SPEARS
internal val ItemStack.isShovel: Boolean get() = item in SHOVELS
internal val ItemStack.isGoldenApple: Boolean get() = `is`(Items.GOLDEN_APPLE) || `is`(Items.ENCHANTED_GOLDEN_APPLE)

/**
 * A full block without gravity to bridge, wall and pillar with.
 */
internal val ItemStack.isBuildingBlock: Boolean
    get() {
        val block = (item as? BlockItem)?.block ?: return false
        if (block is FallingBlock || block in NOT_FOR_BUILDING) return false
        return block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
    }

internal fun ItemStack.isOf(item: Item): Boolean = !isEmpty && `is`(item)

internal fun ItemStack.enchantLevel(key: ResourceKey<Enchantment>): Int =
    enchantments.entrySet().firstOrNull { it.key.`is`(key) }?.intValue ?: 0

/**
 * Whether the splash potion carries [effect].
 */
internal fun ItemStack.splashes(effect: Holder<MobEffect>): Boolean {
    if (!`is`(Items.SPLASH_POTION)) return false
    val contents = get(DataComponents.POTION_CONTENTS) ?: return false
    return contents.allEffects.any { it.effect == effect }
}

/**
 * Which weapon the fight holds between skills: a sword or spear first, then an axe or mace, then a shovel; 0 for
 * anything else.
 */
internal val ItemStack.weaponRank: Int
    get() = when {
        isEmpty -> 0
        isSword || isSpear -> 3
        isAxe || `is`(Items.MACE) -> 2
        isShovel -> 1
        else -> 0
    }

/**
 * Damage per second of full-charge hits with this item under 1.9 rules, from its attack damage and speed, sharpness
 * included: a wooden sword's 6.4 loses to a stone axe's 7.2, a diamond sword's 11.2 beats a diamond axe's 9.
 *
 * @agent claude-opus-5-5
 */
internal val ItemStack.damagePerSecond: Double
    get() {
        val modifiers = getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        val sharpness = enchantLevel(Enchantments.SHARPNESS)
        val damage = modifiers.compute(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE, EquipmentSlot.MAINHAND) +
            if (sharpness > 0) 0.5 + 0.5 * sharpness else 0.0
        return damage * modifiers.compute(Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED, EquipmentSlot.MAINHAND)
    }

private const val BASE_ATTACK_DAMAGE = 1.0
private const val BASE_ATTACK_SPEED = 4.0
