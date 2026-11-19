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

package net.ccbluex.agent.world

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Clip
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.world.block.BlockEntityType
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.FluidState
import net.ccbluex.agent.world.block.VoxelShape
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.item.ItemStack

/**
 * Blocks by position: a live level, or a copy a background search reads.
 *
 * @agent claude-opus-5-5
 */
public interface BlockGetter {

    public fun getBlockState(pos: BlockPos): BlockState

    public fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    /**
     * The kind of block entity at [pos]; null where there is none or it is not known.
     */
    public fun getBlockEntity(pos: BlockPos): BlockEntityType? = null

    public val minY: Int

    public val height: Int

    public val maxY: Int
        get() = minY + height - 1

    /**
     * The first block the ray of [context] hits, as the game traces it; a miss at its end otherwise.
     */
    public fun clip(context: ClipContext): BlockHitResult = traverse(context)

    public companion object {
        /**
         * The game's block walk along a ray, for adapters without one of their own.
         */
        @JvmStatic
        public fun BlockGetter.traverse(context: ClipContext): BlockHitResult {
            val from = context.from
            val to = context.to
            if (from == to) return miss(context)
            val toX = Mth.lerp(-EPSILON, to.x, from.x)
            val toY = Mth.lerp(-EPSILON, to.y, from.y)
            val toZ = Mth.lerp(-EPSILON, to.z, from.z)
            val fromX = Mth.lerp(-EPSILON, from.x, to.x)
            val fromY = Mth.lerp(-EPSILON, from.y, to.y)
            val fromZ = Mth.lerp(-EPSILON, from.z, to.z)
            var x = Mth.floor(fromX)
            var y = Mth.floor(fromY)
            var z = Mth.floor(fromZ)
            val pos = BlockPos.MutableBlockPos(x, y, z)
            hit(context, pos)?.let { return it }
            val dx = toX - fromX
            val dy = toY - fromY
            val dz = toZ - fromZ
            val signX = Math.signum(dx).toInt()
            val signY = Math.signum(dy).toInt()
            val signZ = Math.signum(dz).toInt()
            val stepX = if (signX == 0) Double.MAX_VALUE else signX / dx
            val stepY = if (signY == 0) Double.MAX_VALUE else signY / dy
            val stepZ = if (signZ == 0) Double.MAX_VALUE else signZ / dz
            var tX = stepX * if (signX > 0) 1.0 - frac(fromX) else frac(fromX)
            var tY = stepY * if (signY > 0) 1.0 - frac(fromY) else frac(fromY)
            var tZ = stepZ * if (signZ > 0) 1.0 - frac(fromZ) else frac(fromZ)
            while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
                if (tX < tY) {
                    if (tX < tZ) {
                        x += signX
                        tX += stepX
                    } else {
                        z += signZ
                        tZ += stepZ
                    }
                } else if (tY < tZ) {
                    y += signY
                    tY += stepY
                } else {
                    z += signZ
                    tZ += stepZ
                }
                hit(context, pos.set(x, y, z))?.let { return it }
            }
            return miss(context)
        }

        private fun BlockGetter.hit(context: ClipContext, pos: BlockPos): BlockHitResult? {
            val state = getBlockState(pos)
            val shape = if (context.block == ClipContext.Block.COLLIDER) state.getCollisionShape(this, pos) else
                state.getShape(this, pos)
            return shape.clip(context.from, context.to, pos)
        }

        private fun miss(context: ClipContext): BlockHitResult {
            val delta = context.from.subtract(context.to)
            return BlockHitResult.miss(
                context.to, Direction.getApproximateNearest(delta.x, delta.y, delta.z), BlockPos.containing(context.to),
            )
        }

        private fun frac(value: Double) = value - Math.floor(value)

        private const val EPSILON = 1.0E-7
    }
}

/**
 * Air everywhere, for facts of a block state that do not depend on its neighbours.
 *
 * @agent claude-opus-5-5
 */
public object EmptyBlockGetter : BlockGetter {
    override fun getBlockState(pos: BlockPos): BlockState = Blocks.AIR.defaultBlockState()

    override fun getFluidState(pos: BlockPos): FluidState = FluidState.EMPTY

    override val minY: Int get() = 0

    override val height: Int get() = 0
}

/**
 * Where [from] to [to] first enters this shape placed at [pos]; null when it misses.
 *
 * @agent claude-opus-5-5
 */
public fun VoxelShape.clip(from: Vec3, to: Vec3, pos: BlockPos): BlockHitResult? {
    if (isEmpty) return null
    val delta = to.subtract(from)
    if (delta.lengthSqr() < 1.0E-7) return null
    val past = from.add(delta.scale(0.001))
    for (box in toAabbs()) {
        if (box.move(pos).contains(past)) {
            return BlockHitResult(past, Direction.getApproximateNearest(delta.x, delta.y, delta.z).opposite, pos, true)
        }
    }
    val clip = Clip(from, to)
    for (box in toAabbs()) clip.box(box.move(pos))
    val face = clip.face ?: return null
    return BlockHitResult(clip.location(), face, pos, false)
}

/**
 * A level with its entities.
 *
 * @agent claude-opus-5-5
 */
public interface Level : BlockGetter {

    public fun players(): List<net.ccbluex.agent.world.entity.Player>

    public fun getEntity(id: Int): Entity?

    /**
     * The entities whose boxes touch [area], but [except], that pass [predicate].
     */
    public fun getEntities(except: Entity?, area: AABB, predicate: (Entity) -> Boolean): List<Entity>

    public fun <T : Entity> getEntitiesOfClass(
        type: Class<T>,
        area: AABB,
        predicate: (T) -> Boolean = { true },
    ): List<T>

    public val worldBorder: WorldBorder

    /**
     * The rules the game running this level moves and fights by.
     */
    public val mechanics: Mechanics
        get() = Mechanics.MODERN

    /**
     * The dimension's id, such as `minecraft:the_nether`.
     */
    public fun dimension(): String

    public fun hasCeiling(): Boolean

    public fun hasChunk(chunkX: Int, chunkZ: Int): Boolean

    public fun hasChunkAt(x: Int, z: Int): Boolean = hasChunk(x shr 4, z shr 4)

    public fun hasChunkAt(pos: BlockPos): Boolean = hasChunkAt(pos.x, pos.z)

    public fun isLoaded(pos: BlockPos): Boolean = hasChunkAt(pos)

    /**
     * The y above the highest block that blocks motion or holds liquid, as the game's MOTION_BLOCKING height map.
     */
    public fun getHeight(x: Int, z: Int): Int = getHeight(Heightmap.MOTION_BLOCKING, x, z)

    /**
     * The y above the highest block [heightmap] counts; an adapter without that map answers the plain one.
     */
    public fun getHeight(heightmap: Heightmap, x: Int, z: Int): Int

    /**
     * Whether it is night or dark enough for monsters to spawn out in the open, as the game tells it.
     */
    public val isDarkOutside: Boolean

    /**
     * Whether it is day and the sky bright, as the game tells it.
     */
    public val isBrightOutside: Boolean

    /**
     * The collision shapes of the blocks [box] touches, moved to where they are, as [entity] meets them.
     */
    public fun getBlockCollisions(entity: Entity?, box: AABB): Iterable<VoxelShape>

    public fun noBlockCollision(entity: Entity?, box: AABB): Boolean =
        !getBlockCollisions(entity, box).iterator().hasNext()

    /**
     * The positions of the block entities of kind [type] in the loaded chunk [chunkX] [chunkZ].
     */
    public fun blockEntities(chunkX: Int, chunkZ: Int, type: BlockEntityType): List<BlockPos>

    /**
     * The positions of the blocks from [minY] to [maxY] in the loaded chunk [chunkX] [chunkZ] whose state [test]
     * accepts; empty where the chunk is not loaded. Where the game keeps a palette per section, sections it rules out
     * are skipped before any block is read.
     */
    public fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, test: (BlockState) -> Boolean): List<BlockPos>

    /**
     * The text of every sign in the loaded chunk [chunkX] [chunkZ], one string per line, by the sign's position.
     */
    public fun signs(chunkX: Int, chunkZ: Int): Map<BlockPos, List<String>>

    /**
     * The state [stack] places when clicked on [hit] by a sneaking player looking with [rotation], as the game decides
     * it, so no block clicked on takes the click instead; null where it places no block or the adapter does not tell.
     */
    public fun placedState(stack: ItemStack, hit: BlockHitResult, rotation: ViewRotation): BlockState? = null

    /**
     * Where players spawn when they have no bed.
     */
    public val respawnPos: BlockPos

    /**
     * A copy of the blocks from [minX] [minY] [minZ] to [maxX] [maxY] [maxZ] for a search off the game thread; take it
     * on the game thread.
     */
    public fun snapshot(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): LevelBlocks

    public companion object {
        public const val OVERWORLD: String = "minecraft:overworld"
        public const val NETHER: String = "minecraft:the_nether"
        public const val END: String = "minecraft:the_end"
    }
}

/**
 * @agent claude-opus-5-5
 */
public interface WorldBorder {
    public val minX: Double
    public val minZ: Double
    public val maxX: Double
    public val maxZ: Double
    public val centerX: Double get() = (minX + maxX) / 2.0
    public val centerZ: Double get() = (minZ + maxZ) / 2.0

    /**
     * Whether the border is closing in.
     */
    public val isShrinking: Boolean

    public fun isWithinBounds(x: Double, z: Double): Boolean = x >= minX && x < maxX && z >= minZ && z < maxZ

    public fun isWithinBounds(pos: BlockPos): Boolean = isWithinBounds(pos.x.toDouble(), pos.z.toDouble())
}

/**
 * A ray to trace through blocks: against what stops movement ([Block.COLLIDER]) or what a click hits
 * ([Block.OUTLINE]), through fluids too with [Fluid.ANY], where the game's adapter knows them. [entity] is the one
 * whose collisions count, for blocks that let some through.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class ClipContext(
    public val from: Vec3,
    public val to: Vec3,
    public val block: Block,
    public val entity: Entity?,
    public val fluid: Fluid = Fluid.NONE,
) {
    /**
     * @agent claude-opus-5-5
     */
    public enum class Block { COLLIDER, OUTLINE }

    /**
     * @agent claude-sonnet-5-5
     */
    public enum class Fluid { NONE, SOURCE_ONLY, ANY }
}

/**
 * @agent claude-opus-5-5
 */
public sealed class HitResult(public val location: Vec3) {
    public abstract val type: Type

    /**
     * @agent claude-opus-5-5
     */
    public enum class Type { MISS, BLOCK, ENTITY }
}

/**
 * @agent claude-opus-5-5
 */
public class BlockHitResult(
    location: Vec3,
    public val direction: Direction,
    public val blockPos: BlockPos,
    public val isInside: Boolean,
    private val miss: Boolean = false,
) : HitResult(location) {

    override val type: Type get() = if (miss) Type.MISS else Type.BLOCK

    public fun withPosition(pos: BlockPos): BlockHitResult = BlockHitResult(location, direction, pos, isInside, miss)

    public companion object {
        @JvmStatic
        public fun miss(location: Vec3, direction: Direction, pos: BlockPos): BlockHitResult =
            BlockHitResult(location, direction, pos, false, true)
    }
}

/**
 * @agent claude-opus-5-5
 */
public class EntityHitResult(public val entity: Entity, location: Vec3) : HitResult(location) {
    override val type: Type get() = Type.ENTITY
}

/**
 * An open container's slots, as the server numbers them.
 *
 * @agent claude-opus-5-5
 */
public interface ContainerMenu {
    public val slots: List<Slot>

    /**
     * What the cursor holds.
     */
    public val carried: net.ccbluex.agent.world.item.ItemStack

    public companion object {
        /**
         * The slot index of a click outside the menu, which drops what the cursor holds.
         */
        public const val SLOT_CLICKED_OUTSIDE: Int = -999
    }
}

/**
 * A crafting grid: the own inventory's 2 by 2 or a crafting table's 3 by 3, with the slot of the result.
 *
 * @agent claude-sonnet-5-5
 */
public interface CraftingMenu : ContainerMenu {
    public val resultSlot: Slot
    public val inputGridSlots: List<Slot>
    public val gridWidth: Int
    public val gridHeight: Int
}

/**
 * A furnace, blast furnace or smoker, with the input, fuel and result slots at 0, 1 and 2.
 *
 * @agent claude-sonnet-5-5
 */
public interface FurnaceMenu : ContainerMenu

/**
 * How the game's height maps count blocks; each answers the y above the highest block it counts.
 *
 * @agent claude-sonnet-5-5
 */
public enum class Heightmap { MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES }

/**
 * A slot of a [ContainerMenu]: [index] in the menu, [isPlayerInventory] for the player's own slots below the
 * container's.
 *
 * @agent claude-opus-5-5
 */
public interface Slot {
    public val index: Int
    public val item: net.ccbluex.agent.world.item.ItemStack
    public val isPlayerInventory: Boolean

    /**
     * The index in the container the slot belongs to, such as 0 to 35 for the main inventory.
     */
    public val containerSlot: Int
}

/**
 * The click kinds of a container.
 *
 * @agent claude-opus-5-5
 */
public enum class ContainerInput { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL }
