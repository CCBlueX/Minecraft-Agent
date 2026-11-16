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

package net.ccbluex.agent.world

import java.util.concurrent.ConcurrentHashMap

/**
 * A named set of blocks or items, such as `minecraft:logs`; the adapter tells each block and item its tags.
 *
 * @agent claude-opus-5-5
 */
public class TagKey<T> private constructor(public val id: String) {

    override fun toString(): String = "#$id"

    public companion object {
        private val tags = ConcurrentHashMap<String, TagKey<*>>()

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        public fun <T> of(id: String): TagKey<T> = tags.computeIfAbsent(namespaced(id)) { TagKey<T>(it) } as TagKey<T>
    }
}

/**
 * [id] with the `minecraft:` namespace when it has none.
 *
 * @agent claude-opus-5-5
 */
public fun namespaced(id: String): String = if (':' in id) id else "minecraft:$id"
