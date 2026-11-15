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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Setbacks of a server's anti-cheat: from about (55.6, 73, -27.2) three times along a pit, and a stair ramp climbed
 * four times and set back a little further up each time.
 *
 * @agent claude-opus-5-5
 */
internal class SetbacksTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val setbacks = Setbacks()

    @Test
    fun `the second setback at one spot is a refusal`() {
        assertTrue(setbacks.refused(Vec3(55.56, 73.0, -27.28), 757).isEmpty())
        assertEquals(setOf(BlockPos(55, 73, -28)), setbacks.refused(Vec3(55.61, 73.0, -27.16), 761))
    }

    @Test
    fun `a climb set back further up each time is one spot`() {
        val climb = listOf(
            744L to Vec3(39.90, 89.25, -17.29),
            754L to Vec3(40.06, 89.02, -16.38),
            764L to Vec3(39.89, 89.50, -15.96),
            774L to Vec3(38.97, 90.03, -15.16),
        )
        val spots = climb.map { (tick, at) -> setbacks.refused(at, tick) }
        assertTrue(spots.first().isEmpty())
        assertEquals(4, spots.last().size)
    }

    @Test
    fun `setbacks far apart in place or time are not`() {
        assertTrue(setbacks.refused(Vec3(55.56, 73.0, -27.28), 757).isEmpty())
        assertTrue(setbacks.refused(Vec3(42.7, 67.0, -28.5), 770).isEmpty())
        assertTrue(setbacks.refused(Vec3(42.6, 67.4, -28.5), 1401).isEmpty())
    }

    /**
     * A server sent 302 position packets in one tick, all to one spot.
     */
    @Test
    fun `position packets of one tick are one setback`() {
        repeat(302) { assertTrue(setbacks.refused(Vec3(-4.53, 96.0, -58.36), 5133).isEmpty()) }
        assertEquals(setOf(BlockPos(-5, 96, -59)), setbacks.refused(Vec3(-4.6, 96.0, -58.3), 5140))
    }

    @Test
    fun `stairs the right way up are walked up, upside down ones jumped`() {
        val stairs = Blocks.RED_SANDSTONE_STAIRS.defaultBlockState()
        assertTrue(isWalkableStair(stairs.setValue(StairBlock.HALF, Half.BOTTOM)))
        assertFalse(isWalkableStair(stairs.setValue(StairBlock.HALF, Half.TOP)))
        assertFalse(isWalkableStair(Blocks.STONE.defaultBlockState()))
    }
}
