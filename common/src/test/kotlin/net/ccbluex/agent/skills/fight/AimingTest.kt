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

import net.ccbluex.agent.math.ViewRotation
import net.minecraft.world.phys.Vec3
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
    fun `step turns at most the speed, xRot at 0_7 of it`() {
        val turned = Aiming.step(
            ViewRotation(0f, 0f), ViewRotation(90f, 60f), speed = 15.0, noise = 0.0, yRotNoise = 0.0, xRotNoise = 0.0,
        )
        assertEquals(15f, turned.yRot, 1e-4f)
        assertEquals(10.5f, turned.xRot, 1e-4f)
    }

    @Test
    fun `step takes the short way round and stops on the goal`() {
        val turned = Aiming.step(
            ViewRotation(170f, 0f), ViewRotation(-170f, 0f), speed = 25.0, noise = 0.0, yRotNoise = 0.0, xRotNoise = 0.0,
        )
        assertEquals(-170f, turned.yRot, 1e-3f)
    }

    @Test
    fun `step adds the noise, half of it to xRot`() {
        val turned = Aiming.step(
            ViewRotation(0f, 0f), ViewRotation(0f, 0f), speed = 15.0, noise = 2.8, yRotNoise = 1.0, xRotNoise = 1.0,
        )
        assertEquals(2.8f, turned.yRot, 1e-4f)
        assertEquals(1.4f, turned.xRot, 1e-4f)
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
