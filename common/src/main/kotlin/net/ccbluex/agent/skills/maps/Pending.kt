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

package net.ccbluex.agent.skills.maps

/**
 * Something tried that had an effect; [follows] when it went for what the one before turned on. The map said
 * [lines] lines since.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal data class Pending(
    val action: String,
    val at: KnownMap.Point,
    val target: KnownMap.Point?,
    val entity: String?,
    val effect: String,
    val seconds: Double,
    val follows: Boolean,
) {

    var lines = 0

    companion object {

        /**
         * What of [tried] a known map keeps once the player got somewhere new: the last thing tried, the things
         * before it that each turned on the next, and the scenes before those.
         */
        fun keep(tried: List<Pending>): List<Pending> {
            if (tried.isEmpty()) return emptyList()
            var first = tried.lastIndex
            while (first > 0 && tried[first].follows) first--
            return tried.take(first).filter { it.lines >= SCENE_LINES } + tried.drop(first)
        }

        private const val SCENE_LINES = 20
    }
}
