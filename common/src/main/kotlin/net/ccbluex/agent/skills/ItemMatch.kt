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

package net.ccbluex.agent.skills


import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack

/**
 * Which item stacks count, such as the swords that make a wish come true.
 *
 * @agent claude-opus-5-5
 */
public fun interface ItemMatch {

    public fun matches(stack: ItemStack): Boolean

    public companion object {

        /**
         * Any of [items].
         */
        @JvmStatic
        public fun of(vararg items: Item): ItemMatch {
            val set = items.toSet()
            return ItemMatch { !it.isEmpty && it.item in set }
        }

        @JvmStatic
        public fun tag(tag: TagKey<Item>): ItemMatch = ItemMatch { !it.isEmpty && it.`is`(tag) }

        /**
         * Items whose id ends in [suffix], such as `_sword`; works before tags are loaded.
         */
        @JvmStatic
        public fun named(suffix: String): ItemMatch = ItemMatch { !it.isEmpty && it.itemId.endsWith(suffix) }
    }
}

/**
 * The path of the item's id, such as `iron_sword`.
 */
internal val ItemStack.itemId: String
    get() = item.id.substringAfter(":")
