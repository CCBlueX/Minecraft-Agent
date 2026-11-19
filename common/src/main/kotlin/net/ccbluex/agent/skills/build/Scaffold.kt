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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.PlaceableBlocks
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.INVENTORY_SLOTS
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.count
import net.ccbluex.agent.skills.dig
import net.ccbluex.agent.skills.gather.pickUpItems
import net.ccbluex.agent.skills.placeBlock
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.item.ItemStack

/**
 * The spare blocks of a build, those carried beyond what is left to place, which the player stands on where no face
 * shows from the ground; [placed] keeps where they went, so they come down again once the walk goes on elsewhere.
 *
 * @agent claude-opus-5-5
 */
internal class Scaffold(private val agent: Agent, private val blueprint: Map<BlockPos, BlockState>) : PlaceableBlocks {

    val placed = HashSet<BlockPos>()

    /**
     * Blocks of the build that had a spot to place them from only on a spare block, while none was carried.
     */
    val lacking = HashSet<BlockPos>()
    private val items = blueprint.values.mapTo(HashSet()) { it.block.asItem() }

    /**
     * Whether [stack] is a block no part of the build takes.
     */
    fun isSpare(stack: ItemStack): Boolean = PlaceableBlocks.DEFAULT.isPlaceable(stack) && stack.item !in items

    override fun isPlaceable(stack: ItemStack): Boolean {
        if (!PlaceableBlocks.DEFAULT.isPlaceable(stack)) return false
        if (stack.item !in items) return true
        val needed = blueprint.count { (pos, state) ->
            state.block.asItem() == stack.item && !isBuilt(agent.body.level, pos, state)
        }
        return agent.body.player.inventory.count(ItemMatch.of(stack.item)) > needed
    }

    /**
     * Whether a spare block is carried, or put up and free to come down again.
     */
    fun hasSpare(): Boolean {
        val inventory = agent.body.player.inventory
        return (0 until INVENTORY_SLOTS).any { isPlaceable(inventory.getItem(it)) } ||
            placed.any { it !in blueprint && !agent.body.level.getBlockState(it).canBeReplaced() }
    }

    /**
     * Whether a spare block could go at [pos] to stand on: on solid ground, and nowhere the build places a block.
     */
    fun canPlace(pos: BlockPos): Boolean {
        val level = agent.body.level
        return pos !in blueprint && level.getBlockState(pos).canBeReplaced() &&
            !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty
    }

    /**
     * Puts a spare block at [pos] from beside it, stepping off the spot first; false when none is carried, there is
     * nowhere beside it to stand, or it did not stay.
     */
    suspend fun put(pos: BlockPos): Boolean {
        val player = agent.body.player
        val slot = (0 until INVENTORY_SLOTS).firstOrNull { isPlaceable(player.inventory.getItem(it)) } ?: return false
        if (player.boundingBox.intersects(AABB(pos))) {
            val beside = Direction.Plane.HORIZONTAL.map(pos::relative)
                .firstOrNull { canPlace(it) && agent.body.level.getBlockState(it.above()).canBeReplaced() } ?: return false
            walkTo(Vec3.atBottomCenterOf(beside), 0.3)
        }
        if (!agent.placeBlock(slot, pos)) return false
        placed += pos
        return true
    }

    /**
     * Walks to [spot], within [radius]; the walk may pillar or bridge on spare blocks, and [placed] keeps where.
     */
    suspend fun walkTo(spot: Vec3, radius: Double) {
        val navigator = agent.navigator
        val options = navigator.options
        val before = navigator.placement
        navigator.options = options.copy(canPlace = true, placeableBlocks = this)
        var ending: Ending? = null
        navigator.moveTo(spot, radius) { ending = it }
        try {
            tickUntil { ticks ->
                navigator.placement?.takeIf { it !== before }?.let { placed += it.pos }
                ending != null || ticks >= 160 || !agent.body.player.isAlive
            }
        } finally {
            if (ending == null) navigator.stop()
            navigator.options = options
        }
    }

    /**
     * Digs out what was put up outside the build but [keep], top down as a pillar comes down, and picks the blocks up
     * again for the next spot.
     */
    suspend fun clear(keep: BlockPos? = null) {
        val level = agent.body.level
        val inventory = agent.body.player.inventory
        for (pos in placed.sortedByDescending { it.y }) {
            if (pos in blueprint || pos == keep) continue
            val state = level.getBlockState(pos)
            if (!state.canBeReplaced()) {
                val item = ItemMatch.of(state.block.asItem())
                val carried = inventory.count(item)
                if (!agent.dig(pos, forDrops = false)) continue
                val around = Vec3.atCenterOf(pos)
                // The drop shows a tick or more after the block went, unless the player stood on it and took it at once
                tickConditional(20) {
                    inventory.count(item) > carried || level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(around, 3.0, 3.0, 3.0)).isNotEmpty()
                }
                agent.pickUpItems(around, 1.5, 100)
            }
            placed -= pos
        }
    }
}
