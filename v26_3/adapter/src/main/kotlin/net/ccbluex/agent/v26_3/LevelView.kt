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

package net.ccbluex.agent.v26_3

import com.google.common.collect.MapMaker
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.pathfinder.level.BlockRegion
import net.ccbluex.agent.pathfinder.level.SectionCache
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.Heightmap
import net.ccbluex.agent.world.LevelBlocks
import net.ccbluex.agent.world.WorldBorder
import net.ccbluex.agent.world.block.BlockEntityType
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.FluidState
import net.ccbluex.agent.world.block.VoxelShape
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.core.SectionPos
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
import net.minecraft.world.level.border.BorderStatus
import net.minecraft.world.level.levelgen.Heightmap as McHeightmap
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.concurrent.ConcurrentMap
import net.minecraft.core.Direction as McDirection
import net.minecraft.world.item.ItemStack as McItemStack
import net.minecraft.world.level.ClipContext as McClipContext
import net.minecraft.world.level.Level as McLevel
import net.minecraft.world.phys.BlockHitResult as McBlockHitResult

/**
 * A level of the game as the agents see it.
 *
 * @agent claude-opus-5-5
 */
internal class LevelView(val handle: McLevel) : Level, GameBlocks {
    override val blocks: BlockGetter get() = handle

    override fun getBlockState(pos: BlockPos): BlockState = handle.getBlockState(pos.toMinecraft()).toAgent()

    override fun getFluidState(pos: BlockPos): FluidState = handle.getFluidState(pos.toMinecraft()).toAgent()

    override fun getBlockEntity(pos: BlockPos): BlockEntityType? = handle.getBlockEntity(pos.toMinecraft())?.type()

    override val minY: Int get() = handle.minY
    override val height: Int get() = handle.height

    override fun clip(context: ClipContext): BlockHitResult {
        val block = when (context.block) {
            ClipContext.Block.COLLIDER -> McClipContext.Block.COLLIDER
            ClipContext.Block.OUTLINE -> McClipContext.Block.OUTLINE
        }
        val collision = context.entity?.let { CollisionContext.of(it.toMinecraft()) } ?: CollisionContext.empty()
        val fluid = when (context.fluid) {
            ClipContext.Fluid.NONE -> McClipContext.Fluid.NONE
            ClipContext.Fluid.SOURCE_ONLY -> McClipContext.Fluid.SOURCE_ONLY
            ClipContext.Fluid.ANY -> McClipContext.Fluid.ANY
        }
        val game = McClipContext(
            context.from.toMinecraft(), context.to.toMinecraft(), block, fluid, collision,
        )
        return handle.clip(game).toAgent()
    }

    override fun players(): List<Player> = handle.players().map { it.toAgent() }

    override fun getEntity(id: Int): Entity? = handle.getEntity(id)?.toAgent()

    override fun getEntities(except: Entity?, area: AABB, predicate: (Entity) -> Boolean): List<Entity> =
        handle.getEntities(except?.toMinecraft(), area.toMinecraft()).map { it.toAgent() }.filter(predicate)

    override fun <T : Entity> getEntitiesOfClass(type: Class<T>, area: AABB, predicate: (T) -> Boolean): List<T> =
        handle.getEntities(null, area.toMinecraft()).map { it.toAgent() }.filterIsInstance(type).filter(predicate)

    override val worldBorder: WorldBorder = object : WorldBorder {
        override val minX: Double get() = handle.worldBorder.minX
        override val minZ: Double get() = handle.worldBorder.minZ
        override val maxX: Double get() = handle.worldBorder.maxX
        override val maxZ: Double get() = handle.worldBorder.maxZ
        override val isShrinking: Boolean get() = handle.worldBorder.status == BorderStatus.SHRINKING
    }

    override fun dimension(): String = handle.dimension().identifier().toString()

    override fun hasCeiling(): Boolean = handle.dimensionType().hasCeiling()

    override fun hasChunk(chunkX: Int, chunkZ: Int): Boolean = handle.hasChunk(chunkX, chunkZ)

    override fun getHeight(heightmap: Heightmap, x: Int, z: Int): Int = handle.getHeight(
        when (heightmap) {
            Heightmap.MOTION_BLOCKING -> McHeightmap.Types.MOTION_BLOCKING
            Heightmap.MOTION_BLOCKING_NO_LEAVES -> McHeightmap.Types.MOTION_BLOCKING_NO_LEAVES
        },
        x, z,
    )

    override val isDarkOutside: Boolean get() = handle.isDarkOutside

    override val isBrightOutside: Boolean get() = handle.isBrightOutside

    override fun getBlockCollisions(entity: Entity?, box: AABB): Iterable<VoxelShape> =
        handle.getBlockCollisions(entity?.toMinecraft(), box.toMinecraft()).map { it.toAgent() }

    override fun noBlockCollision(entity: Entity?, box: AABB): Boolean =
        handle.noBlockCollision(entity?.toMinecraft(), box.toMinecraft())

    override fun blockEntities(chunkX: Int, chunkZ: Int, type: BlockEntityType): List<BlockPos> {
        val chunk = handle.chunkSource.getChunkNow(chunkX, chunkZ) ?: return emptyList()
        return chunk.blockEntities.filter { (_, entity) -> entity.type() == type }.map { (pos, _) -> pos.toAgent() }
    }

    override fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, test: (BlockState) -> Boolean): List<BlockPos> {
        val chunk = handle.chunkSource.getChunkNow(chunkX, chunkZ) ?: return emptyList()
        val found = ArrayList<BlockPos>()
        for ((index, section) in chunk.sections.withIndex()) {
            val baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index))
            if (baseY + 15 < minY || baseY > maxY || !section.maybeHas { test(it.toAgent()) }) continue
            for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                if (baseY + y in minY..maxY && test(section.getBlockState(x, y, z).toAgent())) {
                    found += BlockPos(SectionPos.sectionToBlockCoord(chunkX) + x, baseY + y, SectionPos.sectionToBlockCoord(chunkZ) + z)
                }
            }
        }
        return found
    }

    override fun signs(chunkX: Int, chunkZ: Int): Map<BlockPos, List<String>> {
        val chunk = handle.chunkSource.getChunkNow(chunkX, chunkZ) ?: return emptyMap()
        return chunk.blockEntities.values.filterIsInstance<SignBlockEntity>().associate { sign ->
            sign.blockPos.toAgent() to SignTextSlot.entries.flatMap { sign.getText(it).getMessages(false) }.map { it.string }
        }
    }

    override fun placedState(stack: ItemStack, hit: BlockHitResult, rotation: ViewRotation): BlockState? {
        val game = stack.toMinecraft()
        val item = game.item as? BlockItem ?: return null
        val context = item.updatePlacementContext(LookingContext(handle, game, hit.toMinecraft(), rotation)) ?: return null
        return item.block.getStateForPlacement(context)?.toAgent()
    }

    override val respawnPos: BlockPos get() = handle.levelData.respawnData.pos().toAgent()

    override fun snapshot(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): LevelBlocks =
        SectionCache.capture(handle, BlockRegion(minX, minY, minZ, maxX, maxY, maxZ))

    override fun toString(): String = handle.toString()
}

/**
 * A placement as a player looking with [rotation] makes it, without moving anyone.
 *
 * @agent claude-opus-5-5
 */
private class LookingContext(level: McLevel, stack: McItemStack, hit: McBlockHitResult, private val rotation: ViewRotation) :
    BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, hit) {

    override fun getHorizontalDirection(): McDirection = McDirection.fromYRot(rotation.yRot.toDouble())

    override fun getRotation(): Float = rotation.yRot

    override fun getNearestLookingVerticalDirection(): McDirection =
        if (rotation.xRot < 0f) McDirection.UP else McDirection.DOWN

    override fun getNearestLookingDirection(): McDirection = nearest()[0]

    override fun getNearestLookingDirections(): Array<McDirection> {
        val nearest = nearest()
        if (replacingClickedOnBlock()) return nearest
        // As in vanilla, the block clicked on comes first
        val opposite = clickedFace.opposite
        return arrayOf(opposite) + nearest.filter { it != opposite }
    }

    private fun nearest(): Array<McDirection> {
        val view = rotation.viewVector
        return McDirection.entries.sortedByDescending { view.x * it.stepX + view.y * it.stepY + view.z * it.stepZ }
            .toTypedArray()
    }
}

private fun BlockEntity.type(): BlockEntityType = when (this) {
    is ChestBlockEntity -> BlockEntityType.CHEST
    is Container -> BlockEntityType.CONTAINER
    else -> BlockEntityType.OTHER
}

private val levels: ConcurrentMap<McLevel, Level> = MapMaker().weakKeys().makeMap()

/**
 * The library's view of [this]; the same instance every time.
 *
 * @agent claude-opus-5-5
 */
public fun McLevel.toAgent(): Level = levels.computeIfAbsent(this, ::LevelView)

/**
 * The game's level behind [this].
 *
 * @agent claude-opus-5-5
 */
public fun Level.toMinecraft(): McLevel = (this as LevelView).handle

/**
 * [Terrain.live] over the game's [level]; only for the thread that ticks it.
 *
 * @agent claude-opus-5-5
 */
public fun Terrain.Companion.live(level: McLevel): Terrain = Terrain.live(level.toAgent())
