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

package net.ccbluex.agent.pathfinder.glide

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * From LiquidBounce: [ElytraTravelTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/ElytraTravelTest.kt#L35-L68)
 *
 * @agent claude-opus-5-5
 */
class ElytraTravelTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val ROCKET_TICKS = 20
    }

    @Test
    fun `a far cruise needs more rockets than a near one`() {
        val near = Cruise.rocketsNeeded(300.0, 10.0, ROCKET_TICKS)
        val far = Cruise.rocketsNeeded(1200.0, 10.0, ROCKET_TICKS)
        val high = Cruise.rocketsNeeded(300.0, 80.0, ROCKET_TICKS)

        assertTrue(far > near * 3, "1200 blocks took $far rockets, 300 took $near")
        assertTrue(high > near, "climbing 80 blocks took no extra rockets")
        assertEquals(Int.MAX_VALUE, Cruise.rocketsNeeded(300.0, 0.0, 0))
    }

    @Test
    fun `the cruise climbs over what is on the line and stays higher over unloaded columns`() {
        val start = Vec3(0.5, 100.0, 0.5)
        val goal = Vec3(300.5, 110.0, 0.5)
        val pillar = ColumnHeights { x, _ -> if (x in 150..152) 160 else 0 }
        val unloaded = ColumnHeights { x, _ -> if (x > 50) null else 0 }

        assertEquals(160.0 + Cruise.CLEARANCE, Cruise.cruiseAltitude(start, goal, pillar))
        assertEquals(
            110.0 + Cruise.CLEARANCE + Cruise.UNKNOWN_CLEARANCE,
            Cruise.cruiseAltitude(start, goal, unloaded),
        )
    }

}
