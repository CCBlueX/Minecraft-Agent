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

package net.ccbluex.agent.skills.games.blitz

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.games.BLOCKS_AROUND_ORIGIN
import net.ccbluex.agent.skills.games.FakeRoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.playerNamed
import net.ccbluex.agent.skills.loot.Chests
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Blitz senses over a flat map with one chest.
 *
 * @agent claude-sonnet-5-5
 */
internal class LevelSensesTest {

    companion object {
        init {
            TestInventories.bootstrap()
        }
    }

    private class FakeFacts : FakeRoundFacts(), LevelSenses.Facts {
        override var kitDelaySeconds: Int? = 30
        override var secondsToDeathmatch: Int? = null
        override var isDeathmatchStarted = false
    }

    private val level = TestLevel(TestWorld.flat().set(5, 1, 5, Blocks.CHEST.defaultBlockState()))
    private val facts = FakeFacts()
    private val chests = Chests()
    private val senses = LevelSenses(level.toAgent(), facts, chests, BLOCKS_AROUND_ORIGIN)
    private val me = level.playerNamed("Me", TestWorld.feet(0, 1, 0))

    @Test
    fun timersComeFromTheTextAndChestsFromTheLevel() {
        senses.tick()
        facts.secondsToDeathmatch = 120
        assertEquals(listOf(BlockPos(5, 1, 5)), senses.chests.toList())
        assertEquals(30, senses.kitDelaySeconds)
        assertEquals(120, senses.secondsToDeathmatch)
    }

    @Test
    fun compassTargetNeedsACompassAndAMovedSpawn() {
        facts.state = RoundState.LOBBY
        senses.tick()
        facts.state = RoundState.PLAYING
        me.inventory.add(ItemStack(Items.COMPASS))
        repeat(21) { senses.tick() }
        assertNull(senses.compassTarget)
    }

    @Test
    fun compassTargetIsTheSpawnOfARoundAlreadyUnderway() {
        me.inventory.add(ItemStack(Items.COMPASS))
        senses.tick()
        assertEquals(level.respawnData.pos().toAgent(), senses.compassTarget)
    }
}
