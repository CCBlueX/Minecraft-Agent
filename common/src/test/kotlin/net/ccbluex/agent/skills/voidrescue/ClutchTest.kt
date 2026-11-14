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
package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.SkillLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertNotNull

/**
 * How fast a bot looks down for a clutch. In the ring8 arena the pitch of every clutch climbed 10.5 degrees a tick
 * from the knock: from -0.7 to 69.0 in seven ticks with the feet down to y 65.58 and the island top at 65, so the
 * second block never found the look turned to it.
 *
 * @agent claude-sonnet-5-5
 */
class ClutchTest {

    private fun ticksToLookDown(purpose: LookPurpose, yRot: Float = 0f): Int {
        val turning = SkillLevel.NORMAL.turning()
        val target = ViewRotation(yRot, 70f)
        var current = ViewRotation(0f, 0f)
        var ticks = 0
        while ((abs(Mth.wrapDegrees(target.yRot - current.yRot)) > 0.5f || abs(target.xRot - current.xRot) > 0.5f) && ticks < 100) {
            current = turning.turn(current, target, purpose)
            ticks++
        }
        return ticks
    }

    @Test
    fun theAimPaceIsTheOneSeenInTheRuns() {
        assertEquals(7, ticksToLookDown(LookPurpose.AIM))
    }

    @Test
    fun theClutchLooksDownWithinTwoTicksAndAroundWithinFour() {
        assertTrue(ticksToLookDown(Clutch.LOOK) <= 2)
        assertTrue(ticksToLookDown(Clutch.LOOK, yRot = 180f) <= 5)
    }

    /**
     * A knock-off: clutch blocks at y 70 at x -18..-16.
     */
    private val platform = setOf(BlockPos(-18, 70, 20), BlockPos(-17, 70, 20), BlockPos(-16, 70, 19))
    private val landing = listOf(BlockPos(-14, 70, 17))
    private val eyes = Vec3(-14.3, 73.4, 17.2)

    private fun next(solid: Set<BlockPos>, from: List<BlockPos> = landing) =
        Clutch.nextPlacement(from, eyes, 1L, isOpen = { it !in solid }, isSolid = { it in solid })

    @Test
    fun aKnockThreeCellsPastTheLastBlockStillStartsAChain() {
        val placement = assertNotNull(next(platform))
        val aimed = BlockPos.containing(placement.aim.add(-0.01, 0.0, 0.0))
        assertEquals(BlockPos(-16, 70, 19), aimed, "clicked onto the platform's side")
        assertTrue(BlockPos(-15, 70, 19) in placement.cells)
    }

    @Test
    fun theChainGrowsTowardsWhereTheBodyComesDown() {
        val grown = platform + BlockPos(-15, 70, 19)
        val placement = assertNotNull(next(grown))
        assertTrue(BlockPos(-15, 70, 18) in placement.cells)
        assertTrue(BlockPos(-16, 70, 18) !in placement.cells, "the nearest supported ring is the one that counts")
    }

    @Test
    fun nothingWithinTheChainReachGivesNoPlacement() {
        assertNull(next(setOf(BlockPos(-30, 70, 40))))
    }
}
