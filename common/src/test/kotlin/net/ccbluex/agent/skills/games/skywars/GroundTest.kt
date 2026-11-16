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
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The island ground under a cage that hangs above it: the island was taken at the cage's height 76 and holding
 * pillared from the ground at 67 up to it.
 *
 * @agent claude-sonnet-5-5
 */
class GroundTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    /**
     * An island whose top is at y = 66, and a cage floor at y = 75 over its middle.
     */
    private val world = TestWorld().fill(-4, 60, -4, 4, 66, 4)
    private val spawn = Vec3(0.5, 76.0, 0.5)

    @Test
    fun cageDroppedGivesTheIslandTop() {
        assertEquals(Vec3(0.5, 67.0, 0.5), world.terrain().islandGround(spawn))
    }

    @Test
    fun cageFloorIsNotTheIsland() {
        val caged = TestWorld().fill(-4, 60, -4, 4, 66, 4).fill(-1, 75, -1, 1, 75, 1)
        assertEquals(Vec3(0.5, 76.0, 0.5), caged.terrain().islandGround(spawn))
    }

    @Test
    fun spawnOnTheGroundStaysThere() {
        assertEquals(Vec3(0.5, 67.0, 0.5), world.terrain().islandGround(Vec3(0.5, 67.0, 0.5)))
    }

    @Test
    fun noGroundUnderTheSpawnIsNull() {
        assertNull(TestWorld().terrain().islandGround(spawn))
    }
}
