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
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack

/**
 * The spare blocks of a build, those carried beyond what is left to place, which the player stands on where no face
 * shows from the ground; [placed] keeps where they went, so one comes down again when a spot needs it and none is
 * carried, and all of them once the build is done.
 *
 * @agent claude-opus-5-5
 */
internal class Scaffold(private val agent: Agent, private val blueprint: Map<BlockPos, BlockState>) : PlaceableBlocks {

    val placed = HashSet<BlockPos>()
    private val spareItems = HashSet<Item>()

    /**
     * Whether the block at [pos] is one put up as a spare: one [placed] knows, or of a kind put up as one before,
     * as walks outside [walkTo] may put one up too.
     */
    fun isOwn(pos: BlockPos): Boolean = pos in placed || agent.body.level.getBlockState(pos).block.asItem() in spareItems

    /**
     * Blocks of the build that had a spot to place them from only on a spare block, while none was carried.
     */
    val lacking = HashSet<BlockPos>()
    private val items = blueprint.values.mapTo(HashSet()) { it.block.asItem() }

    /**
     * Whether part of the build goes over [stand], up to a few blocks above the head.
     */
    fun isCovered(stand: Vec3): Boolean {
        val feet = BlockPos.containing(stand)
        return (2..6).any { feet.above(it) in blueprint }
    }

    /**
     * Whether [stand] is on top of a block of the build, where the walk to it is narrower than on the ground.
     */
    fun isOnBuild(stand: Vec3): Boolean = BlockPos.containing(stand).below() in blueprint

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
     * Whether a spare block is carried.
     */
    fun carriesSpare(): Boolean {
        val inventory = agent.body.player.inventory
        return (0 until INVENTORY_SLOTS).any { isPlaceable(inventory.getItem(it)) }
    }

    /**
     * Whether a spare block is carried, or put up and free to come down again.
     */
    fun hasSpare(): Boolean =
        carriesSpare() || placed.any { it !in blueprint && !agent.body.level.getBlockState(it).canBeReplaced() }

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
        if (!stepOff(pos)) return false
        if (!agent.placeBlock(slot, pos)) return false
        placed += pos
        spareItems += agent.body.level.getBlockState(pos).block.asItem()
        return true
    }

    /**
     * Steps out of [pos] onto a cell beside it when the player is in it; false when there is none to stand on.
     */
    suspend fun stepOff(pos: BlockPos): Boolean {
        if (!agent.body.player.boundingBox.intersects(AABB(pos))) return true
        val beside = Direction.Plane.HORIZONTAL.map(pos::relative)
            .firstOrNull { canPlace(it) && agent.body.level.getBlockState(it.above()).canBeReplaced() } ?: return false
        walkTo(Vec3.atBottomCenterOf(beside), 0.3)
        return true
    }

    /**
     * Spare blocks put up beside a block of the build with nothing else to place it against, by the block they hold
     * out a face to; they stay up until it stands.
     */
    val props = HashMap<BlockPos, BlockPos>()

    /**
     * Puts a spare block next to [pos], first where the block faces, as a stair or hopper takes its facing from the
     * face it is placed against, then under it where it can, so the block has a face to go against; false when no
     * cell beside it outside the build has something to put one against, or none could be put.
     */
    suspend fun prop(pos: BlockPos): Boolean {
        val level = agent.body.level
        val facing = blueprint[pos]?.let {
            it.getOptionalValue(BlockStateProperties.FACING) ?: it.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
        }
        val sides = listOfNotNull(facing) + Direction.DOWN + Direction.Plane.HORIZONTAL + Direction.UP
        val cell = sides.distinct().map(pos::relative).firstOrNull { cell ->
            cell !in blueprint && level.getBlockState(cell).canBeReplaced() &&
                Direction.entries.any { !level.getBlockState(cell.relative(it)).getCollisionShape(level, cell.relative(it)).isEmpty }
        } ?: return false
        if (!put(cell)) return false
        props[cell] = pos
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
                navigator.placement?.takeIf { it !== before }?.let {
                    placed += it.pos
                    spareItems += agent.body.level.getBlockState(it.pos).block.asItem()
                }
                ending != null || ticks >= 160 || !agent.body.player.isAlive
            }
        } finally {
            if (ending == null) navigator.stop()
            navigator.options = options
        }
    }

    /**
     * Digs out what was put up outside the build but the [props] whose block is still missing, nearest first and
     * each pillar top down, and picks the blocks up again; with [around], only what stands in the cell of one of
     * these blocks or where the player would stand to place it.
     */
    suspend fun clear(around: Collection<BlockPos>? = null) {
        val failed = HashSet<BlockPos>()
        while (true) {
            val pos = nearest(null) { pos ->
                pos !in failed && (around == null || around.any { isInStand(pos, it) })
            } ?: return
            if (!take(pos)) failed += pos
        }
    }

    /**
     * Takes back the spare block nearest the player but [keep], not the one it stands on, when a spot needs one and
     * none is carried; digging all of them would cost seconds each. False when none is left.
     */
    suspend fun free(keep: BlockPos?): Boolean {
        val level = agent.body.level
        val player = agent.body.player
        val pos = nearest(keep) {
            !level.getBlockState(it).canBeReplaced() && !player.boundingBox.intersects(AABB(it.above()))
        } ?: return false
        return take(pos)
    }

    /**
     * The spare block nearest the player that may come down and [matches]: the top of its pillar, neither [keep] nor
     * a prop whose block is still missing.
     */
    private fun nearest(keep: BlockPos?, matches: (BlockPos) -> Boolean): BlockPos? {
        val level = agent.body.level
        val position = agent.body.player.position()
        return placed.filter { pos ->
            pos !in blueprint && pos != keep && pos.above() !in placed && matches(pos) &&
                props[pos]?.let { held -> blueprint[held]?.let { !isBuilt(level, held, it) } } != true
        }.minByOrNull { it.distToCenterSqr(position) }
    }

    /**
     * Digs the block at [pos], a spare one or one placed wrong, and picks it up; false when the dig failed.
     */
    suspend fun take(pos: BlockPos): Boolean {
        val level = agent.body.level
        val inventory = agent.body.player.inventory
        val state = level.getBlockState(pos)
        if (!state.canBeReplaced()) {
            val item = ItemMatch.of(state.block.asItem())
            val carried = inventory.count(item)
            if (!agent.dig(pos, forDrops = false)) return false
            val center = Vec3.atCenterOf(pos)
            // The drop shows a tick or more after the block went, unless the player stood on it and took it at once
            tickConditional(20) {
                inventory.count(item) > carried || level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(center, 3.0, 3.0, 3.0)).isNotEmpty()
            }
            if (inventory.count(item) <= carried) agent.pickUpItems(center, 1.5, 100)
        }
        props -= pos
        placed -= pos
        return true
    }
}

/**
 * Whether [cell] is [pos] or in a column [clicks] takes stand spots from to place [pos], feet to head.
 *
 * @agent claude-opus-5-5
 */
internal fun isInStand(cell: BlockPos, pos: BlockPos): Boolean {
    val x = cell.x - pos.x
    val z = cell.z - pos.z
    return (x == 0 && z in -3..3 || z == 0 && x in -3..3) && cell.y - pos.y in -3..2
}
