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

package net.ccbluex.agent.v1_8_8.spigot

import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.entity.Attribute
import net.ccbluex.agent.world.entity.Attributes
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.MobEffect
import net.ccbluex.agent.world.entity.MobEffectInstance
import net.ccbluex.agent.world.item.Enchantment
import net.ccbluex.agent.world.item.FoodProperties
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.UseEffects
import net.minecraft.server.v1_8_R3.EnchantmentManager
import net.minecraft.server.v1_8_R3.ItemArmor
import net.minecraft.server.v1_8_R3.ItemFood
import net.minecraft.server.v1_8_R3.ItemGoldenApple
import net.minecraft.server.v1_8_R3.ItemPotion
import net.minecraft.server.v1_8_R3.MobEffectList
import net.minecraft.server.v1_8_R3.ItemStack as McItemStack

/**
 * A 1.8 item stack as the library sees it: the modern item it is, its enchantments by modern name, its food and the
 * effects it gives, read from 1.8's item code.
 *
 * @agent claude-opus-5-5
 */
internal class StackView(val handle: McItemStack) : ItemStack() {

    override val item: Item = GameTypes.toAgent(handle.item, handle.data)

    override val count: Int get() = handle.count

    override val maxStackSize: Int get() = handle.maxStackSize

    override val damageValue: Int get() = if (handle.e()) handle.h() else 0

    override val maxDamage: Int get() = handle.j()

    override val isDamageableItem: Boolean get() = handle.e()

    override fun copy(): ItemStack = StackView(handle.cloneItemStack())

    override fun getDestroySpeed(state: BlockState): Float = handle.a((state as StateView).data.block)

    override fun isCorrectToolForDrops(state: BlockState): Boolean = handle.b((state as StateView).data.block)

    override fun getEnchantmentLevel(enchantment: Enchantment): Int {
        val id = ENCHANTMENTS[enchantment.id] ?: return 0
        return EnchantmentManager.getEnchantmentLevel(id, handle)
    }

    override val enchantments: Map<Enchantment, Int>
        get() = ENCHANTMENTS.mapNotNull { (name, id) ->
            EnchantmentManager.getEnchantmentLevel(id, handle).takeIf { it > 0 }?.let { Enchantment.of(name) to it }
        }.toMap()

    override val food: FoodProperties?
        get() {
            val food = handle.item as? ItemFood ?: return null
            return FoodProperties(food.getNutrition(handle), food.getSaturationModifier(handle), food is ItemGoldenApple)
        }

    override val potionEffects: List<MobEffectInstance>?
        get() = when (val game = handle.item) {
            is ItemPotion -> game.h(handle).orEmpty().map { effect(it.effectId, it.duration, it.amplifier) }
            is ItemGoldenApple -> if (handle.data > 0) ENCHANTED_GOLDEN_APPLE else GOLDEN_APPLE
            else -> null
        }

    override fun computeAttribute(attribute: Attribute, base: Double, slot: EquipmentSlot): Double {
        if (attribute != Attributes.ATTACK_DAMAGE || slot != EquipmentSlot.MAINHAND) return base
        // 1.8's weapon modifiers add to the attack damage (operation 0)
        return base + handle.B().get(ATTACK_DAMAGE).filter { it.c() == 0 }.sumOf { it.d() }
    }

    override val equipmentSlot: EquipmentSlot?
        get() = when ((handle.item as? ItemArmor)?.b) {
            0 -> EquipmentSlot.HEAD
            1 -> EquipmentSlot.CHEST
            2 -> EquipmentSlot.LEGS
            3 -> EquipmentSlot.FEET
            else -> null
        }

    // A 1.8 client slows any item use to 0.2 and stops sprinting
    override val useEffects: UseEffects get() = UseEffects.DEFAULT

    override val fireworkFlightDuration: Int? get() = null

    override fun canGlideUsing(slot: EquipmentSlot): Boolean = false

    override fun sameData(other: ItemStack): Boolean {
        val game = (other as? StackView)?.handle ?: return false
        return handle.data == game.data && handle.tag == game.tag
    }

    companion object {
        private const val ATTACK_DAMAGE = "generic.attackDamage"

        // 1.8's enchantment ids by modern name
        private val ENCHANTMENTS = mapOf(
            "minecraft:protection" to 0, "minecraft:fire_protection" to 1, "minecraft:feather_falling" to 2,
            "minecraft:blast_protection" to 3, "minecraft:projectile_protection" to 4, "minecraft:respiration" to 5,
            "minecraft:aqua_affinity" to 6, "minecraft:thorns" to 7, "minecraft:depth_strider" to 8,
            "minecraft:sharpness" to 16, "minecraft:smite" to 17, "minecraft:bane_of_arthropods" to 18,
            "minecraft:knockback" to 19, "minecraft:fire_aspect" to 20, "minecraft:looting" to 21,
            "minecraft:efficiency" to 32, "minecraft:silk_touch" to 33, "minecraft:unbreaking" to 34,
            "minecraft:fortune" to 35, "minecraft:power" to 48, "minecraft:punch" to 49, "minecraft:flame" to 50,
            "minecraft:infinity" to 51, "minecraft:luck_of_the_sea" to 61, "minecraft:lure" to 62,
        )

        // 1.8's effect ids, from 1
        private val EFFECTS = listOf(
            "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
            "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
            "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption",
            "saturation",
        )

        // ItemGoldenApple.c: what eating one gives
        private val GOLDEN_APPLE = listOf(effect(MobEffectList.REGENERATION.id, 100, 1), effect(MobEffectList.ABSORBTION.id, 2400, 0))
        private val ENCHANTED_GOLDEN_APPLE = listOf(
            effect(MobEffectList.REGENERATION.id, 600, 4),
            effect(MobEffectList.RESISTANCE.id, 6000, 0),
            effect(MobEffectList.FIRE_RESISTANCE.id, 6000, 0),
            effect(MobEffectList.ABSORBTION.id, 2400, 0),
        )

        fun effectOf(id: Int): MobEffect = MobEffect.of(EFFECTS.getOrElse(id - 1) { "unknown_$id" })

        fun effect(id: Int, duration: Int, amplifier: Int) = MobEffectInstance(effectOf(id), duration, amplifier)

        fun of(stack: McItemStack?): ItemStack =
            if (stack == null || stack.item == null || stack.count <= 0) EMPTY else StackView(stack)
    }
}
