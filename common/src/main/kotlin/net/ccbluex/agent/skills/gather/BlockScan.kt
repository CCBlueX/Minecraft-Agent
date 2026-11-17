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

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.BlockState
import java.util.function.BiPredicate

/**
 * The blocks matching [match] within [radius] blocks across and [depth] up and down of [center], nearest first, at
 * most [limit]. Unloaded cells read as air, so they never match a solid block.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun BlockGetter.nearestBlocks(
    center: BlockPos,
    radius: Int,
    depth: Int,
    limit: Int,
    match: BiPredicate<BlockPos, BlockState>,
): List<BlockPos> {
    val found = ArrayList<BlockPos>()
    val pos = BlockPos.MutableBlockPos()
    val low = maxOf(center.y - depth, minY)
    val high = minOf(center.y + depth, maxY)
    for (x in center.x - radius..center.x + radius) {
        for (z in center.z - radius..center.z + radius) {
            for (y in low..high) {
                pos.set(x, y, z)
                val state = getBlockState(pos)
                if (!state.isAir && match.test(pos, state)) found += pos.immutable()
            }
        }
    }
    return found.sortedBy { it.distSqr(center) }.take(limit)
}

/**
 * Whether [pos] touches a cell a player could see it from: air, or something else it can look through.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun BlockGetter.isExposed(pos: BlockPos): Boolean = Direction.entries.any {
    val next = pos.relative(it)
    val state = getBlockState(next)
    state.isAir || !state.canOcclude() && state.getCollisionShape(this, next).isEmpty && state.fluidState.isEmpty
}
