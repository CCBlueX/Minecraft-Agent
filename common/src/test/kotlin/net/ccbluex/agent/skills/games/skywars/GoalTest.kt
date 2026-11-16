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
package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The goal of a hunt over a bridge: it stays put while the enemy wanders off a few blocks, so the route is not
 * searched again every few ticks (routes 44..48 in 50 ticks while standing still on a half built bridge).
 *
 * @agent claude-sonnet-5-5
 */
class GoalTest {

    private val from = Vec3(24.0, 85.0, 18.0)
    private val held = Vec3(2.4, 71.0, 17.3)

    @Test
    fun farEnemyThatMovedALittleKeepsTheGoal() {
        assertEquals(held, followGoal(held, Vec3(4.0, 71.5, 20.0), from))
    }

    @Test
    fun enemyThatMovedFarGetsANewGoal() {
        val moved = Vec3(9.0, 72.0, 24.0)
        assertEquals(moved, followGoal(held, moved, from))
    }

    @Test
    fun closeEnemyIsFollowedExactly() {
        val near = Vec3(30.0, 85.0, 18.0)
        assertEquals(near, followGoal(Vec3(31.0, 85.0, 18.0), near, from))
    }

    @Test
    fun firstTickHasNoGoalYet() {
        assertEquals(held, followGoal(null, held, from))
    }
}
