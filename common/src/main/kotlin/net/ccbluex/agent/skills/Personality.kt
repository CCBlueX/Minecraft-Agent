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

package net.ccbluex.agent.skills

import java.util.Random

/**
 * How one bot leans, rolled per bot and game. Traits run from 0 to 1 and weigh the choices a game mode scores;
 * none decides a game on its own.
 *
 * @property aggression how much it likes going for players and rushing early
 * @property caution how early it backs off to heal and how much better gear it wants before a fight
 * @property greed how much it likes more loot: the middle, empty islands, refills, ores, upgrades
 * @property opportunism how much it likes joining a fight two others are having
 * @property ranged how keen it is to shoot or throw at players across a gap
 * @property patience how long it sticks with what it does before something else looks better
 * @property whim 0.5 to 1.5: how much its scores waver from one decision to the next
 * @property fighting how it moves and which skills it reaches for in a fight
 * @agent claude-opus-5-5
 */
public data class Personality(
    val aggression: Double,
    val caution: Double,
    val greed: Double,
    val opportunism: Double,
    val ranged: Double,
    val patience: Double,
    val whim: Double,
    val fighting: FightingStyle,
) {

    public companion object {

        /**
         * Middling in everything, fighting with no particular style.
         */
        @JvmField
        public val BALANCED: Personality = Personality(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0, FightingStyle.NONE)

        /**
         * Rolls traits that are mostly middling: caution leans against aggression, and the fighting style follows
         * the strongest lean.
         */
        @JvmStatic
        public fun roll(random: Random): Personality {
            val aggression = trait(random)
            val caution = (1 - aggression + random.nextGaussian() * 0.2).coerceIn(0.0, 1.0)
            val ranged = trait(random)
            val fighting = when {
                aggression > 0.75 -> if (random.nextBoolean()) FightingStyle.AGGRESSIVE else FightingStyle.TRADER
                caution > 0.75 -> FightingStyle.DEFENSIVE
                ranged > 0.7 -> FightingStyle.KITER
                random.nextInt(5) == 0 -> FightingStyle.TRICKSTER
                else -> FightingStyle.NONE
            }
            return Personality(
                aggression = aggression,
                caution = caution,
                greed = trait(random),
                opportunism = trait(random),
                ranged = ranged,
                patience = trait(random),
                whim = 0.5 + random.nextDouble(),
                fighting = fighting,
            )
        }

        private fun trait(random: Random) = (random.nextDouble() + random.nextDouble()) / 2
    }
}

/**
 * How a fighter likes to fight. It changes how the bot moves and which skills it reaches for, never how well it
 * clicks, aims or reacts: that is [SkillLevel].
 *
 * @property wtap multiplies the w-tap chance
 * @property strafe multiplies the strafe chance
 * @property retreats whether healing and eating back off
 * @property hitBack whether every hit taken is answered at once
 * @property heal multiplies the health share under which the bot heals or flees
 * @agent claude-opus-5-5
 */
public enum class FightingStyle(
    public val wtap: Double,
    public val strafe: Double,
    public val retreats: Boolean,
    public val hitBack: Boolean,
    public val heal: Double,
) {
    NONE(1.0, 1.0, true, false, 1.0),

    /**
     * Always closes in, w-taps more and does not back off to heal.
     */
    AGGRESSIVE(1.6, 0.6, false, false, 1.0),

    /**
     * Shields and heals early, keeps its distance while its hit charges and counters every hit.
     */
    DEFENSIVE(1.0, 1.0, true, true, 1.4),

    /**
     * Bow and rod first, holding the range they need.
     */
    KITER(1.0, 1.0, true, false, 1.0),

    /**
     * Stands and trades: no strafing, no backing off, hits straight back.
     */
    TRADER(1.0, 0.0, false, true, 1.0),

    /**
     * Pearls and cobwebs more, and backs off now and then to draw the opponent in.
     */
    TRICKSTER(1.0, 1.0, true, false, 1.0),
}
