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

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * When a way counts as leading nowhere. While hunting, the agent stood at the end of its bridge for 105 s while its
 * prey ran about out of reach, and every move of the prey started the stall over.
 *
 * @agent claude-opus-5-5
 */
class TravelTest {

    private val bridgeEnd = Vec3(65.5, 89.0, 48.33)
    private val prey = listOf(Vec3(36.94, 89.0, 80.73), Vec3(33.72, 89.0, 82.5), Vec3(37.72, 89.0, 74.91))

    /**
     * The first tick [isStalled] holds, null within [ticks].
     */
    private fun stallTick(ticks: Int, position: (Int) -> Vec3, goal: (Int) -> Vec3): Int? {
        val travel = Travel()
        return (0..ticks).firstOrNull { travel.isStalled(position(it), goal(it), it.toLong()) }
    }

    @Test
    fun `standing still while the prey runs about stalls`() {
        assertEquals(Travel.STALL_TICKS + 1, stallTick(400, { bridgeEnd }) { prey[it / 10 % prey.size] })
    }

    @Test
    fun `bridging after a running prey goes on`() {
        val way = { tick: Int -> bridgeEnd.add(-0.1 * tick, 0.0, 0.1 * tick) }
        assertNull(stallTick(400, way) { prey[it / 10 % prey.size] })
    }

    @Test
    fun `walking about without getting closer to a fixed goal stalls`() {
        val circle = { tick: Int -> bridgeEnd.add(3 * Math.cos(tick / 10.0), 0.0, 3 * Math.sin(tick / 10.0)) }
        assertNotNull(stallTick(400, circle) { prey[0] })
    }
}
