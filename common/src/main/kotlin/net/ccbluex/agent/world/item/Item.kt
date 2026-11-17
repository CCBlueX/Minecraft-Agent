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

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.namespaced
import java.util.concurrent.ConcurrentHashMap

/**
 * An item type by its modern id, such as `minecraft:golden_apple`. One instance per id, so items compare with `==`.
 * The adapter of the running game tells it its facts ([define]); an item the game lacks (a shield on 1.8) stays
 * undefined and never turns up in an inventory.
 *
 * @agent claude-opus-5-5
 */
public class Item private constructor(public val id: String) {

    /**
     * The block it places; null for items that are no block.
     */
    public var block: Block? = null
        private set
    private var tags: (TagKey<Item>) -> Boolean = { false }
    private var defaultInstance: Lazy<ItemStack>? = null

    /**
     * Whether the running game has this item.
     */
    public val isDefined: Boolean get() = defaultInstance != null

    /**
     * One of this item with its default data, for its facts; [ItemStack.EMPTY] for an item the game lacks.
     */
    public fun getDefaultInstance(): ItemStack = defaultInstance?.value ?: ItemStack.EMPTY

    public fun `is`(tag: TagKey<Item>): Boolean = tags(tag)

    /**
     * Sets the game's facts; [defaultInstance] runs on first use, once the game has bound the item's default data.
     *
     * @agent claude-opus-5-5
     */
    @InternalAgentApi
    public fun define(defaultInstance: () -> ItemStack, block: Block?, tags: (TagKey<Item>) -> Boolean) {
        this.defaultInstance = lazy(defaultInstance)
        this.block = block
        this.tags = tags
    }

    override fun toString(): String = id

    public companion object {
        private val items = ConcurrentHashMap<String, Item>()

        @JvmField
        public val AIR: Item = of("air")

        @JvmStatic
        public fun of(id: String): Item = items.computeIfAbsent(namespaced(id)) { Item(it) }

        /**
         * Every item the game defines, as its registry lists them.
         */
        @JvmStatic
        public fun all(): List<Item> = items.values.filter { it.isDefined }
    }
}

/**
 * The item tags the agents read.
 *
 * @agent claude-opus-5-5
 */
public object ItemTags {
    @JvmField public val SWORDS: TagKey<Item> = TagKey.of("swords")
    @JvmField public val AXES: TagKey<Item> = TagKey.of("axes")
    @JvmField public val PICKAXES: TagKey<Item> = TagKey.of("pickaxes")
    @JvmField public val LOGS: TagKey<Item> = TagKey.of("logs")
    @JvmField public val PLANKS: TagKey<Item> = TagKey.of("planks")
    @JvmField public val BEDS: TagKey<Item> = TagKey.of("beds")
    @JvmField public val BOATS: TagKey<Item> = TagKey.of("boats")
    @JvmField public val HOES: TagKey<Item> = TagKey.of("hoes")
}

/**
 * An enchantment by its id.
 *
 * @agent claude-opus-5-5
 */
public class Enchantment private constructor(public val id: String) {
    override fun toString(): String = id

    public companion object {
        private val enchantments = ConcurrentHashMap<String, Enchantment>()

        @JvmStatic
        public fun of(id: String): Enchantment = enchantments.computeIfAbsent(namespaced(id)) { Enchantment(it) }
    }
}

/**
 * @agent claude-opus-5-5
 */
public object Enchantments {
    @JvmField public val BREACH: Enchantment = Enchantment.of("breach")
    @JvmField public val DENSITY: Enchantment = Enchantment.of("density")
    @JvmField public val EFFICIENCY: Enchantment = Enchantment.of("efficiency")
    @JvmField public val FORTUNE: Enchantment = Enchantment.of("fortune")
    @JvmField public val FLAME: Enchantment = Enchantment.of("flame")
    @JvmField public val INFINITY: Enchantment = Enchantment.of("infinity")
    @JvmField public val LUNGE: Enchantment = Enchantment.of("lunge")
    @JvmField public val PROTECTION: Enchantment = Enchantment.of("protection")
    @JvmField public val SHARPNESS: Enchantment = Enchantment.of("sharpness")
    @JvmField public val WIND_BURST: Enchantment = Enchantment.of("wind_burst")
    @JvmField public val KNOCKBACK: Enchantment = Enchantment.of("knockback")
    @JvmField public val PUNCH: Enchantment = Enchantment.of("punch")
    @JvmField public val POWER: Enchantment = Enchantment.of("power")
}
