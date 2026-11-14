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

package net.ccbluex.agent.skills.games

import net.ccbluex.agent.navigation.Navigator

/**
 * Whether the agent keeps getting closer to where it goes, a block at a time. A goal it got no closer to for
 * [NO_PROGRESS_TICKS], or the navigator found no way to for [Navigator.UNREACHABLE_TICKS], is out of reach for now.
 *
 * @agent claude-opus-5-5
 */
internal class Progress {

    private var key: Any? = null
    private var since = 0L
    private var distance = Double.MAX_VALUE

    /**
     * The navigator's stranded ticks from before the current key, which do not count against it.
     */
    private var strandedBefore = 0

    /**
     * False once the way to [key], [distance] away now, stopped getting shorter; [strandedTicks] is
     * [Navigator.strandedTicks].
     */
    fun check(key: Any, distance: Double, strandedTicks: Int, now: Long): Boolean {
        if (key != this.key) {
            this.key = key
            since = now
            this.distance = distance
            strandedBefore = strandedTicks
        } else if (distance < this.distance - MIN_PROGRESS) {
            since = now
            this.distance = distance
        }
        if (strandedTicks < strandedBefore) strandedBefore = 0
        return strandedTicks - strandedBefore < Navigator.UNREACHABLE_TICKS && now - since <= NO_PROGRESS_TICKS
    }

    fun reset() {
        key = null
    }

    private companion object {
        const val MIN_PROGRESS = 1.0
        const val NO_PROGRESS_TICKS = 200
    }
}
