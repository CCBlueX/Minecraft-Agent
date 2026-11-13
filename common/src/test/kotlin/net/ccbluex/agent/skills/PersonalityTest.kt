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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Rolled personalities stay within their ranges, and the fighting style follows the strongest lean.
 *
 * @agent claude-opus-5-5
 */
class PersonalityTest {

    private val rolls = (0L until 5000L).map { Personality.roll(Random(it)) }

    @Test
    fun traitsStayBetweenZeroAndOne() {
        for (personality in rolls) {
            with(personality) {
                for (trait in listOf(aggression, caution, greed, opportunism, ranged, patience)) {
                    assertTrue(trait in 0.0..1.0, "trait $trait of $personality")
                }
                assertTrue(whim in 0.5..1.5, "whim of $personality")
            }
        }
    }

    @Test
    fun traitsAreMostlyMiddling() {
        val greed = rolls.map { it.greed }
        // The mean of two uniform draws lands within 0.25 of the middle three times in four
        val middling = greed.count { it in 0.25..0.75 }
        assertTrue(middling > rolls.size * 0.7, "$middling of ${rolls.size} middling")
        assertEquals(0.5, greed.average(), 0.02)
    }

    @Test
    fun cautionLeansAgainstAggression() {
        val bold = rolls.filter { it.aggression > 0.7 }.map { it.caution }.average()
        val timid = rolls.filter { it.aggression < 0.3 }.map { it.caution }.average()
        assertTrue(bold < 0.4 && timid > 0.6, "caution of bold $bold, of timid $timid")
    }

    @Test
    fun fightingStyleFollowsTheStrongestLean() {
        for (personality in rolls) {
            val style = personality.fighting
            when {
                personality.aggression > 0.75 ->
                    assertTrue(style == FightingStyle.AGGRESSIVE || style == FightingStyle.TRADER, "$personality")
                personality.caution > 0.75 -> assertEquals(FightingStyle.DEFENSIVE, style, "$personality")
                personality.ranged > 0.7 -> assertEquals(FightingStyle.KITER, style, "$personality")
                else -> assertTrue(style == FightingStyle.TRICKSTER || style == FightingStyle.NONE, "$personality")
            }
        }
        assertTrue(rolls.any { it.fighting == FightingStyle.TRICKSTER })
    }

    @Test
    fun difficultyMixesMeleeAndBlastUse() {
        val normal = SkillLevel.NORMAL
        assertEquals(normal.melee, normal.use(0.0))
        assertEquals(normal.blast, normal.use(1.0))
        assertEquals((normal.melee.skill + normal.blast.skill) / 2, normal.use(0.5).skill, 1e-9)
        assertEquals(SkillLevel.HARD, SkillLevel.of("Hard"))
        assertEquals(SkillLevel.NORMAL, SkillLevel.of("adaptive"))
    }
}
