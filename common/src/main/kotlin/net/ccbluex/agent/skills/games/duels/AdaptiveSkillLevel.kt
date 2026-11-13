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

package net.ccbluex.agent.skills.games.duels

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.SkillLevel
import java.util.UUID

/**
 * Picks the difficulty of a player's next duel per mode: it starts at [start], goes a step up when the player wins
 * and a step down when they lose, between [SkillLevel.BEGINNER] and [SkillLevel.HARD].
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class AdaptiveSkillLevel @JvmOverloads constructor(private val start: SkillLevel = SkillLevel.EASY) {

    private val ratings = HashMap<UUID, HashMap<String, SkillLevel>>()

    /**
     * The difficulty [player]'s next duel in [mode] is fought at.
     */
    public fun skillLevel(player: UUID, mode: String): SkillLevel = ratings[player]?.get(mode) ?: start

    /**
     * The duel the agent fought at [skillLevel] against [player] in [mode] ended [result] for the agent.
     */
    public fun record(player: UUID, mode: String, skillLevel: SkillLevel, result: Outcome) {
        val steps = when (result) {
            Outcome.WON -> -1
            Outcome.LOST -> 1
            Outcome.DRAW -> return
        }
        val next = stepped(skillLevel, steps) ?: return
        ratings.getOrPut(player) { HashMap() }[mode] = next
    }

    /**
     * Forgets [player], such as when they leave.
     */
    public fun forget(player: UUID) {
        ratings.remove(player)
    }

    private companion object {
        val LEVELS = listOf(SkillLevel.BEGINNER, SkillLevel.EASY, SkillLevel.NORMAL, SkillLevel.HARD)

        /**
         * The level [steps] harder than [skillLevel]; null past either end or for a level not in [LEVELS].
         */
        fun stepped(skillLevel: SkillLevel, steps: Int): SkillLevel? {
            val index = LEVELS.indexOf(skillLevel)
            if (index < 0) return null
            return LEVELS.getOrNull(index + steps)
        }
    }
}
