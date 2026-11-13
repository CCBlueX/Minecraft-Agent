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

import net.ccbluex.agent.skills.SkillLevel
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @agent claude-opus-5-5
 */
class AdaptiveSkillLevelTest {

    private val player = UUID.randomUUID()

    @Test
    fun `a player's win makes the next duel harder, a loss easier, per mode`() {
        val adaptive = AdaptiveSkillLevel()
        assertEquals(SkillLevel.EASY, adaptive.skillLevel(player, "sword"))
        adaptive.record(player, "sword", SkillLevel.EASY, Outcome.LOST)
        assertEquals(SkillLevel.NORMAL, adaptive.skillLevel(player, "sword"))
        assertEquals(SkillLevel.EASY, adaptive.skillLevel(player, "axe"))
        adaptive.record(player, "sword", SkillLevel.NORMAL, Outcome.WON)
        assertEquals(SkillLevel.EASY, adaptive.skillLevel(player, "sword"))
        adaptive.record(player, "sword", SkillLevel.EASY, Outcome.DRAW)
        assertEquals(SkillLevel.EASY, adaptive.skillLevel(player, "sword"))
    }

    @Test
    fun `the rating stays within the levels`() {
        val adaptive = AdaptiveSkillLevel(SkillLevel.HARD)
        adaptive.record(player, "sumo", SkillLevel.HARD, Outcome.LOST)
        assertEquals(SkillLevel.HARD, adaptive.skillLevel(player, "sumo"))
        adaptive.record(player, "sumo", SkillLevel.BEGINNER, Outcome.WON)
        assertEquals(SkillLevel.HARD, adaptive.skillLevel(player, "sumo"))
        adaptive.forget(player)
        assertEquals(SkillLevel.HARD, adaptive.skillLevel(player, "sumo"))
    }
}
