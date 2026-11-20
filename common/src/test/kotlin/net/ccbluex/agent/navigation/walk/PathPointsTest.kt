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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.completePath
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import net.ccbluex.agent.pathfinder.walkAbilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @agent claude-opus-5-5
 */
class PathPointsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val LIMITS = Limits(maxLength = 32.0, maxComputeNanos = Long.MAX_VALUE)
    }

    /**
     * A route searched while the player walked on was joined at the top of its first pillar block, one block above the
     * player: the walk took the second pillar step from the ground, jumped without placing, and searched again from the
     * air, over and over.
     */
    @Test
    fun `a route is never joined past blocks it still has to place`() {
        val start = Vec3(0.9, 1.0, 0.5)
        val search = RouteSearch(
            TestWorld.flat().terrain(),
            start,
            NearDestination(feet(0, 4, 0), 0.5),
            walkAbilities(canPlace = true),
            LIMITS,
        )
        val route = completePath(search.advanceToEnd())
        assertEquals(Move.PILLAR, route.steps.first().move)

        assertEquals(0, route.joinIndex(Vec3(0.3, 1.0, 0.5)))
    }

}
