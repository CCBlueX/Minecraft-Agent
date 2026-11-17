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

import net.ccbluex.agent.navigation.Bridging
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The method picked per bridge shape and option, and the cadences of the methods.
 *
 * @agent claude-opus-5-5
 */
internal class BridgingTest {

    @Test
    fun `level bridges are Ninja bridged unless risky methods are allowed`() {
        assertEquals(Bridging.NINJA, pick(straight(10), risky = false))
        assertEquals(Bridging.NINJA, pick(staircase(10), risky = false))
    }

    @Test
    fun `long straight bridges are tellied, godbridged without sprinting, shorter ones moonwalked`() {
        assertEquals(Bridging.TELLY, pick(straight(10), risky = true))
        assertEquals(Bridging.GODBRIDGE, pick(straight(16), risky = true, canSprint = false))
        assertEquals(Bridging.BREEZILY, pick(straight(9), risky = true, canSprint = false))
        assertEquals(Bridging.BREEZILY, pick(straight(8), risky = true))
        assertEquals(Bridging.MOONWALK, pick(straight(8) + turn(8, 1), risky = true))
    }

    @Test
    fun `staircases and short bridges stay Ninja`() {
        assertEquals(Bridging.NINJA, pick(staircase(16), risky = true))
        assertEquals(Bridging.NINJA, pick(straight(2) + walk(2), risky = true))
    }

    @Test
    fun `rising bridges are one-stacked, and Swift Sneak III sneaks across level ones`() {
        assertEquals(Bridging.ONE_STACK, pick(straight(2) + rise(2), risky = true))
        assertEquals(Bridging.SWIFT_SNEAK, pick(straight(10), risky = false, sneakingSpeed = 0.75))
        assertEquals(Bridging.NINJA, pick(straight(10), risky = false, sneakingSpeed = 0.45))
    }

    @Test
    fun `a fixed method crosses what it can and leaves the rest to Ninja`() {
        assertEquals(Bridging.GODBRIDGE, pick(straight(2), risky = false, option = Bridging.GODBRIDGE))
        assertEquals(Bridging.TELLY, pick(straight(10), risky = false, option = Bridging.TELLY))
        assertEquals(Bridging.NINJA, pick(straight(2) + walk(2), risky = true, option = Bridging.TELLY))
        assertEquals(Bridging.NINJA, pick(staircase(10), risky = true, option = Bridging.GODBRIDGE))
        assertEquals(Bridging.NINJA, pick(straight(2) + rise(2), risky = true, option = Bridging.BREEZILY))
        assertEquals(Bridging.NINJA, pick(straight(10), risky = true, option = Bridging.ONE_STACK))
        assertEquals(Bridging.ONE_STACK, pick(straight(2) + rise(2), risky = false, option = Bridging.ONE_STACK))
        assertEquals(Bridging.SWIFT_SNEAK, pick(staircase(10), risky = false, option = Bridging.SWIFT_SNEAK))
    }

    @Test
    fun `the bridge of a step runs over the gap steps around it`() {
        val steps = walk(0) + straight(4, from = 1) + walk(5)
        val bridge = Bridge.at(steps, 3)
        assertEquals(1 until 5, bridge?.let { it.start until it.end })
        assertNull(Bridge.at(steps, 0))
    }

    @Test
    fun `godbridging resets every 7 or 8 blocks`() {
        val resets = (0 until 32).filter(Bridging.GODBRIDGE::resets)
        assertEquals(listOf(7, 15, 22, 30), resets)
    }

    @Test
    fun `moonwalks reset by sneaking every 12 blocks and tap only in the middle of a block`() {
        assertEquals(listOf(12, 24), (0 until 30).filter(Bridging.MOONWALK::resets))
        assertEquals(listOf(0, -1, 0), listOf(0.05, 0.5, 0.95).map { Bridging.MOONWALK.tap(3, it, turn = -1) })
        assertEquals(1, Bridging.MOONWALK.tap(3, 0.5, turn = 0))
        assertEquals(listOf(-1, 1), listOf(0, 1).map { Bridging.BREEZILY.tap(it, 0.5, turn = 1) })
    }

    private fun pick(
        steps: List<Step>,
        risky: Boolean,
        sneakingSpeed: Double = 0.3,
        canSprint: Boolean = true,
        option: Bridging = Bridging.AUTO,
    ) = option.pick(checkNotNull(Bridge.at(steps, 0)), risky, sneakingSpeed, canSprint)

    /**
     * [blocks] level bridge steps east from x [from].
     */
    private fun straight(blocks: Int, from: Int = 0) = (from until from + blocks).map { x ->
        step(BlockPos(x, 0, 0), BlockPos(x + 1, 0, 0), Move.TRAVERSE, places = listOf(BlockPos(x + 1, -1, 0)))
    }

    /**
     * [blocks] level bridge steps, east and south in turn.
     */
    private fun staircase(blocks: Int): List<Step> {
        var from = BlockPos.ZERO
        return (0 until blocks).map { i ->
            val to = if (i % 2 == 0) from.east() else from.south()
            step(from, to, Move.TRAVERSE, places = listOf(to.below())).also { from = to }
        }
    }

    /**
     * One-stack steps up from x [from], [blocks] of them.
     */
    private fun rise(from: Int, blocks: Int = 2) = (0 until blocks).map { i ->
        val start = BlockPos(from + i, i, 0)
        val to = BlockPos(from + i + 1, i + 1, 0)
        step(start, to, Move.ASCEND, places = listOf(to.below(2), to.below()))
    }

    /**
     * A plain step east from x [from].
     */
    private fun walk(from: Int) = listOf(step(BlockPos(from, 0, 0), BlockPos(from + 1, 0, 0), Move.TRAVERSE))

    /**
     * A plain step from x [x] turning south, to the right of east, or north for [side] -1.
     */
    private fun turn(x: Int, side: Int) =
        listOf(step(BlockPos(x, 0, 0), BlockPos(x, 0, side), Move.TRAVERSE))

    private fun step(from: BlockPos, to: BlockPos, move: Move, places: List<BlockPos> = emptyList()) =
        Step(from, to, move, Vec3.atBottomCenterOf(to), emptyList(), places, emptyList(), 1.0)
}
