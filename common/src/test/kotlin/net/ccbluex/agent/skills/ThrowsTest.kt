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
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertNotNull

/**
 * Ender pearls thrown onto islands over the void, followed through a [TestWorld] as the game moves them.
 *
 * @agent claude-opus-5-5
 */
class ThrowsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val EYE = 1.62
    }

    /**
     * A 16x16 island and a 5x5 one 8 blocks east of it, both three blocks thick with their tops at y 1.
     */
    private fun islands() = TestWorld().fill(-8, -2, -8, 7, 0, 7).fill(16, -2, -2, 20, 0, 2)

    private fun assertLandsOn(world: TestWorld, eye: Vec3, motion: Vec3, aim: ViewRotation, to: Vec3) {
        val landing = assertNotNull(Throws.pearlLanding(world, eye, motion, aim, to.y), "no landing for $aim").at
        assertEquals(to.y, landing.y, 1.0E-9, "lands on the top")
        assertTrue(hypot(landing.x - to.x, landing.z - to.z) <= 1.0, "lands at $landing, aimed at $to")
    }

    @Test
    fun `a throw across the void comes down on the other island`() {
        val world = islands()
        val eye = Vec3(0.5, 1 + EYE, 0.5)
        val to = TestWorld.feet(18, 1, 0)
        val aim = assertNotNull(Throws.pearlRotation(world, eye, Vec3.ZERO, to))
        assertLandsOn(world, eye, Vec3.ZERO, aim, to)
    }

    @Test
    fun `falling beside an island the pearl goes up onto its top`() {
        val world = islands()
        val eye = Vec3(11.5, -3 + EYE, 0.5)
        val falling = Vec3(0.0, -0.5, 0.0)
        val to = TestWorld.feet(-3, 1, 0)
        val aim = assertNotNull(Throws.pearlRotation(world, eye, falling, to))
        assertTrue(aim.xRot < 0, "throws upwards, not at ${aim.xRot}")
        assertLandsOn(world, eye, falling, aim, to)
    }

    @Test
    fun `a pearl that hits the side of an island lands nowhere`() {
        val west = ViewRotation(90f, 0f)
        assertNull(Throws.pearlLanding(islands(), Vec3(13.5, -2 + EYE, 0.5), Vec3.ZERO, west, 1.0))
    }

    @Test
    fun `no throw gets over a wall too high to clear`() {
        val world = islands().fill(11, 1, -10, 11, 60, 10)
        assertNull(Throws.pearlRotation(world, Vec3(0.5, 1 + EYE, 0.5), Vec3.ZERO, TestWorld.feet(18, 1, 0)))
    }

    @Test
    fun `a player over the void in the way would drop the thrower there`() {
        val inTheWay = listOf(AABB(10.0, -20.0, -6.0, 13.0, 60.0, 6.0))
        val eye = Vec3(0.5, 1 + EYE, 0.5)
        assertNull(Throws.pearlRotation(islands(), eye, Vec3.ZERO, TestWorld.feet(18, 1, 0), inTheWay))
    }

    @Test
    fun `of the throws that land the quickest is taken`() {
        val world = islands()
        val eye = Vec3(0.5, 1 + EYE, 0.5)
        val to = TestWorld.feet(18, 1, 0)
        val aim = assertNotNull(Throws.pearlRotation(world, eye, Vec3.ZERO, to))
        val ticks = assertNotNull(Throws.pearlLanding(world, eye, Vec3.ZERO, aim, to.y)).ticks
        val higher = Throws.pearlLanding(world, eye, Vec3.ZERO, ViewRotation(aim.yRot, -60f), to.y)
        assertTrue(higher == null || higher.ticks > ticks, "a lob takes ${higher?.ticks} ticks, the throw $ticks")
    }
}
