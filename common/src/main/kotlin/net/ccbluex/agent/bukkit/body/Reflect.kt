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

package net.ccbluex.agent.bukkit.body

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * @agent claude-opus-5-5
 */
internal object Reflect {

    fun field(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }

    fun method(owner: Class<*>, name: String, vararg parameters: Class<*>): Method =
        owner.getDeclaredMethod(name, *parameters).apply { isAccessible = true }

    fun invoke(method: Method, owner: Any, vararg arguments: Any?) {
        try {
            method.invoke(owner, *arguments)
        } catch (e: InvocationTargetException) {
            throw IllegalStateException(e.cause)
        }
    }
}
