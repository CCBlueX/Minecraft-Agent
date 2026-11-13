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

/**
 * Whether the agent keeps getting closer to where it goes, a block at a time. A goal it got no closer to for
 * [NO_PROGRESS_TICKS], or the navigator found no way to for [STRANDED_TICKS], is out of reach for now.
 *
 * @agent claude-opus-5-5
 */
internal class Progress {

    private var key: Any? = null
    private var since = 0L
    private var distance = Double.MAX_VALUE
    private var strandedTicks = 0

    /**
     * False once the way to [key], [distance] away now, stopped getting shorter.
     */
    fun check(key: Any, distance: Double, stranded: Boolean, now: Long): Boolean {
        if (key != this.key) {
            this.key = key
            since = now
            this.distance = distance
            strandedTicks = 0
        } else if (distance < this.distance - MIN_PROGRESS) {
            since = now
            this.distance = distance
        }
        strandedTicks = if (stranded) strandedTicks + 1 else 0
        return strandedTicks < STRANDED_TICKS && now - since <= NO_PROGRESS_TICKS
    }

    fun reset() {
        key = null
        strandedTicks = 0
    }

    private companion object {
        const val MIN_PROGRESS = 1.0
        const val STRANDED_TICKS = 40
        const val NO_PROGRESS_TICKS = 200
    }
}
