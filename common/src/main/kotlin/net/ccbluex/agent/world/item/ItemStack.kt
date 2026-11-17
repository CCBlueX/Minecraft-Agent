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

package net.ccbluex.agent.world.item

import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.entity.Attribute
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.MobEffectInstance

/**
 * A stack of items with its data. The adapter wraps the game's stacks, so a stack read from an inventory shows later
 * changes to it, as the game's does; [copy] keeps one as it is.
 *
 * @agent claude-opus-5-5
 */
public abstract class ItemStack {

    public abstract val item: Item

    public abstract val count: Int

    public abstract val maxStackSize: Int

    public abstract val damageValue: Int

    public abstract val maxDamage: Int

    public abstract val isDamageableItem: Boolean

    public open val isEmpty: Boolean get() = item === Item.AIR || count <= 0

    public abstract fun copy(): ItemStack

    /**
     * How fast this item mines [state]: 1 by hand, more with the right tool.
     */
    public abstract fun getDestroySpeed(state: BlockState): Float

    public abstract fun isCorrectToolForDrops(state: BlockState): Boolean

    public abstract fun getEnchantmentLevel(enchantment: Enchantment): Int

    public abstract val enchantments: Map<Enchantment, Int>

    /**
     * What eating it gives; null for anything that is no food.
     */
    public abstract val food: FoodProperties?

    /**
     * The effects of a potion, splash or lingering potion or tipped arrow; null for other items.
     */
    public abstract val potionEffects: List<MobEffectInstance>?

    /**
     * [base] with this item's modifiers of [attribute] applied, as when held or worn in [slot].
     */
    public abstract fun computeAttribute(attribute: Attribute, base: Double, slot: EquipmentSlot): Double

    /**
     * The slot this item is worn in; null for items that are not worn.
     */
    public abstract val equipmentSlot: EquipmentSlot?

    public abstract val useEffects: UseEffects

    /**
     * The flight duration of a firework rocket; null for other items.
     */
    public abstract val fireworkFlightDuration: Int?

    /**
     * Whether worn in [slot] this item lets its wearer glide, as an elytra that is not about to break.
     */
    public abstract fun canGlideUsing(slot: EquipmentSlot): Boolean

    public fun `is`(item: Item): Boolean = this.item === item

    public fun `is`(tag: TagKey<Item>): Boolean = item.`is`(tag)

    override fun toString(): String = "$count $item"

    public companion object {
        @JvmField
        public val EMPTY: ItemStack = Empty

        /**
         * Whether both are the same item with the same data and count.
         */
        @JvmStatic
        public fun matches(a: ItemStack, b: ItemStack): Boolean =
            a === b || a.isEmpty && b.isEmpty || a.count == b.count && isSameItemSameComponents(a, b)

        @JvmStatic
        public fun isSameItemSameComponents(a: ItemStack, b: ItemStack): Boolean =
            a.item === b.item && a.sameData(b)
    }

    /**
     * Whether [other] carries the same data (damage, enchantments, contents) as this stack.
     */
    protected abstract fun sameData(other: ItemStack): Boolean

    private object Empty : ItemStack() {
        override val item: Item get() = Item.AIR
        override val count: Int get() = 0
        override val maxStackSize: Int get() = 64
        override val damageValue: Int get() = 0
        override val maxDamage: Int get() = 0
        override val isDamageableItem: Boolean get() = false
        override val isEmpty: Boolean get() = true
        override fun copy(): ItemStack = this
        override fun getDestroySpeed(state: BlockState): Float = 1f
        override fun isCorrectToolForDrops(state: BlockState): Boolean = false
        override fun getEnchantmentLevel(enchantment: Enchantment): Int = 0
        override val enchantments: Map<Enchantment, Int> get() = emptyMap()
        override val food: FoodProperties? get() = null
        override val potionEffects: List<MobEffectInstance>? get() = null
        override fun computeAttribute(attribute: Attribute, base: Double, slot: EquipmentSlot): Double = base
        override val equipmentSlot: EquipmentSlot? get() = null
        override val useEffects: UseEffects get() = UseEffects.DEFAULT
        override val fireworkFlightDuration: Int? get() = null
        override fun canGlideUsing(slot: EquipmentSlot): Boolean = false
        override fun sameData(other: ItemStack): Boolean = other.isEmpty
    }
}

/**
 * @agent claude-opus-5-5
 */
public class FoodProperties(public val nutrition: Int, public val saturation: Float, public val canAlwaysEat: Boolean)

/**
 * How using an item changes moving: whether the player may sprint and how fast it walks meanwhile.
 *
 * @agent claude-opus-5-5
 */
public class UseEffects(public val canSprint: Boolean, public val speedMultiplier: Float) {
    public companion object {
        @JvmField
        public val DEFAULT: UseEffects = UseEffects(false, 0.2f)
    }
}
