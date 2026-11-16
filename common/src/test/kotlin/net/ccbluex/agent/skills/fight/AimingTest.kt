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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.SkillLevel
import java.lang.reflect.Proxy
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
class AimingTest {

    @Test
    fun `rotation follows Minecraft yRot and xRot`() {
        val eye = Vec3.ZERO
        assertEquals(0f, Aiming.rotation(eye, Vec3(0.0, 0.0, 5.0)).yRot, 1e-4f)
        assertEquals(90f, Aiming.rotation(eye, Vec3(-5.0, 0.0, 0.0)).yRot, 1e-4f)
        assertEquals(-90f, Aiming.rotation(eye, Vec3(5.0, 0.0, 0.0)).yRot, 1e-4f)
        assertEquals(-45f, Aiming.rotation(eye, Vec3(0.0, 5.0, 5.0)).xRot, 1e-4f)
    }

    @Test
    fun `jitter puts the noise on the goal, half of it on the xRot`() {
        val jittered = Aiming.jitter(ViewRotation(10f, 20f), noise = 2.8, yRotNoise = 1.0, xRotNoise = 1.0)
        assertEquals(12.8f, jittered.yRot, 1e-4f)
        assertEquals(21.4f, jittered.xRot, 1e-4f)
    }

    @Test
    fun `jitter keeps the xRot on the sphere`() {
        assertEquals(90f, Aiming.jitter(ViewRotation(0f, 89f), noise = 4.0, yRotNoise = 0.0, xRotNoise = 3.0).xRot)
    }

    @Test
    fun `a quick look is a flick the body lands now, a slow one an aim`() {
        val fight = fight(ViewRotation.ZERO)
        fight.lookAt(ViewRotation(90f, 0f), 4.0)
        assertEquals(LookPurpose.STEER, fight.lookPurpose)
        fight.lookAt(ViewRotation(90f, 0f), 1.0)
        assertEquals(LookPurpose.AIM, fight.lookPurpose)
    }

    @Test
    fun `aim checks read the rotation the server has`() {
        var sent = ViewRotation(0f, 0f)
        val fight = fight { sent }
        assertTrue(fight.aimed(ViewRotation(2f, 0f), 3.0))
        sent = ViewRotation(45f, 0f)
        assertFalse(fight.aimed(ViewRotation(2f, 0f), 3.0))
        assertTrue(fight.aimedXRot(0f, 1.0))
    }

    private fun fight(sent: ViewRotation) = fight { sent }

    /**
     * A fight on a body that only knows the rotation the server has.
     */
    private fun fight(sent: () -> ViewRotation): BuiltInFight {
        val type = AgentBody::class.java
        val body = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            check(method.name == "getSentRotation") { "The aim checks only read the sent rotation: ${method.name}" }
            sent()
        } as AgentBody
        return BuiltInFight(body, SkillLevel.NORMAL, Personality.BALANCED, Rules.DEFAULT, Random(1))
    }

    @Test
    fun `hit tolerance widens up close`() {
        assertEquals(Math.toDegrees(atan2(0.45, 3.0)) + 8, Aiming.hitTolerance(3.0), 1e-9)
        assertEquals(Aiming.hitTolerance(0.5), Aiming.hitTolerance(0.1), 1e-9)
        assertTrue(Aiming.hitTolerance(1.0) > Aiming.hitTolerance(3.0))
    }

    @Test
    fun `aimed compares both axes`() {
        assertTrue(Aiming.aimed(ViewRotation(179f, 0f), ViewRotation(-179f, 2f), 3.0))
        assertFalse(Aiming.aimed(ViewRotation(0f, 0f), ViewRotation(0f, 5f), 3.0))
    }

    @Test
    fun `throw takes the low arc that lands on the point`() {
        val eye = Vec3.ZERO
        val point = Vec3(0.0, 0.0, 20.0)
        val speed = 1.5
        val gravity = 0.03
        val rotation = Aiming.throwAt(eye, point, Vec3.ZERO, speed, gravity)
        assertEquals(0f, rotation.yRot, 1e-4f)
        assertTrue(rotation.xRot < 0, "throws upwards")
        // Flying the drag-free parabola, the projectile comes down at the point's height over the point
        val radians = Math.toRadians(-rotation.xRot.toDouble())
        val vx = speed * kotlin.math.cos(radians)
        val vy = speed * kotlin.math.sin(radians)
        val time = point.z / vx
        assertEquals(0.0, vy * time - gravity * time * time / 2, 1e-6)
    }

    @Test
    fun `throw leads a moving target horizontally`() {
        val led = Aiming.throwAt(Vec3.ZERO, Vec3(0.0, 0.0, 15.0), Vec3(0.2, 0.0, 0.0), 1.5, 0.03)
        // 10 ticks of flight at 0.2 blocks a tick: aims 2 blocks to the side, towards negative yaw (east)
        assertEquals(Math.toDegrees(atan2(-2.0, 15.0)).toFloat(), led.yRot, 1e-3f)
    }

    @Test
    fun `throw out of range goes straight`() {
        val far = Aiming.throwAt(Vec3.ZERO, Vec3(0.0, 0.0, 400.0), Vec3.ZERO, 1.1, 0.03)
        assertEquals(Aiming.rotation(Vec3.ZERO, Vec3(0.0, 0.0, 400.0)).xRot, far.xRot, 1e-4f)
        assertTrue(abs(far.xRot) < 1e-4f)
    }
}
