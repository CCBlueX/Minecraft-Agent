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
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.block.BlockTags
import java.util.function.Predicate

/**
 * Mines [count] blocks of [resource] in sight that [allows] lets it, nearest first, and picks up what they drop; with
 * none in sight it looks further, see [search]. Whether it mined any, or got somewhere new to look.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.gather(resource: Resource, count: Int, allows: Predicate<BlockPos> = Predicate { true }): Boolean {
    val skipped = HashSet<BlockPos>()
    var mined = 0
    // A dozen it could not get at, and the ones in sight are not worth the walk
    while (mined < count && skipped.size < 12) {
        val target = findBlock(resource, skipped, allows) ?: return mined > 0 || search(resource, allows)
        if (!mine(target)) {
            skipped += target
            continue
        }
        mined++
    }
    listener.step("gathered ${resource.name.lowercase()} x$mined")
    return mined > 0
}

/**
 * The nearest block of [resource] a player could see that [allows] lets it mine: ores and stone in a wall, logs of a
 * tree. Blocks by water are left alone, as in a flooded cave the way there runs under water with no air above.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.findBlock(resource: Resource, skipped: Set<BlockPos>, allows: Predicate<BlockPos>): BlockPos? {
    val level = body.level
    return level.nearestBlocks(body.player.blockPosition(), 24, 12, 16) { pos, state ->
        resource.matches(state) && pos !in skipped && level.isExposed(pos) && allows.test(pos) &&
            BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2)).none { !level.getFluidState(it).isEmpty }
    }.firstOrNull { resource != Resource.LOGS || isTree(it) }
}

/**
 * Whether the log at [pos] belongs to a tree, not a post or a house: leaves grow near the top of its trunk.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.isTree(pos: BlockPos): Boolean {
    val level = body.level
    var top = pos
    while (level.getBlockState(top.above()).`is`(BlockTags.LOGS)) top = top.above()
    return level.nearestBlocks(top, 2, 2, 1) { _, state -> state.`is`(BlockTags.LEAVES) }.isNotEmpty()
}

/**
 * Looks further where none of [resource] is in sight: walks on to new ground for trees, mines down a staircase for
 * stone and for ores above their height, and tunnels along at the ore's height until one shows in a wall. Whether it
 * got anywhere.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.search(resource: Resource, allows: Predicate<BlockPos>): Boolean {
    val player = body.player
    val direction = Direction.Plane.HORIZONTAL.toList().random()
    val name = resource.name.lowercase()
    val depth = resource.depth
    return when {
        resource == Resource.LOGS -> explore()
        resource == Resource.STONE -> {
            listener.step("mining down for stone towards $direction")
            digStairsDown(direction, 6) > 0
        }
        // A few blocks over the ore's height is as good as at it
        depth != null && player.blockY > depth + 4 -> {
            val steps = minOf(player.blockY - depth, 24)
            listener.step("mining down for $name towards $direction, $steps steps from y=${player.blockY}")
            digStairsDown(direction, steps) > 0
        }
        else -> {
            listener.step("tunnelling for $name towards $direction at y=${player.blockY}")
            digTunnel(direction, 32) { findBlock(resource, emptySet(), allows) == null } > 0
        }
    }
}
