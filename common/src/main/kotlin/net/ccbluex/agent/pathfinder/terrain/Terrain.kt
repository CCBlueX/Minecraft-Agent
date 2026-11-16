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

package net.ccbluex.agent.pathfinder.terrain

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.simulation.BlockGrid
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.LevelBlocks
import net.ccbluex.agent.world.LiveLevel
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.DoubleBlockHalf
import net.ccbluex.agent.world.block.Fluids
import net.ccbluex.agent.world.block.Shapes
import net.ccbluex.agent.world.block.VoxelShape
import net.ccbluex.agent.world.entity.Entity.Companion.DEFAULT_BB_HEIGHT
import net.ccbluex.agent.world.entity.Entity.Companion.DEFAULT_BB_WIDTH
import net.ccbluex.agent.world.entity.Entity.Companion.DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2
import kotlin.math.abs

/**
 * Block lookups for route searches, cached per block until [invalidate]. Unknown blocks and hazards count as solid.
 * With [canOpenDoors], closed doors, fence gates and trapdoors a player opens by hand count as open, except a trapdoor
 * that is a floor; [closedIn] tells which a move has to open. Not thread-safe.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L70-L341)
 *
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
public class Terrain(
    public val level: LevelBlocks,
    public val width: Double = PLAYER_WIDTH,
    public val height: Double = PLAYER_HEIGHT,
    public val canOpenDoors: Boolean = false,
) : BlockGrid {

    private val halfWidth = width / 2.0
    private val states = Long2ObjectOpenHashMap<BlockState>()
    private val shapes = Long2ObjectOpenHashMap<VoxelShape>()
    private val stands = Long2DoubleOpenHashMap()
    private val crouchStands = Long2DoubleOpenHashMap()

    /**
     * Collision shapes of the closed blocks that count as open.
     */
    private val closed = Long2ObjectOpenHashMap<VoxelShape>()

    public val minY: Int get() = level.minY

    public val maxY: Int get() = level.maxY

    public fun box(feet: Vec3): AABB = box(feet.x, feet.y, feet.z)

    public fun box(x: Double, y: Double, z: Double, height: Double = this.height): AABB =
        AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth)

    /**
     * Whether the block is loaded, copied and inside the world border.
     */
    public fun isKnown(x: Int, y: Int, z: Int): Boolean =
        level.isKnown(x, y, z) && level.isInsideBorder(x + 0.5, z + 0.5)

    override fun state(x: Int, y: Int, z: Int): BlockState {
        val key = BlockPos.asLong(x, y, z)
        states.get(key)?.let { return it }

        val state = if (isKnown(x, y, z)) level.getBlockState(BlockPos(x, y, z)) else VOID_AIR
        states.put(key, state)
        return state
    }

    override fun shape(x: Int, y: Int, z: Int): VoxelShape {
        val key = BlockPos.asLong(x, y, z)
        shapes.get(key)?.let { return it }

        val state = state(x, y, z)
        val pos = BlockPos(x, y, z)
        val shape = when {
            !isKnown(x, y, z) || isHazard(state) -> FULL
            countsOpen(state, x, y, z) -> {
                closed.put(key, state.getCollisionShape(level, pos))
                state.setValue(BlockStateProperties.OPEN, true).getCollisionShape(level, pos)
            }

            else -> state.getCollisionShape(level, pos)
        }

        shapes.put(key, shape)
        return shape
    }

    /**
     * Adds to [opens] the closed blocks counting as open that a body moving from [from] into [box] has to open, a
     * door by its lower half. What [from] collides with was opened before.
     */
    public fun closedIn(box: AABB, from: AABB, opens: MutableList<BlockPos>) {
        if (!canOpenDoors) {
            return
        }

        forEachBlock(box) { x, y, z ->
            val closedShape = closedShape(x, y, z)
            if (closedShape != null && closedShape.intersects(x, y, z, box) && !closedShape.intersects(x, y, z, from)) {
                val half = state(x, y, z).getOptionalValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                val upper = half == DoubleBlockHalf.UPPER
                val pos = BlockPos(x, if (upper) y - 1 else y, z)
                if (pos !in opens) {
                    opens += pos
                }
            }
        }
    }

    private fun closedShape(x: Int, y: Int, z: Int): VoxelShape? {
        shape(x, y, z)
        return closed.get(BlockPos.asLong(x, y, z))
    }

    private fun countsOpen(state: BlockState, x: Int, y: Int, z: Int): Boolean {
        if (!canOpenDoors || state.getOptionalValue(BlockStateProperties.OPEN) ?: true) {
            return false
        }

        val block = state.block
        return when {
            block.`is`(BlockKind.DOOR) -> block.`is`(BlockKind.OPENS_BY_HAND)
            block.`is`(BlockKind.FENCE_GATE) -> true
            // Iron is the only vanilla trapdoor a hand cannot open.
            block.`is`(BlockKind.TRAPDOOR) -> block !== Blocks.IRON_TRAPDOOR && !isFloor(x, y, z)
            else -> false
        }
    }

    // A trapdoor to stand on, unless it covers a ladder or vine or has no room above.
    private fun isFloor(x: Int, y: Int, z: Int) = state(x, y - 1, z).block !in CLIMBABLE &&
        isEmpty(x, y + 1, z) && isEmpty(x, y + 2, z)

    private fun isEmpty(x: Int, y: Int, z: Int) =
        state(x, y, z).getCollisionShape(level, BlockPos(x, y, z)).isEmpty

    public fun isPassable(x: Int, y: Int, z: Int): Boolean = shape(x, y, z).isEmpty

    public fun isHazard(x: Int, y: Int, z: Int): Boolean = isKnown(x, y, z) && isHazard(state(x, y, z))

    public fun isWater(x: Int, y: Int, z: Int): Boolean = state(x, y, z).fluidState.type.isSame(Fluids.WATER)

    public fun isClimbable(x: Int, y: Int, z: Int): Boolean = state(x, y, z).block in CLIMBABLE

    /**
     * The side a player in the climbable block at [x] [y] [z] pushes against to climb: the wall of a ladder, else a
     * block beside it. Null when there is none, where only jumping climbs.
     */
    public fun climbSide(x: Int, y: Int, z: Int): Direction? {
        val state = state(x, y, z)
        if (state.block.`is`(BlockKind.LADDER)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).opposite
        }

        for (side in HORIZONTAL) {
            val sx = x + side.stepX
            val sz = z + side.stepZ
            if (isGrip(sx, y, sz) || isGrip(sx, y + 1, sz)) {
                return side
            }
        }
        return null
    }

    private fun isGrip(x: Int, y: Int, z: Int) = isKnown(x, y, z) && !isHazard(x, y, z) && !isPassable(x, y, z)

    /**
     * How much of a fall onto feet at [top] in column [x] [z] bounces back up, 0 for none: the restitution of the block
     * 0.2 below the feet, Entity.getOnPosLegacy, so slime under a carpet and the bed itself count.
     */
    public fun restitution(x: Int, top: Double, z: Int): Double =
        state(x, Mth.floor(top - DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2), z).block.bounceRestitution.toDouble()

    /**
     * Height the feet rest at when standing in the block, always within it, or NaN when nothing carries the player
     * there or the body does not fit. Slabs, farmland, carpets, fences and the like are taken into account.
     */
    public fun standHeight(x: Int, y: Int, z: Int): Double =
        stands.cached(BlockPos.asLong(x, y, z)) { computeStandHeight(x, y, z, height) }

    public fun isStandable(feet: BlockPos): Boolean = !standHeight(feet.x, feet.y, feet.z).isNaN()

    public fun crouchHeight(x: Int, y: Int, z: Int): Double =
        crouchStands.cached(BlockPos.asLong(x, y, z)) { computeStandHeight(x, y, z, minOf(height, CROUCH_HEIGHT)) }

    public fun feet(node: BlockPos): Vec3? {
        val height = standHeight(node.x, node.y, node.z)
        return if (height.isNaN()) null else Vec3(node.x + 0.5, height, node.z + 0.5)
    }

    public fun check(feet: BlockPos): DestinationCheck = when {
        feet.y < minY || feet.y > maxY -> DestinationCheck.OUTSIDE_WORLD
        !level.isInsideBorder(feet.x + 0.5, feet.z + 0.5) -> DestinationCheck.OUTSIDE_BORDER
        !level.isKnown(feet.x, feet.y, feet.z) -> DestinationCheck.NOT_LOADED
        !isStandable(feet) -> DestinationCheck.NOT_STANDABLE
        else -> DestinationCheck.VALID
    }

    public fun isClear(box: AABB): Boolean {
        forEachBlock(box) { x, y, z ->
            val shape = shape(x, y, z)
            if (!shape.isEmpty && shape.intersects(x, y, z, box)) {
                return false
            }
        }
        return true
    }

    public fun containsAnyLiquid(box: AABB): Boolean {
        forEachBlock(box) { x, y, z ->
            if (!state(x, y, z).fluidState.isEmpty) {
                return true
            }
        }
        return false
    }

    /**
     * Whether the server moves a player from [from] to [to] without hitting anything. It moves one axis after the
     * other, in the order of the game's version, so the corners it passes are checked too.
     */
    public fun sweep(from: Vec3, to: Vec3): Boolean {
        val movement = to.subtract(from)
        val box = box(from)
        val colliders = colliders(box.expandTowards(movement))
        if (colliders.isEmpty()) {
            return true
        }

        var moved = Vec3.ZERO
        for (axis in level.mechanics.axisStepOrder(movement)) {
            val axisMovement = movement.get(axis)
            if (axisMovement == 0.0) {
                continue
            }

            val collision = Shapes.collide(axis, box.move(moved), colliders, axisMovement)
            if (abs(collision - axisMovement) > EPSILON) {
                return false
            }
            moved = moved.with(axis, collision)
        }

        return true
    }

    /**
     * Whether something is right below the feet, which is what the server calls on ground.
     */
    public fun isSupported(feet: Vec3): Boolean {
        val box = box(feet)
        return !isClear(AABB(box.minX, feet.y - SUPPORT_PROBE, box.minZ, box.maxX, feet.y, box.maxZ))
    }

    /**
     * Drops what is cached for [pos], which became [state], and for the blocks depending on it; true if anything
     * was. A change that leaves the collision alike, as a growing crop does, is ignored.
     */
    public fun invalidate(pos: BlockPos, state: BlockState): Boolean {
        val key = pos.asLong()
        val old = states.get(key)
        if (old != null && isAlike(old, state, pos)) {
            if (state.getOptionalValue(BlockStateProperties.OPEN) == true) {
                // An opened door keeps the shape it counted with, but no longer needs opening.
                closed.remove(key)
            }
            return false
        }

        var cached = states.remove(key) != null
        cached = drop(pos.x, pos.y, pos.z) || cached
        if (canOpenDoors) {
            // Whether a trapdoor counts as open depends on the blocks below and above it, see [isFloor].
            for (dy in TRAPDOOR_NEIGHBOURS) {
                if (states.get(BlockPos.asLong(pos.x, pos.y + dy, pos.z))?.block?.`is`(BlockKind.TRAPDOOR) == true) {
                    cached = drop(pos.x, pos.y + dy, pos.z) || cached
                }
            }
        }

        return cached
    }

    private fun drop(x: Int, y: Int, z: Int): Boolean {
        val key = BlockPos.asLong(x, y, z)
        var cached = shapes.remove(key) != null
        closed.remove(key)
        // The floor, the body and the layer below a tall floor block of a standing spot are within two blocks.
        for (dy in -STAND_REACH..STAND_REACH) {
            val standKey = BlockPos.asLong(x, y + dy, z)
            if (stands.containsKey(standKey)) {
                stands.remove(standKey)
                cached = true
            }
            if (crouchStands.containsKey(standKey)) {
                crouchStands.remove(standKey)
                cached = true
            }
        }

        return cached
    }

    private fun isAlike(old: BlockState, new: BlockState, pos: BlockPos): Boolean {
        if (old === new) {
            return true
        }
        if (old.block !== new.block || !old.fluidState.type.isSame(new.fluidState.type) ||
            isHazard(old) != isHazard(new)) {
            return false
        }

        val oldShape = shapes.get(pos.asLong()) ?: old.getCollisionShape(level, pos)
        val newShape = if (isHazard(new)) FULL else new.getCollisionShape(level, pos)
        return oldShape === newShape || oldShape == newShape
    }

    private fun colliders(region: AABB): List<VoxelShape> {
        val colliders = ArrayList<VoxelShape>()
        forEachBlock(region) { x, y, z ->
            val shape = shape(x, y, z)
            if (!shape.isEmpty) {
                colliders.add(shape.move(x.toDouble(), y.toDouble(), z.toDouble()))
            }
        }
        return colliders
    }

    public companion object {
        public const val PLAYER_WIDTH: Double = DEFAULT_BB_WIDTH.toDouble()
        public const val PLAYER_HEIGHT: Double = DEFAULT_BB_HEIGHT.toDouble()
        public const val CROUCH_HEIGHT: Double = 1.5

        /**
         * Highest ledge the player walks onto without jumping.
         */
        public const val STEP_HEIGHT: Double = 0.6

        private const val STAND_REACH = 2
        private const val SUPPORT_PROBE = 1.0E-3

        /**
         * Offsets from a block to the trapdoors whose counting as open depends on it.
         */
        private val TRAPDOOR_NEIGHBOURS = intArrayOf(-2, -1, 1)

        private val HORIZONTAL = arrayOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

        private val VOID_AIR: BlockState = Blocks.VOID_AIR.defaultBlockState()

        /**
         * Only for the thread that ticks [level].
         */
        public fun live(level: Level): Terrain = Terrain(LiveLevel(level))

        public fun isHazard(state: BlockState): Boolean =
            state.fluidState.type.isSame(Fluids.LAVA) || state.block.`is`(BlockKind.FIRE) || state.block in HAZARDS

        private inline fun Long2DoubleOpenHashMap.cached(key: Long, compute: () -> Double): Double {
            if (containsKey(key)) {
                return get(key)
            }
            return compute().also { put(key, it) }
        }
    }

}

/**
 * Whether a body [height] tall fits in the middle of block [x] [y] [z] with the feet at its bottom.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L478-L479)
 *
 * @agent claude-opus-5-5
 */
internal fun Terrain.hasRoom(x: Int, y: Int, z: Int, height: Double = this.height) =
    isClear(box(x + 0.5, y.toDouble(), z + 0.5, height))

/**
 * Whether nothing is in the way of feet and head in [x] [y] [z].
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L482-L482)
 *
 * @agent claude-opus-5-5
 */
internal fun Terrain.isOpen(x: Int, y: Int, z: Int) = isPassable(x, y, z) && isPassable(x, y + 1, z)

