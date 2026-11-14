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

package net.ccbluex.agent.navigation

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @agent claude-opus-5-5
 */
internal class WalkTest {

    private val goal = Walk.Fixed(Vec3.ZERO, 1.0)

    @Test
    fun `a walk to a goal ends on the tick it arrives`() {
        val walk = Walk(goal, endsOnArrival = true, null)
        assertNull(walk.tick(arrived = false, stranded = false))
        assertEquals(Ending.ARRIVED, walk.tick(arrived = true, stranded = false))
    }

    @Test
    fun `a walk ends unreachable after exactly the stranded ticks in a row`() {
        val walk = Walk(goal, endsOnArrival = true, null)
        repeat(Navigator.UNREACHABLE_TICKS - 1) { assertNull(walk.tick(arrived = false, stranded = true)) }
        assertNull(walk.tick(arrived = false, stranded = false), "A tick with a way starts the count again")
        repeat(Navigator.UNREACHABLE_TICKS - 1) { assertNull(walk.tick(arrived = false, stranded = true)) }
        assertEquals(Ending.UNREACHABLE, walk.tick(arrived = false, stranded = true))
    }

    @Test
    fun `a followed source never ends by itself`() {
        val walk = Walk(goal, endsOnArrival = false, null)
        assertNull(walk.tick(arrived = true, stranded = false))
        repeat(Navigator.UNREACHABLE_TICKS * 2) { assertNull(walk.tick(arrived = false, stranded = true)) }
    }

    @Test
    fun `the callback hears how the walk ended`() {
        val heard = ArrayList<Ending>()
        val walk = Walk(goal, endsOnArrival = true) { heard += it }
        walk.end(Ending.STOPPED)
        assertEquals(listOf(Ending.STOPPED), heard)
    }
}
