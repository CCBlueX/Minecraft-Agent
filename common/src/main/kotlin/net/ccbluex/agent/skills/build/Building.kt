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
import net.ccbluex.agent.skills.useBlock
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.BedPart
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.DoubleBlockHalf
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items

/**
 * Builds [blocks] bottom up, nearest first, with blocks from the inventory, taking more from the [supplies] chests
 * when it runs out. Each is placed sneaking, from a side where it comes out facing as wanted, then clicked until a
 * trapdoor or gate is open, a repeater has its delay and a comparator its mode; liquids and waterlogged blocks are
 * poured from buckets. Blocks already right stay, and with [canBreak] wrong ones are dug out first. Piston heads,
 * upper halves and flowing liquids are left to the blocks they come from. A block with nothing to place it against
 * waits for its neighbours, an observer for the block it watches, and a liquid for the blocks beside and under it;
 * one tried three times is given up. Where no face shows from the ground, the player pillars up on a spare block it
 * carries and digs it out again once it walks on elsewhere.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.build(
    blocks: Map<BlockPos, BlockState>,
    canBreak: Boolean = false,
    supplies: Collection<BlockPos> = emptyList(),
): BuildResult {
    val level = body.level
    val tries = HashMap<BlockPos, Int>()
    // Blocks no click places yet, as what they go against is missing; they wait until something else is built
    val waiting = HashSet<BlockPos>()
    val scaffold = Scaffold(this, blocks)
    val stock = Supplies(this, supplies, scaffold)
    val wanted = blocks.filterValues { !follows(it) }
    // Blocks that would leave the neighbour named no spot to be placed from; they go after it
    val shutting = HashMap<BlockPos, BlockPos>()
    fun isShutting(pos: BlockPos) = shutting[pos]?.let { other ->
        tries.getOrDefault(other, 0) < 3 && wanted[other]?.let { !isBuilt(level, other, it) } == true
    } == true
    // Blocks that had a spare block put up beside them to go against
    val propped = HashSet<BlockPos>()
    var cleared = false
    while (body.player.isAlive) {
        // A click of the walk's own scaffold on a hopper or chest opens it, and an open menu stops every inventory click
        if (body.containerMenu != null) body.closeContainer()
        val player = body.player.position()
        // Inside out within a layer: a block its neighbours would close in shows no face once they stand
        fun unbuiltNeighbours(pos: BlockPos) = Direction.Plane.HORIZONTAL.count { side ->
            wanted[pos.relative(side)]?.let { !isBuilt(level, pos.relative(side), it) } == true
        }
        val order = compareBy<Map.Entry<BlockPos, BlockState>>(
            { it.key.y }, { -unbuiltNeighbours(it.key) }, { it.key.distToCenterSqr(player) },
        )
        val open = wanted.entries
            .filter { (pos, state) ->
                pos !in waiting && tries.getOrDefault(pos, 0) < 3 && canBuild(pos, state, canBreak || pos in scaffold.placed)
            }
            .filter { (pos, state) -> itemFor(pos, state)?.let { isCarried(it) || stock.mayHave(it) } != false }
        val free = open.filter { !isShutting(it.key) }.ifEmpty { open }
        val ready = free.filter { (pos, state) -> isReady(level, wanted, pos, state) }.ifEmpty { free }
        val next = ready.minWithOrNull(order)
        if (next == null) {
            // Scaffold left up may hide the faces of what is left: it comes down once before anything is propped
            if (!cleared && scaffold.placed.isNotEmpty()) {
                cleared = true
                scaffold.clear()
                waiting.clear()
                continue
            }
            // What is left has nothing to go against: a spare block beside the lowest gives it a face
            val held = wanted.entries
                .filter { (pos, state) ->
                    pos !in propped && !isLiquid(state) && !state.isAir && level.getBlockState(pos).canBeReplaced() &&
                        tries.getOrDefault(pos, 0) < 3 && itemFor(pos, state)?.let { isCarried(it) || stock.mayHave(it) } != false
                }
                .sortedWith(order)
                .firstOrNull { propped += it.key; scaffold.prop(it.key) }
            if (held == null) break
            waiting.clear()
            continue
        }
        val (pos, state) = next
        val item = itemFor(pos, state)
        if (item != null && !isCarried(item)) {
            val left = wanted.entries.filter { !isBuilt(level, it.key, it.value) }.sortedWith(order)
            stock.restock(item, left.mapNotNull { itemFor(it.key, it.value) })
            continue
        }
        val actual = level.getBlockState(pos)
        val isLiquid = isLiquid(state)
        // A wrong block in the cell, such as one the walk stood on, is dug out first; clicks are judged once it is free
        val toPlace = !isLiquid && !state.isAir && actual.canBeReplaced() && !isPlaced(level, pos, actual, state)
        if (toPlace && !hasClick(pos, state, scaffold)) {
            waiting += pos
            continue
        }
        if (toPlace && pos !in shutting) {
            val shut = around(pos).firstOrNull { other ->
                val wants = wanted[other] ?: return@firstOrNull false
                !isLiquid(wants) && !wants.isAir && !isPlaced(level, other, level.getBlockState(other), wants) &&
                    wouldShut(pos, other, wants, scaffold)
            }
            if (shut != null) {
                shutting[pos] = shut
                continue
            }
        }
        tries.merge(pos, 1, Int::plus)
        val done = when {
            isLiquid -> (actual.canBeReplaced() || dig(pos, forDrops = false)) && pour(pos, state, scaffold)
            isPlaced(level, pos, actual, state) -> if (actual.usedValues != state.usedValues) use(pos, state) else pour(pos, state, scaffold)
            !actual.canBeReplaced() && !dig(pos, forDrops = false) -> false
            else -> state.isAir || place(pos, state, scaffold)
        }
        if (done) {
            waiting.clear()
            cleared = false
        }
    }
    scaffold.props.clear()
    scaffold.clear()
    val left = wanted.filter { (pos, state) -> !isBuilt(level, pos, state) }
    return BuildResult(missing(left), left.keys, left.keys.any { it in scaffold.lacking })
}

/**
 * The 26 cells around [pos].
 *
 * @agent claude-opus-5-5
 */
private fun around(pos: BlockPos): Sequence<BlockPos> =
    (-1..1).asSequence().flatMap { x -> (-1..1).flatMap { y -> (-1..1).map { z -> pos.offset(x, y, z) } } }.filter { it != pos }

/**
 * Whether [state] comes about from another block of the build rather than being placed: the head of a piston, the
 * upper half of a plant or door, the head of a bed, a flowing liquid.
 *
 * @agent claude-opus-5-5
 */
internal fun follows(state: BlockState): Boolean =
    !state.isAir && state.block.asItem() == Items.AIR && !(isLiquid(state) && state.fluidState.isSource) ||
        state.getOptionalValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ||
        state.getOptionalValue(BlockStateProperties.BED_PART) == BedPart.HEAD

/**
 * Whether [state] is water or lava itself rather than a block that holds it.
 *
 * @agent claude-opus-5-5
 */
internal fun isLiquid(state: BlockState): Boolean = state.`is`(Blocks.WATER) || state.`is`(Blocks.LAVA)

/**
 * The item building [state] at [pos] takes next: the block, the bucket of a liquid or of the water a placed block
 * still lacks; null when only clicks on the placed block are left.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.itemFor(pos: BlockPos, state: BlockState): Item? {
    val fluid = state.fluidState.type
    if (isLiquid(state)) return if (fluid == Fluid.LAVA) Items.LAVA_BUCKET else Items.WATER_BUCKET
    val actual = body.level.getBlockState(pos)
    if (!isPlaced(body.level, pos, actual, state)) return state.block.asItem().takeIf { it != Items.AIR }
    if (actual.usedValues != state.usedValues) return null
    return Items.WATER_BUCKET.takeIf { fluid == Fluid.WATER }
}

private fun Agent.isCarried(item: Item): Boolean = body.player.inventory.slotOf(ItemMatch.of(item)) >= 0

/**
 * Whether [state] is not at [pos] yet and can be built now: what is there is that block, needing only a click or
 * water, or it may be dug or replaced and a neighbour is there to place it against.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.canBuild(pos: BlockPos, state: BlockState, canBreak: Boolean): Boolean {
    val level = body.level
    val actual = level.getBlockState(pos)
    if (isBuilt(level, pos, state)) return false
    if (isPlaced(level, pos, actual, state)) return true
    if (!actual.canBeReplaced() && !canBreak) return false
    if (state.isAir) return true
    return Direction.entries.any { !level.getBlockState(pos.relative(it)).getCollisionShape(level, pos.relative(it)).isEmpty }
}

/**
 * Whether [state] at [pos] goes before what is left of [wanted] around it: an observer once the block it watches
 * stands, as that block coming later would make it pulse; a liquid once the blocks beside and under it stand, so it
 * flows nowhere else, and water into a placed block once all around it stand; any other block not over a liquid
 * still to pour, whose cell it would close.
 *
 * @agent claude-opus-5-5
 */
internal fun isReady(level: BlockGetter, wanted: Map<BlockPos, BlockState>, pos: BlockPos, state: BlockState): Boolean {
    fun stands(at: BlockPos) = wanted[at]?.let { isLiquid(it) || isBuilt(level, at, it) } != false
    if (isLiquid(state)) return Direction.entries.all { it == Direction.UP || stands(pos.relative(it)) }
    val actual = level.getBlockState(pos)
    if (isPlaced(level, pos, actual, state)) {
        // An observer next to it waits for the water instead, which would make it pulse
        return actual.usedValues != state.usedValues ||
            Direction.entries.all { wanted[pos.relative(it)]?.`is`(Blocks.OBSERVER) == true || stands(pos.relative(it)) }
    }
    wanted[pos.below()]?.let { if (isLiquid(it) && !isBuilt(level, pos.below(), it)) return false }
    if (!state.`is`(Blocks.OBSERVER)) return true
    val watched = pos.relative(state.getValue(BlockStateProperties.FACING))
    return wanted[watched]?.let { isBuilt(level, watched, it) } != false
}

/**
 * Right-clicks the placed block at [pos] until it is open, delayed or in the mode [wanted] has; false when a click
 * changed nothing.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.use(pos: BlockPos, wanted: BlockState): Boolean {
    val level = body.level
    // A repeater takes up to three clicks to its longest delay
    repeat(4) {
        val state = level.getBlockState(pos)
        if (state.usedValues == wanted.usedValues) return true
        if (!useBlock(pos) { level.getBlockState(pos) !== state }) return false
    }
    return level.getBlockState(pos).usedValues == wanted.usedValues
}

/**
 * The items [left] needs beyond those carried.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.missing(left: Map<BlockPos, BlockState>): Map<Item, Int> {
    val inventory = body.player.inventory
    return left.mapNotNull { (pos, state) -> itemFor(pos, state) }.groupingBy { it }.eachCount()
        .mapValues { (item, count) -> count - inventory.count(ItemMatch.of(item)) }
        .filterValues { it > 0 }
}
