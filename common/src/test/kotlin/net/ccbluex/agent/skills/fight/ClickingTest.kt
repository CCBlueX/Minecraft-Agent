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

import net.ccbluex.agent.skills.SkillLevel
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Click timing under 1.8 rules and the reach as the victim sees it.
 *
 * @agent claude-opus-5-5
 */
class ClickingTest {

    @Test
    fun `click interval follows the skillLevel's clicks per second`() {
        val normal = Rules.DEFAULT.cps * SkillLevel.NORMAL.cps
        val hard = Rules.DEFAULT.cps * SkillLevel.HARD.cps
        assertEquals(3L, Striking.interval(normal, 0.0))
        assertEquals(2L, Striking.interval(hard, 0.0))
        assertEquals(6L, Striking.interval(Rules.DEFAULT.cps * SkillLevel.BEGINNER.cps, 0.0))
    }

    @Test
    fun `click interval spreads by a fifth per standard deviation and never drops under a tick`() {
        assertEquals(4L, Striking.interval(6.0, 1.0))
        assertEquals(3L, Striking.interval(6.0, -1.0))
        assertEquals(1L, Striking.interval(20.0, -4.0))
        assertEquals(200L, Striking.interval(0.0, 0.0))
    }

    @Test
    fun `swing reach is the skillLevel's share of the weapon reach`() {
        assertEquals(2.4, 3.0 * SkillLevel.BEGINNER.reach, 1e-9)
        assertEquals(2.94, 3.0 * SkillLevel.HARD.reach, 1e-9)
    }

    @Test
    fun `players see the fighter a ping and a few ticks late, half a second at most`() {
        assertEquals(100L, EyeTrail.viewLag(0))
        assertEquals(150L, EyeTrail.viewLag(50))
        assertEquals(500L, EyeTrail.viewLag(900))
        assertEquals(100L, EyeTrail.viewLag(-5))
    }

    @Test
    fun `the eye trail gives where the eyes were that long ago`() {
        val trail = EyeTrail()
        assertNull(trail.at(0, 100))
        for (tick in 0..20) trail.record(tick * 50L, Vec3(tick.toDouble(), 0.0, 0.0))
        // At 1000 ms, 150 ms ago is 850 ms: the sample of tick 17
        assertEquals(17.0, trail.at(1000, 150)?.x)
        assertEquals(20.0, trail.at(1000, 0)?.x)
        // Older than the trail keeps: its oldest sample
        assertEquals(0.0, trail.at(1000, 5000)?.x)
    }

    @Test
    fun `the eye trail keeps one second`() {
        val trail = EyeTrail()
        for (tick in 0..60) trail.record(tick * 50L, Vec3(tick.toDouble(), 0.0, 0.0))
        assertEquals(40.0, trail.at(3000, 5000)?.x)
    }
}
