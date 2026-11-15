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

package net.ccbluex.agent.skills.fight

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A hard bot against a beginner on a 25-block island over the void: it had circled to the outside of its rival,
 * jumped for a crit 0.6 blocks from the east edge with its back to the void, and the rival's sprint hit carried it
 * 6 blocks off.
 *
 * @agent claude-opus-5-5
 */
class FootworkTest {

    private fun hasGround(point: Vec3) = Mth.floor(point.x) in 20468..20492 && Mth.floor(point.z) in 756..780

    private val edge = Vec3(20492.372, 100.0, 772.193)
    private val rival = Vec3(20489.781, 100.827, 773.301)

    @Test
    fun `a sprint hit knocks the fighter off with its back to the edge, not in the middle`() {
        assertTrue(Footwork.canKnockOff(rival, edge, ::hasGround))
        assertFalse(Footwork.canKnockOff(Vec3(20489.0, 100.0, 772.0), Vec3(20486.0, 100.0, 772.0), ::hasGround))
    }

    @Test
    fun `the middle of the ground lies inland of the edge`() {
        val centre = assertNotNull(Footwork.groundCentre(edge, ::hasGround))
        assertTrue(centre.x < edge.x - 2, "inland: $centre")
    }
}
