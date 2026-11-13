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

package net.ccbluex.agent.skills.fight

import net.minecraft.world.phys.Vec3

/**
 * Where the fighter's eyes were over the last second, to tell how far away a player's screen shows it. A client
 * shows another player a ping and a few ticks late: the server sends moves every other tick and the client eases
 * into each over three.
 *
 * @agent claude-opus-5-5
 */
internal class EyeTrail {

    private class Eye(val at: Long, val position: Vec3)

    /**
     * Newest first.
     */
    private val eyes = ArrayDeque<Eye>()

    fun record(now: Long, eye: Vec3) {
        eyes.addFirst(Eye(now, eye))
        while (now - eyes.last().at > 2 * MAX_VIEW_MILLIS) eyes.removeLast()
    }

    /**
     * Where the eyes were [lag] milliseconds before [now]; the oldest kept when the trail is shorter.
     */
    fun at(now: Long, lag: Long): Vec3? {
        if (eyes.isEmpty()) return null
        val since = now - lag
        return (eyes.firstOrNull { it.at <= since } ?: eyes.last()).position
    }

    fun clear() = eyes.clear()

    companion object {
        const val VIEW_MILLIS = 100L
        const val MAX_VIEW_MILLIS = 500L

        /**
         * How late a player with [pingMillis] sees the fighter.
         */
        fun viewLag(pingMillis: Int): Long = minOf(MAX_VIEW_MILLIS, VIEW_MILLIS + maxOf(0, pingMillis))
    }
}
