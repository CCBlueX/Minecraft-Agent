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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.walkTo
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.dig
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.math.Vec3
import java.util.function.Predicate

/**
 * Digs a staircase [steps] steps down towards [direction], one block forward and one down each, as a player mines
 * down without falling into what lies below; stops before a fluid, a drop, blocks that would fall in, or blocks the
 * navigator's options keep it from breaking. How many steps it went down.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.digStairsDown(direction: Direction, steps: Int): Int {
    for (step in 0 until steps) {
        val feet = body.player.blockPosition()
        val next = feet.relative(direction).below()
        val cells = listOf(next.above(2), next.above(), next)
        if (!isSafeToDig(body.level, cells, next.below()) || !mayBreak(cells)) return step
        for (cell in cells) {
            if (!dig(cell, forDrops = false)) return step
        }
        if (navigator.walkTo(Vec3.atBottomCenterOf(next), 0.4) != Ending.ARRIVED) return step
    }
    return steps
}

/**
 * Digs a tunnel two blocks high [length] blocks towards [direction] at the feet's level, stopping before fluids,
 * drops and blocks the navigator's options keep it from breaking; how many blocks it got along. [onStep] hears each
 * new cell and says whether to go on, so the caller can look at the walls.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.digTunnel(direction: Direction, length: Int, onStep: Predicate<BlockPos>): Int {
    for (step in 0 until length) {
        val feet = body.player.blockPosition()
        val next = feet.relative(direction)
        val cells = listOf(next.above(), next)
        if (!isSafeToDig(body.level, cells, next.below()) || !mayBreak(cells)) return step
        for (cell in cells) {
            if (!dig(cell, forDrops = false)) return step
        }
        if (navigator.walkTo(Vec3.atBottomCenterOf(next), 0.4) != Ending.ARRIVED) return step
        if (!onStep.test(next)) return step + 1
    }
    return length
}

/**
 * Whether the navigator's options let the agent break every one of [cells] that is not open already.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.mayBreak(cells: List<BlockPos>): Boolean = cells.all {
    val state = body.level.getBlockState(it)
    state.isAir || navigator.options.breakableBlocks.isBreakable(state)
}

/**
 * Whether [cells] can be dug out: none holds or touches a fluid, nothing above them falls, and [floor] holds the
 * player.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun isSafeToDig(level: BlockGetter, cells: List<BlockPos>, floor: BlockPos): Boolean {
    val top = cells.maxBy { it.y }
    if (level.getBlockState(top.above()).`is`(BlockKind.FALLING)) return false
    if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty) return false
    return cells.all { cell ->
        level.getFluidState(cell).isEmpty && Direction.entries.all { level.getFluidState(cell.relative(it)).isEmpty }
    }
}
