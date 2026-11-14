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

package net.ccbluex.agent.skills

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @agent claude-opus-5-5
 */
class SkillLevelTest {

    private val from = ViewRotation(0f, 0f)
    private val far = ViewRotation(170f, 80f)

    @Test
    fun `aims turn at 25 times the aim, the xRot at 0_7 of it`() {
        val turned = SkillLevel.NORMAL.turning().turn(from, far, LookPurpose.AIM)
        assertEquals(15f, turned.yRot, 1e-4f)
        assertEquals(10.5f, turned.xRot, 1e-4f)
    }

    @Test
    fun `walking turns at the walk pace`() {
        val turned = SkillLevel.NORMAL.turning(walkDegrees = 30f).turn(from, far, LookPurpose.MOVEMENT)
        assertEquals(ViewRotation(30f, 30f), turned)
    }

    @Test
    fun `flicks turn four times as fast as aims, never faster than walking`() {
        val beginner = SkillLevel.BEGINNER.turning(walkDegrees = 100f).turn(from, far, LookPurpose.STEER)
        assertEquals(40f, beginner.yRot, 1e-4f)
        val hard = SkillLevel.HARD.turning().turn(from, far, LookPurpose.STEER)
        assertEquals(40f, hard.yRot, 1e-4f)
    }
}
