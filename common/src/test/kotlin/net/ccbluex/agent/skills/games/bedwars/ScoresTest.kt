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
import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
class ScoresTest {

    private val middling = Traits(aggression = 0.5, greed = 0.5, caution = 0.5, patience = 0.5, skill = 0.5)

    /**
     * Draws no noise, so a choice is the plain scores plus taste and stickiness.
     */
    private val still = object : Random() {
        override fun nextGaussian(): Double = 0.0
    }

    @Test
    fun `gathering is always open and costs the way home`() {
        val home = scores(Facts(), middling)
        assertEquals(0.15 + 0.2 * 0.5, home.getValue(Activity.GATHER), 1e-9)
        val away = scores(Facts(away = 1.0), middling)
        assertEquals(0.15 + 0.2 * 0.5 - 0.4, away.getValue(Activity.GATHER), 1e-9)
    }

    @Test
    fun `activities without their precondition are left out`() {
        val scores = scores(Facts(), middling)
        assertEquals(setOf(Activity.GATHER), scores.keys)
    }

    @Test
    fun `covering the first layer early is worth most to a careful agent`() {
        val careful = middling.copy(caution = 0.9)
        val early = scores(Facts(seconds = 30, coverLayer = 1, openSpots = 8), careful).getValue(Activity.COVER)
        assertEquals(0.5 + 0.55 * 0.9 + 0.3, early, 1e-9)
        val late = scores(Facts(seconds = 120, coverLayer = 1, openSpots = 8), careful).getValue(Activity.COVER)
        assertEquals(early - 0.3, late, 1e-9)
    }

    @Test
    fun `an outer layer waits for its blocks and teammates share the cover`() {
        val base = 0.2 + 0.55 * 0.5
        val lacking = scores(Facts(seconds = 200, coverLayer = 2, openSpots = 12), middling)
        assertEquals(base - 0.3, lacking.getValue(Activity.COVER), 1e-9)
        val shared = scores(Facts(seconds = 200, coverLayer = 2, hasCoverBlocks = true, openSpots = 12, coverers = 2), middling)
        assertEquals(base - 0.45, shared.getValue(Activity.COVER), 1e-9)
        val room = scores(Facts(seconds = 200, coverLayer = 2, hasCoverBlocks = true, openSpots = 12, coverers = 1), middling)
        assertEquals(base, room.getValue(Activity.COVER), 1e-9)
    }

    @Test
    fun `an intruder far off makes covering more urgent and defending less`() {
        val far = scores(Facts(seconds = 200, coverLayer = 1, openSpots = 8, intruderDistance = 30.0, intruders = 1), middling)
        assertEquals(0.5 + 0.55 * 0.5 + 0.3, far.getValue(Activity.COVER), 1e-9)
        assertEquals(1.15 + 0.2 * 0.5, far.getValue(Activity.DEFEND), 1e-9)
        val near = scores(Facts(intruderDistance = 5.0, intruders = 1), middling)
        assertEquals(1.15 + 0.25 + 0.2 * 0.5, near.getValue(Activity.DEFEND), 1e-9)
    }

    @Test
    fun `enough defenders leave the next one to other things`() {
        val alone = scores(Facts(intruderDistance = 5.0, intruders = 1, defenders = 0), middling)
        val crowded = scores(Facts(intruderDistance = 5.0, intruders = 1, defenders = 2), middling)
        assertEquals(alone.getValue(Activity.DEFEND) - 0.4 - 0.2, crowded.getValue(Activity.DEFEND), 1e-9)
        val matched = scores(Facts(intruderDistance = 5.0, intruders = 1, defenders = 1), middling)
        assertEquals(alone.getValue(Activity.DEFEND) - 0.4, matched.getValue(Activity.DEFEND), 1e-9)
    }

    @Test
    fun `a rusher almost at the enemy bed would rather finish`() {
        val facts = Facts(intruderDistance = 5.0, intruders = 1, current = Activity.RUSH, almostThere = true)
        val plain = scores(facts.copy(almostThere = false), middling).getValue(Activity.DEFEND)
        assertEquals(plain - 0.2 - 0.5 * 0.5, scores(facts, middling).getValue(Activity.DEFEND), 1e-9)
    }

    @Test
    fun `gear grows with the round but wool does not`() {
        val sword = scores(Facts(seconds = 300, wishUrgency = 0.8, affordsWish = true), middling)
        assertEquals(0.8 + 0.3 + 0.05 + 0.35, sword.getValue(Activity.GEAR), 1e-9)
        val wool = scores(Facts(seconds = 300, wishUrgency = 0.85, wishIsWool = true), middling)
        assertEquals(0.85 + 0.05 - 0.15, wool.getValue(Activity.GEAR), 1e-9)
    }

    @Test
    fun `ores wait for their time and are left to one agent`() {
        val opening = scores(Facts(seconds = 10, diamondTrip = true), middling).getValue(Activity.DIAMONDS)
        assertEquals(0.2 + 0.3 - 0.35, opening, 1e-9)
        val taken = scores(Facts(seconds = 100, diamondTrip = true, diamondMates = 1), middling).getValue(Activity.DIAMONDS)
        assertEquals(0.2 + 0.3 - 0.35, taken, 1e-9)
        assertFalse(Activity.EMERALDS in scores(Facts(seconds = 100, emeraldTrip = true), middling))
        val emeralds = scores(Facts(seconds = 400, emeraldTrip = true, hasIronSword = true), middling)
        assertEquals(0.1 + 0.25 + 0.15, emeralds.getValue(Activity.EMERALDS), 1e-9)
    }

    @Test
    fun `rushing grows over the round and drops when nobody is home`() {
        val aggressive = middling.copy(aggression = 0.9)
        val opening = scores(Facts(seconds = 30, hasRushTarget = true), aggressive).getValue(Activity.RUSH)
        assertEquals(0.25 + 0.63 + 0.225 - 0.05, opening, 1e-9)
        val late = scores(Facts(seconds = 700, hasRushTarget = true), aggressive).getValue(Activity.RUSH)
        assertEquals(0.25 + 0.63 + 0.2 + 0.3, late, 1e-9)
        val alone = scores(Facts(seconds = 200, hasRushTarget = true, nobodyHome = true), aggressive).getValue(Activity.RUSH)
        assertEquals(0.25 + 0.63 - 0.35 * 0.5, alone, 1e-9)
        val stuck = scores(Facts(seconds = 200, hasRushTarget = true, rushTargetAvoided = true), aggressive)
        assertEquals(0.25 + 0.63 - 0.5, stuck.getValue(Activity.RUSH), 1e-9)
    }

    @Test
    fun `hunting is the way to go once no bed is left to rush`() {
        val noBeds = scores(Facts(hasPrey = true), middling).getValue(Activity.HUNT)
        assertEquals(0.35 + 0.25 + 0.45, noBeds, 1e-9)
        val beds = scores(Facts(hasPrey = true, hasRushTarget = true, hunters = 1), middling).getValue(Activity.HUNT)
        assertEquals(0.35 + 0.25 - 0.2, beds, 1e-9)
    }

    @Test
    fun `help is worth more for a real teammate close by`() {
        val bot = scores(Facts(brawlDistance = 10.0), middling).getValue(Activity.HELP)
        val real = scores(Facts(brawlDistance = 10.0, brawlMateIsReal = true), middling).getValue(Activity.HELP)
        assertEquals(0.6 + 0.15 + 0.1 - 0.2, bot, 1e-9)
        assertEquals(bot + 0.25, real, 1e-9)
    }

    @Test
    fun `holding the base never leaves it`() {
        val facts = Facts(
            hasRushTarget = true, hasPrey = true, diamondTrip = true, emeraldTrip = true, seconds = 400,
            strategy = Strategy.HOLD_BASE,
        )
        val scores = scores(facts, middling)
        for (away in listOf(Activity.RUSH, Activity.HUNT, Activity.DIAMONDS, Activity.EMERALDS)) {
            assertFalse(away in scores, "$away")
        }
    }

    @Test
    fun `the choice takes the best score with taste and sticks with what it does`() {
        val scores = mapOf(Activity.GATHER to 0.5, Activity.GEAR to 0.6)
        val noTaste = emptyMap<Activity, Double>()
        assertEquals(Activity.GEAR, choose(scores, noTaste, middling, null, null, still).first)
        // Gathering already: 0.5 + 0.15 + 0.3 * 0.5 beats 0.6
        assertEquals(Activity.GATHER, choose(scores, noTaste, middling, Activity.GATHER, null, still).first)
        assertEquals(Activity.GATHER, choose(scores, mapOf(Activity.GATHER to 0.2), middling, null, null, still).first)
    }

    @Test
    fun `a finished activity is left out until its time is up`() {
        val scores = mapOf(Activity.GATHER to 0.2, Activity.COVER to 0.9)
        val (best, score) = choose(scores, emptyMap(), middling, null, Activity.COVER, still)
        assertEquals(Activity.GATHER, best)
        assertEquals(0.2, score, 1e-9)
        assertEquals(Activity.GATHER, choose(emptyMap(), emptyMap(), middling, null, null, still).first)
    }

    @Test
    fun `the rush strategy makes the agent as aggressive and careless as it gets`() {
        val personality = Personality(0.3, 0.8, 0.4, 0.5, 0.5, 0.6, 1.0, FightingStyle.NONE)
        val rush = Traits.of(personality, SkillLevel.NORMAL, Strategy.RUSH)
        assertEquals(0.95, rush.aggression, 1e-9)
        assertEquals(0.05, rush.caution, 1e-9)
        assertEquals(0.4, rush.greed, 1e-9)
        val hold = Traits.of(personality, SkillLevel.NORMAL, Strategy.HOLD_BASE)
        assertEquals(0.05, hold.aggression, 1e-9)
        assertEquals(0.95, hold.caution, 1e-9)
        val adaptive = Traits.of(personality, SkillLevel.NORMAL, Strategy.ADAPTIVE)
        assertEquals(0.3, adaptive.aggression, 1e-9)
    }

    @Test
    fun `skill follows the skillLevel`() {
        val personality = Personality.BALANCED
        val beginner = Traits.of(personality, SkillLevel.BEGINNER, Strategy.ADAPTIVE).skill
        val normal = Traits.of(personality, SkillLevel.NORMAL, Strategy.ADAPTIVE).skill
        val hard = Traits.of(personality, SkillLevel.HARD, Strategy.ADAPTIVE).skill
        assertTrue(beginner < 0.3 && normal in 0.3..0.72 && hard > 0.72, "$beginner $normal $hard")
        assertTrue(Traits.of(personality, SkillLevel.HARD, Strategy.ADAPTIVE).noise < middling.noise)
        assertNull(scores(Facts(), middling)[Activity.UPGRADE])
    }
}
