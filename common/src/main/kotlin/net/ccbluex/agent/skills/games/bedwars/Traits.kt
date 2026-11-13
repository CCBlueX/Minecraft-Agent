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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.Personality

/**
 * The leanings a Bed Wars agent weighs its activities by, 0 to 1 each.
 *
 * @property aggression rushing beds and chasing players
 * @property greed diamonds, emeralds and team upgrades
 * @property caution covering and defending the own bed
 * @property patience sticking with the activity, waiting at ore generators
 * @property skill sharper choices: less noise, quicker to think again
 * @agent claude-opus-5-5
 */
internal data class Traits(
    val aggression: Double,
    val greed: Double,
    val caution: Double,
    val patience: Double,
    val skill: Double,
) {

    /**
     * Gaussian noise on each score of a think.
     */
    val noise: Double
        get() = 0.05 + 0.15 * (1 - skill)

    companion object {
        private const val LOW = 0.05
        private const val HIGH = 0.95

        fun of(personality: Personality, skillLevel: SkillLevel, strategy: Strategy): Traits {
            val traits = Traits(
                aggression = personality.aggression.coerceIn(LOW, HIGH),
                greed = personality.greed.coerceIn(LOW, HIGH),
                caution = personality.caution.coerceIn(LOW, HIGH),
                patience = personality.patience.coerceIn(LOW, HIGH),
                skill = skill(skillLevel),
            )
            return when (strategy) {
                Strategy.ADAPTIVE -> traits
                Strategy.RUSH -> traits.copy(aggression = HIGH, caution = LOW)
                Strategy.HOLD_BASE -> traits.copy(aggression = LOW, caution = HIGH)
            }
        }

        // The reaction multiplier runs from 3 (beginner) to 0.6 (hard), which spans the skill range
        private fun skill(skillLevel: SkillLevel): Double = ((3.0 - skillLevel.reaction) / 2.4).coerceIn(LOW, HIGH)
    }
}
