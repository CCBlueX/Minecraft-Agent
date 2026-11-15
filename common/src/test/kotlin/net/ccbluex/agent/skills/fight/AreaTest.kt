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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boxes by block coordinates and spheres by distance.
 *
 * @agent claude-opus-5-5
 */
class AreaTest {

    @Test
    fun `box takes its corners in either order`() {
        val forward = Area.box(BlockPos(0, 60, 0), BlockPos(10, 70, 10))
        val backward = Area.box(BlockPos(10, 70, 10), BlockPos(0, 60, 0))
        val mixed = Area.box(BlockPos(10, 60, 0), BlockPos(0, 70, 10))
        for (area in listOf(forward, backward, mixed)) {
            assertTrue(area.contains(Vec3(5.0, 65.0, 5.0)))
            assertFalse(area.contains(Vec3(11.5, 65.0, 5.0)))
        }
    }

    @Test
    fun `box includes both corner blocks whole`() {
        val area = Area.box(BlockPos(0, 60, 0), BlockPos(10, 70, 10))
        assertTrue(area.contains(Vec3(0.0, 60.0, 0.0)))
        assertTrue(area.contains(Vec3(10.99, 70.99, 10.99)))
        assertFalse(area.contains(Vec3(11.0, 65.0, 5.0)))
        assertFalse(area.contains(Vec3(-0.01, 65.0, 5.0)))
        assertFalse(area.contains(Vec3(5.0, 59.99, 5.0)))
    }

    @Test
    fun `box counts negative coordinates by the block they are in`() {
        val area = Area.box(BlockPos(-5, 0, -5), BlockPos(-1, 0, -1))
        assertTrue(area.contains(Vec3(-0.5, 0.5, -0.5)))
        assertTrue(area.contains(Vec3(-5.0, 0.0, -5.0)))
        assertFalse(area.contains(Vec3(-5.01, 0.5, -3.0)))
        assertFalse(area.contains(Vec3(0.0, 0.5, -3.0)))
    }

    @Test
    fun `sphere holds what is within the radius`() {
        val area = Area.sphere(Vec3(0.0, 64.0, 0.0), 5.0)
        assertTrue(area.contains(Vec3(3.0, 64.0, 4.0)))
        assertTrue(area.contains(Vec3(0.0, 64.0, 0.0)))
        assertFalse(area.contains(Vec3(3.0, 64.0, 4.01)))
    }
}
