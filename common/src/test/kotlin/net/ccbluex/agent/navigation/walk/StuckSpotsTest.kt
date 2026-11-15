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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A bot got stuck before (9, 92, -80) every 6 ticks for 3.7 minutes while it chased a player. Each time the stuck
 * spots changed version, which restarted the route search; a search takes 20 ticks or more, so none finished and the
 * walk kept its old route.
 *
 * @agent claude-opus-5-5
 */
internal class StuckSpotsTest {

    private val spots = StuckSpots()
    private val spot = BlockPos(9, 92, -80)

    @Test
    fun `getting stuck at the same spot again keeps the running search`() {
        spots.tick(Vec3(-11.68, 94.0, -13.47))
        assertTrue(spots.add(spot))
        val version = spots.version
        repeat(90) {
            spots.tick(Vec3(-11.68, 94.0, -13.47))
            assertFalse(spots.add(spot))
        }
        assertEquals(version, spots.version)
    }

    @Test
    fun `a new spot is new`() {
        spots.add(spot)
        val version = spots.version
        assertTrue(spots.add(spot.east()))
        assertEquals(version + 1, spots.version)
    }
}
