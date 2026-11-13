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

import net.ccbluex.agent.skills.games.RoundState
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * A Sky Wars game mode before its first tick, on senses a host would give.
 *
 * @agent claude-opus-5-5
 */
class SkyWarsTest {

    /**
     * A solo round about to start: four cages around a middle, two chests on each island.
     */
    private class FakeSenses : Senses {
        override val state = RoundState.COUNTDOWN
        override val countdownSeconds: Int? = 5
        override val matchSeconds = 0
        override fun isOpponent(entity: Entity) = true
        override fun isTeammate(entity: Entity) = false
        override val isSpectator = false
        override val ownSpawn: Vec3? = Vec3(30.5, 65.0, 0.5)
        override val cages = listOf(Vec3(30.5, 65.0, 0.5), Vec3(-29.5, 65.0, 0.5), Vec3(0.5, 65.0, 30.5))
        override val middle: Vec3? = Vec3(0.5, 70.0, 0.5)
        override val chests = cages.flatMap {
            val cage = BlockPos.containing(it)
            listOf(cage.offset(2, 0, 0), cage.offset(-2, 0, 0))
        }
        override val refills = 0
        override val secondsToRefill: Int? = 120
        override val isDoom = false
    }

    @Test
    fun waitsBeforeItsFirstTick() {
        val senses = FakeSenses()
        val game = SkyWars(senses)
        assertEquals("SkyWars", game.name)
        assertEquals("Waiting", game.phase)
        assertNull(game.goal)
        assertSame(senses, game.senses)
    }
}
