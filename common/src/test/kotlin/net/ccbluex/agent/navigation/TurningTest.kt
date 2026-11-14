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

import net.ccbluex.agent.math.ViewRotation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * @agent claude-opus-5-5
 */
internal class TurningTest {

    private val turning = Turning.linear(10f)

    @Test
    fun `each axis turns at most the cap a tick`() {
        val turned = turning.turn(ViewRotation(0f, 0f), ViewRotation(90f, -45f), LookPurpose.AIM)
        assertEquals(ViewRotation(10f, -10f), turned)
    }

    @Test
    fun `the yRot turns the short way across 180`() {
        val turned = turning.turn(ViewRotation(175f, 0f), ViewRotation(-175f, 0f), LookPurpose.MOVEMENT)
        assertEquals(185f, turned.yRot, 1e-4f)
    }

    @Test
    fun `a goal closer than the cap is reached`() {
        val turned = turning.turn(ViewRotation(720f, 10f), ViewRotation(3f, 7f), LookPurpose.MOVEMENT)
        assertEquals(723f, turned.yRot, 1e-4f)
        assertEquals(7f, turned.xRot, 1e-4f)
    }

    @Test
    fun `the xRot may turn at its own pace`() {
        val turned = Turning.linear(10f, 5f).turn(ViewRotation(0f, 0f), ViewRotation(30f, 30f), LookPurpose.AIM)
        assertEquals(ViewRotation(10f, 5f), turned)
    }

    @Test
    fun `an eased turn takes a share of what is left, at most the calm pace`() {
        val eased = Turning.eased()
        assertEquals(Turning.CALM_TURN, eased.turn(ViewRotation(0f, 0f), ViewRotation(170f, 0f), LookPurpose.MOVEMENT).yRot)
        assertEquals(9f, eased.turn(ViewRotation(0f, 0f), ViewRotation(20f, 0f), LookPurpose.MOVEMENT).yRot, 1e-4f)
        assertEquals(1f, eased.turn(ViewRotation(0f, 0f), ViewRotation(1f, 0f), LookPurpose.MOVEMENT).yRot, 1e-4f)
    }

    /**
     * The BedWars GEAR flip: the look turned between the shopkeeper and the generator, about 180 degrees apart, every
     * 10 ticks, and the host turned at 180 degrees a tick (the old angle smoother): 317 ticks of over 90 degrees in
     * one run.
     */
    @Test
    fun `a host turning at 180 a tick is paced as a person turns`() {
        val host = Turning.linear(180f)
        assertEquals(180f, maxYRotStep(host, LookPurpose.MOVEMENT), 1e-3f)
        assertEquals(Turning.CALM_TURN, maxYRotStep(Turning.humanPaced(host), LookPurpose.MOVEMENT), 1e-3f)
        assertEquals(Turning.CALM_TURN, maxYRotStep(Turning.humanPaced(host), LookPurpose.AIM), 1e-3f)
        assertEquals(Turning.FLICK_TURN, maxYRotStep(Turning.humanPaced(host), LookPurpose.STEER), 1e-3f)
    }

    private fun maxYRotStep(turning: Turning, purpose: LookPurpose): Float {
        var look = ViewRotation(0f, 0f)
        var most = 0f
        for (tick in 0 until 200) {
            val goal = ViewRotation(if (tick / 10 % 2 == 0) 179f else -1f, 0f)
            val turned = turning.turn(look, goal, purpose)
            most = maxOf(most, kotlin.math.abs(turned.yRot - look.yRot))
            look = turned
        }
        return most
    }
}
