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

package net.ccbluex.agent.pathfinder.terrain

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.TestWorld.Companion.flat
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Vec3
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * @agent claude-sonnet-5-5
 */
class ReachTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        // The block at 0 10 0, out of reach from the floor at y = 1
        private val TARGET = AABB(0.0, 10.0, 0.0, 1.0, 11.0, 1.0)
        private const val REACH = 4.5
    }

    @Test
    fun `positions need support by default`() {
        val terrain = flat().terrain()
        assertTrue(terrain.reachPositions(TARGET, REACH, feet(0, 1, 0)).isEmpty())
    }

    @Test
    fun `positions in the air without support`() {
        val terrain = flat().terrain()
        val positions = terrain.reachPositions(TARGET, REACH, feet(0, 1, 0), requiresSupport = false)
        assertTrue(positions.isNotEmpty())
        assertTrue(positions.none { terrain.isStandable(it) })
        assertTrue(positions.all { terrain.isClear(terrain.box(Vec3.atBottomCenterOf(it))) })
    }
}
