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

package net.ccbluex.agent.skills.build

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.count
import net.ccbluex.agent.skills.dig
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items

/**
 * Builds [blocks] bottom up, nearest first, with blocks from the inventory. Each is placed from a side where it comes
 * out facing as wanted; blocks already right stay, and with [canBreak] wrong ones are dug out first. A block with
 * nothing to place it against waits for its neighbours, and one tried three times is given up. Where no face shows
 * from the ground, the player pillars up on a spare block it carries and digs it out again once it walks on elsewhere.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.build(blocks: Map<BlockPos, BlockState>, canBreak: Boolean = false): BuildResult {
    val level = body.level
    val tries = HashMap<BlockPos, Int>()
    val scaffold = Scaffold(this, blocks)
    while (body.player.isAlive) {
        val player = body.player.position()
        // Inside out within a layer: a block its neighbours would close in shows no face once they stand
        fun unbuiltNeighbours(pos: BlockPos) = Direction.Plane.HORIZONTAL.count { side ->
            blocks[pos.relative(side)]?.let { !isBuilt(level.getBlockState(pos.relative(side)), it) } == true
        }
        val (pos, state) = blocks.entries
            .filter { (pos, state) -> tries.getOrDefault(pos, 0) < 3 && canBuild(pos, state, canBreak || pos in scaffold.placed) }
            .minWithOrNull(
                compareBy<Map.Entry<BlockPos, BlockState>>(
                    { it.key.y }, { -unbuiltNeighbours(it.key) }, { it.key.distToCenterSqr(player) },
                ),
            )
            ?: break
        tries.merge(pos, 1, Int::plus)
        if (!level.getBlockState(pos).canBeReplaced() && !dig(pos, forDrops = false)) continue
        if (!state.isAir) place(pos, state, scaffold)
    }
    scaffold.clear()
    val left = blocks.filter { (pos, state) -> !isBuilt(level.getBlockState(pos), state) }
    return BuildResult(missing(left.values), left.keys, left.keys.any { it in scaffold.lacking })
}

/**
 * Whether [state] is not at [pos] yet and can be built now: what is there may be dug or replaced, the block is
 * carried, and a neighbour is there to place it against.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.canBuild(pos: BlockPos, state: BlockState, canBreak: Boolean): Boolean {
    val level = body.level
    val actual = level.getBlockState(pos)
    if (isBuilt(actual, state) || !actual.canBeReplaced() && !canBreak) return false
    if (state.isAir) return true
    val item = state.block.asItem()
    return item != Items.AIR && body.player.inventory.slotOf(ItemMatch.of(item)) >= 0 &&
        Direction.entries.any { !level.getBlockState(pos.relative(it)).getCollisionShape(level, pos.relative(it)).isEmpty }
}

/**
 * The items [states] need beyond those carried.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.missing(states: Collection<BlockState>): Map<Item, Int> {
    val inventory = body.player.inventory
    return states.map { it.block.asItem() }.filter { it != Items.AIR }.groupingBy { it }.eachCount()
        .mapValues { (item, count) -> count - inventory.count(ItemMatch.of(item)) }
        .filterValues { it > 0 }
}
