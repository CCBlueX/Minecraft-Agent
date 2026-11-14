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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Where the server set the player back. One setback can be lag; setbacks soon after each other, each close to one of
 * those before, are one spot the server refuses, such as a climb set back a little further up each time. Walking
 * the same way again only repeats them.
 *
 * @agent claude-opus-5-5
 */
internal class Setbacks {

    private val spot = ArrayList<Vec3>()
    private var lastTick = 0L

    /**
     * Notes a setback of the player from [at] on [tick]; the feet blocks of the refused spot once it is set back
     * there more than once, else none.
     */
    fun refused(at: Vec3, tick: Long): Set<BlockPos> {
        val joins = tick - lastTick <= WINDOW_TICKS && spot.any { it.closerThan(at, RADIUS) }
        if (!joins) spot.clear()
        spot += at
        lastTick = tick
        return if (spot.size > 1) spot.mapTo(LinkedHashSet(), BlockPos::containing) else emptySet()
    }

    private companion object {
        /**
         * Blocks between two setbacks of one spot; a pass along it is set back within about a block.
         */
        const val RADIUS = 1.5
        const val WINDOW_TICKS = 60L
    }
}
