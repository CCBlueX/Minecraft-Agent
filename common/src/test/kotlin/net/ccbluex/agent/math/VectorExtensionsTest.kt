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

package net.ccbluex.agent.math

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Where a hand clicks a face. A bot held yaw 180.0 while it bridged, standing on the line through the middle of the
 * face it clicked, and the server's anti-cheat banned the account.
 *
 * @agent claude-opus-5-5
 */
class VectorExtensionsTest {

    private val block = BlockPos(0, 64, 1)
    private val middle = Vec3(0.5, 64.5, 1.0)

    @Test
    fun `a click from the line through the middle of a face is never along the axis`() {
        val eye = Vec3(0.5, 66.12, 0.3)
        assertEquals(0f, middle.subtract(eye).yRot, 1.0E-4f)
        for (seed in 0L until 200L) {
            val yRot = clickPoint(block, Direction.NORTH, seed).subtract(eye).yRot
            assertTrue(abs(yRot) >= 1f, "seed $seed looks at yaw $yRot")
        }
    }

    @Test
    fun `the click stays on the face, off its middle and its edges`() {
        for (seed in 0L until 200L) {
            val point = clickPoint(block, Direction.NORTH, seed)
            assertEquals(1.0, point.z, "seed $seed")
            for ((at, mid) in listOf(point.x to middle.x, point.y to middle.y)) {
                assertTrue(abs(at - mid) in 0.08..0.35, "seed $seed at $point")
            }
        }
    }

    @Test
    fun `the same seed clicks the same spot, another block another one`() {
        assertEquals(clickPoint(block, Direction.UP, 7L), clickPoint(block, Direction.UP, 7L))
        val offsets = (0 until 8).map { step ->
            val pos = block.east(step)
            clickPoint(pos, Direction.UP, 7L xor pos.asLong()).subtract(Vec3.atLowerCornerOf(pos))
        }
        assertNotEquals(1, offsets.distinct().size)
    }
}
