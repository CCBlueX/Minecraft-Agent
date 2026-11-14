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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The GEAR loop: a failed purchase sent the agent to its generator for a tick and back to the shop the next, 579 goal
 * switches in 5.6 minutes. After a failure the shop waits out the shop's own retry time.
 *
 * @agent claude-opus-5-5
 */
class ShopVisitsTest {

    @Test
    fun `after a failed purchase the agent stays away for the retry time`() {
        val visits = ShopVisits()
        visits.failed(1000)
        val goals = (1001L..1200L).map { visits.mayVisit(it) }
        val switches = goals.zipWithNext().count { (a, b) -> a != b }
        assertEquals(1, switches)
        assertEquals(ShopVisits.AWAY_TICKS.toInt() - 1, goals.count { !it })
    }

    @Test
    fun `without a failure the shop may be visited`() {
        assertEquals(true, ShopVisits().mayVisit(0))
    }
}
