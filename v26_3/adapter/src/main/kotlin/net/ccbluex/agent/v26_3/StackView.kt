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

package net.ccbluex.agent.v26_3

import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.entity.Attribute
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.MobEffect
import net.ccbluex.agent.world.entity.MobEffectInstance
import net.ccbluex.agent.world.item.Enchantment
import net.ccbluex.agent.world.item.FoodProperties
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.UseEffects
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.component.ItemAttributeModifiers
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.item.ItemStack as McItemStack

/**
 * A stack of the game as the agents see it; it shows later changes to [handle], as the game's stack does.
 *
 * @agent claude-opus-5-5
 */
internal class StackView(val handle: McItemStack) : ItemStack() {

    override val item: Item get() = handle.item.toAgent()
    override val count: Int get() = handle.count
    override val maxStackSize: Int get() = handle.maxStackSize
    override val damageValue: Int get() = handle.damageValue
    override val maxDamage: Int get() = handle.maxDamage
    override val isDamageableItem: Boolean get() = handle.isDamageableItem
    override val isEmpty: Boolean get() = handle.isEmpty

    override fun copy(): ItemStack = StackView(handle.copy())

    override fun getDestroySpeed(state: BlockState): Float = handle.getDestroySpeed(state.toMinecraft())

    override fun isCorrectToolForDrops(state: BlockState): Boolean = handle.isCorrectToolForDrops(state.toMinecraft())

    override val isTool: Boolean get() = handle.has(DataComponents.TOOL)

    override fun isToolCorrectForDrops(state: BlockState): Boolean =
        handle.get(DataComponents.TOOL)?.isCorrectForDrops(state.toMinecraft()) == true

    override fun getEnchantmentLevel(enchantment: Enchantment): Int =
        handle.enchantments.entrySet().firstOrNull { it.key.id == enchantment.id }?.intValue ?: 0

    override val enchantments: Map<Enchantment, Int>
        get() = handle.enchantments.entrySet().associate { Enchantment.of(it.key.id) to it.intValue }

    override val food: FoodProperties?
        get() = handle.get(DataComponents.FOOD)?.let {
            FoodProperties(it.nutrition(), it.saturation(), it.canAlwaysEat())
        }

    override val potionEffects: List<MobEffectInstance>?
        get() = handle.get(DataComponents.POTION_CONTENTS)?.allEffects?.map {
            MobEffectInstance(MobEffect.of(it.effect.id), it.duration, it.amplifier)
        }

    override fun computeAttribute(attribute: Attribute, base: Double, slot: EquipmentSlot): Double =
        handle.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .compute(attribute.toMinecraft(), base, slot.toMinecraft())

    override val equipmentSlot: EquipmentSlot? get() = handle.get(DataComponents.EQUIPPABLE)?.slot()?.toAgent()

    override val useEffects: UseEffects
        get() = handle.get(DataComponents.USE_EFFECTS)?.let { UseEffects(it.canSprint(), it.speedMultiplier()) }
            ?: UseEffects.DEFAULT

    override val fireworkFlightDuration: Int? get() = handle.get(DataComponents.FIREWORKS)?.flightDuration()

    override fun canGlideUsing(slot: EquipmentSlot): Boolean = LivingEntity.canGlideUsing(handle, slot.toMinecraft())

    override fun sameData(other: ItemStack): Boolean =
        McItemStack.isSameItemSameComponents(handle, other.toMinecraft())

    override fun equals(other: Any?): Boolean = other is StackView && other.handle === handle

    override fun hashCode(): Int = System.identityHashCode(handle)
}

/**
 * The game's stack behind [this]; a copy for stacks the game did not make, which only the empty stack is.
 *
 * @agent claude-opus-5-5
 */
public fun ItemStack.toMinecraft(): McItemStack = (this as? StackView)?.handle ?: McItemStack.EMPTY

/**
 * @agent claude-opus-5-5
 */
public fun McItemStack.toAgent(): ItemStack = if (isEmpty) ItemStack.EMPTY else StackView(this)

/**
 * The registry id of the value behind this holder, such as `minecraft:sharpness`.
 *
 * @agent claude-opus-5-5
 */
internal val Holder<*>.id: String
    get() = unwrapKey().orElseThrow().identifier().toString()

private val attributes = ConcurrentHashMap<Attribute, Holder<net.minecraft.world.entity.ai.attributes.Attribute>>()

/**
 * @agent claude-opus-5-5
 */
internal fun Attribute.toMinecraft(): Holder<net.minecraft.world.entity.ai.attributes.Attribute> =
    attributes.computeIfAbsent(this) { BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(it.id)).orElseThrow() }

private val effects = ConcurrentHashMap<MobEffect, Holder<net.minecraft.world.effect.MobEffect>>()

/**
 * @agent claude-opus-5-5
 */
internal fun MobEffect.toMinecraft(): Holder<net.minecraft.world.effect.MobEffect> =
    effects.computeIfAbsent(this) { BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(it.id)).orElseThrow() }
