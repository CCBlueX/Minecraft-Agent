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
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.games.BLOCKS_AROUND_ORIGIN
import net.ccbluex.agent.skills.games.FakeRoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.playerNamed
import net.ccbluex.agent.skills.games.scanNow
import net.ccbluex.agent.skills.loot.Chests
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sky Wars senses over a flat map with one chest, the agent standing on it.
 *
 * @agent claude-sonnet-5-5
 */
internal class LevelSensesTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val level = TestLevel(TestWorld.flat().set(5, 1, 5, Blocks.CHEST.defaultBlockState()))
    private val facts = FakeRoundFacts()
    private val chests = Chests()
    private val senses = LevelSenses(level.toAgent(), facts, chests, BLOCKS_AROUND_ORIGIN)

    init {
        level.playerNamed("Me", TestWorld.feet(0, 1, 0))
    }

    @Test
    fun chestsAreThoseOfTheLevelAndRefillsComeFromTheHost() {
        senses.tick()
        assertEquals(listOf(BlockPos(5, 1, 5)), senses.chests.toList())
        assertEquals(0, senses.refills)

        chests.secondsToRefill = 90
        chests.refilled()
        assertEquals(1, senses.refills)
        assertEquals(90, senses.secondsToRefill)
    }

    @Test
    fun doomIsTheBorderClosingInDuringTheRound() {
        level.worldBorder.lerpSizeBetween(200.0, 100.0, 60_000L, 0L)
        facts.state = RoundState.COUNTDOWN
        senses.tick()
        assertFalse(senses.isDoom)

        facts.state = RoundState.PLAYING
        senses.scanNow()
        assertTrue(senses.isDoom)
    }

    @Test
    fun cagesMiddleAndSpawnAreUnknownToAClient() {
        senses.scanNow()
        assertTrue(senses.cages.isEmpty())
        assertNull(senses.middle)
        assertNull(senses.ownSpawn)
    }
}
