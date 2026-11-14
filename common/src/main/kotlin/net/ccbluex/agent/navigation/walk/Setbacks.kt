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

package net.ccbluex.agent.navigation.walk

import net.minecraft.world.phys.Vec3

/**
 * Where the server set the player back. One setback can be lag; a second one close to it soon after is a spot the
 * server refuses, which walking the same way again only repeats.
 *
 * @agent claude-opus-5-5
 */
internal class Setbacks {

    private var last: Vec3? = null
    private var lastTick = 0L

    /**
     * Notes a setback of the player from [at] on [tick]; true if it repeats the one before.
     */
    fun refuses(at: Vec3, tick: Long): Boolean {
        val before = last
        val repeated = before != null && tick - lastTick <= WINDOW_TICKS && before.closerThan(at, RADIUS)
        last = at
        lastTick = tick
        return repeated
    }

    private companion object {
        /**
         * Blocks between two setbacks of one spot; a pass along it is set back within about a block.
         */
        const val RADIUS = 1.5
        const val WINDOW_TICKS = 60L
    }
}
