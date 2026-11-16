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

import net.ccbluex.agent.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Chests given up on together (four middle chests were each tried for 50 ticks from the same island edge, the agent
 * standing still for 200 ticks).
 *
 * @agent claude-sonnet-5-5
 */
class LootingTest {

    private val blocked = BlockPos(-1, 71, 11)
    private val beside = BlockPos(3, 71, 13)
    private val chests = listOf(
        blocked, beside, BlockPos(-2, 71, 19), BlockPos(-1, 75, 11), BlockPos(21, 76, 16),
    )

    @Test
    fun chestsAroundABlockedOneAreGivenUpWithIt() {
        assertEquals(listOf(blocked, beside), sharingGround(blocked, chests))
    }

    @Test
    fun otherIslandsAndLevelsAreNotAffected() {
        assertEquals(listOf(chests[4]), sharingGround(chests[4], chests))
        assertEquals(listOf(chests[3]), sharingGround(chests[3], chests))
    }
}
