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

package net.ccbluex.agent.v1_8_8.spigot

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.LevelBlocks
import net.ccbluex.agent.world.Mechanics
import net.ccbluex.agent.world.WorldBorder
import net.ccbluex.agent.world.block.BlockEntityType
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.Shapes
import net.ccbluex.agent.world.block.VoxelShape
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import net.minecraft.server.v1_8_R3.AxisAlignedBB
import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.Chunk
import net.minecraft.server.v1_8_R3.EnumWorldBorderState
import net.minecraft.server.v1_8_R3.IInventory
import net.minecraft.server.v1_8_R3.TileEntity
import net.minecraft.server.v1_8_R3.TileEntityChest
import net.minecraft.server.v1_8_R3.TileEntitySign
import net.minecraft.server.v1_8_R3.World
import net.minecraft.server.v1_8_R3.WorldServer
import java.util.Collections
import java.util.WeakHashMap

/**
 * A 1.8 world as the agents see it, read live on the server thread. Unloaded chunks read as air and stay unloaded.
 *
 * @agent claude-opus-5-5
 */
internal class LevelView private constructor(val handle: World) : Level {

    override val mechanics: Mechanics get() = Mechanics.V1_8

    override val minY: Int get() = 0
    override val height: Int get() = WORLD_HEIGHT

    // Only server worlds have bots in them
    private val chunks get() = (handle as WorldServer).chunkProviderServer

    private fun chunk(x: Int, z: Int): Chunk? = chunks.getChunkIfLoaded(x shr 4, z shr 4)

    override fun getBlockState(pos: BlockPos): BlockState {
        if (pos.y !in 0 until WORLD_HEIGHT) return GameTypes.state(0)
        val chunk = chunk(pos.x, pos.z) ?: return GameTypes.state(0)
        return GameTypes.state(chunk.getBlockData(BlockPosition(pos.x, pos.y, pos.z)))
    }

    override fun getBlockEntity(pos: BlockPos): BlockEntityType? {
        val chunk = chunk(pos.x, pos.z) ?: return null
        return chunk.tileEntities[BlockPosition(pos.x, pos.y, pos.z)]?.type()
    }

    override fun players(): List<Player> = handle.players.map { it.toAgent() }

    override fun getEntity(id: Int): Entity? = handle.a(id)?.toAgent()

    override fun getEntities(except: Entity?, area: AABB, predicate: (Entity) -> Boolean): List<Entity> =
        handle.getEntities((except as? EntityView)?.handle, area.toMinecraft()).map { it.toAgent() }.filter(predicate)

    override fun <T : Entity> getEntitiesOfClass(type: Class<T>, area: AABB, predicate: (T) -> Boolean): List<T> =
        handle.getEntities(null, area.toMinecraft()).map { it.toAgent() }.filterIsInstance(type).filter(predicate)

    override val worldBorder: WorldBorder = object : WorldBorder {
        override val minX: Double get() = handle.worldBorder.b()
        override val minZ: Double get() = handle.worldBorder.c()
        override val maxX: Double get() = handle.worldBorder.d()
        override val maxZ: Double get() = handle.worldBorder.e()
        override val isShrinking: Boolean get() = handle.worldBorder.state == EnumWorldBorderState.SHRINKING
    }

    override fun dimension(): String = when (handle.worldProvider.dimension) {
        NETHER_ID -> Level.NETHER
        END_ID -> Level.END
        else -> Level.OVERWORLD
    }

    override fun hasCeiling(): Boolean = handle.worldProvider.dimension == NETHER_ID

    override fun hasChunk(chunkX: Int, chunkZ: Int): Boolean = chunks.isChunkLoaded(chunkX, chunkZ)

    // 1.8 has one height map, the highest block that lets no light through, close to MOTION_BLOCKING
    override fun getHeight(x: Int, z: Int): Int = chunk(x, z)?.b(x and 15, z and 15) ?: 0

    override fun getBlockCollisions(entity: Entity?, box: AABB): Iterable<VoxelShape> {
        val shapes = ArrayList<VoxelShape>()
        for (x in Mth.floor(box.minX)..Mth.floor(box.maxX)) {
            for (z in Mth.floor(box.minZ)..Mth.floor(box.maxZ)) {
                // Fences reach 0.5 above their block
                for (y in Mth.floor(box.minY) - 1..Mth.floor(box.maxY)) {
                    val state = getBlockState(BlockPos(x, y, z)) as StateView
                    if (state.isAir) continue
                    val shape = state.shapeAt(handle, x, y, z)
                    if (shape.isEmpty) continue
                    val moved = shape.move(x.toDouble(), y.toDouble(), z.toDouble())
                    if (moved.toAabbs().any { it.intersects(box) }) shapes += moved
                }
            }
        }
        return shapes
    }

    override fun blockEntities(chunkX: Int, chunkZ: Int, type: BlockEntityType): List<BlockPos> {
        val chunk = chunks.getChunkIfLoaded(chunkX, chunkZ) ?: return emptyList()
        return chunk.tileEntities.filter { (_, entity) -> entity.type() == type }.map { (pos, _) -> BlockPos(pos.x, pos.y, pos.z) }
    }

    override fun findBlocks(chunkX: Int, chunkZ: Int, minY: Int, maxY: Int, test: (BlockState) -> Boolean): List<BlockPos> {
        val chunk = chunks.getChunkIfLoaded(chunkX, chunkZ) ?: return emptyList()
        val found = ArrayList<BlockPos>()
        for (section in chunk.sections) {
            if (section == null || section.a() || section.yPosition + 15 < minY || section.yPosition > maxY) continue
            val ids = section.idArray
            for (index in ids.indices) {
                val id = ids[index].code
                val y = section.yPosition + (index shr 8)
                if (id != 0 && y in minY..maxY && test(GameTypes.state(id))) {
                    found += BlockPos((chunkX shl 4) + (index and 15), y, (chunkZ shl 4) + (index shr 4 and 15))
                }
            }
        }
        return found
    }

    override fun signs(chunkX: Int, chunkZ: Int): Map<BlockPos, List<String>> {
        val chunk = chunks.getChunkIfLoaded(chunkX, chunkZ) ?: return emptyMap()
        return chunk.tileEntities.filterValues { it is TileEntitySign }.map { (pos, sign) ->
            BlockPos(pos.x, pos.y, pos.z) to (sign as TileEntitySign).lines.map { it.c() }
        }.toMap()
    }

    override val respawnPos: BlockPos get() = handle.spawn.let { BlockPos(it.x, it.y, it.z) }

    /**
     * Copies the chunk sections of the region, and the shapes of the blocks whose shape depends on their neighbours,
     * on the server thread.
     */
    override fun snapshot(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): LevelBlocks {
        val sections = Long2ObjectOpenHashMap<CharArray?>()
        val shapes = Long2ObjectOpenHashMap<VoxelShape>()
        val minSection = minY.coerceIn(0, WORLD_HEIGHT - 1) shr 4
        val maxSection = maxY.coerceIn(0, WORLD_HEIGHT - 1) shr 4
        for (chunkX in (minX shr 4)..(maxX shr 4)) {
            for (chunkZ in (minZ shr 4)..(maxZ shr 4)) {
                val chunk = chunks.getChunkIfLoaded(chunkX, chunkZ) ?: continue
                for (sectionY in minSection..maxSection) {
                    val section = chunk.sections[sectionY]
                    val ids = if (section == null || section.a()) null else section.idArray.copyOf()
                    sections.put(LevelSnapshot.sectionKey(chunkX, sectionY, chunkZ), ids)
                    if (ids != null) captureShapes(ids, chunkX shl 4, sectionY shl 4, chunkZ shl 4, shapes)
                }
            }
        }
        val border = worldBorder
        return LevelSnapshot(sections, shapes, border.minX, border.maxX, border.minZ, border.maxZ)
    }

    private fun captureShapes(ids: CharArray, baseX: Int, baseY: Int, baseZ: Int, shapes: Long2ObjectOpenHashMap<VoxelShape>) {
        for (index in ids.indices) {
            val id = ids[index].code
            if (id == 0) continue
            val state = GameTypes.state(id)
            if (state.isPlaced) {
                val x = baseX + (index and 15)
                val y = baseY + (index shr 8)
                val z = baseZ + (index shr 4 and 15)
                shapes.put(BlockPos.asLong(x, y, z), state.shapeAt(handle, x, y, z))
            } else if (state.shape == null) {
                state.shapeAt(handle, baseX + (index and 15), baseY + (index shr 8), baseZ + (index shr 4 and 15))
            }
        }
    }

    override fun toString(): String = "LevelView(${handle.worldData.name})"

    companion object {
        const val WORLD_HEIGHT = 256
        private const val NETHER_ID = -1
        private const val END_ID = 1

        private val levels = Collections.synchronizedMap(WeakHashMap<World, LevelView>())

        fun of(world: World): LevelView = levels.getOrPut(world) { LevelView(world) }
    }
}

/**
 * Chunk sections of a 1.8 world copied on the server thread, for route searches on other threads. Blocks whose
 * shape depends on their neighbours keep the shape they had at the copy.
 *
 * @agent claude-opus-5-5
 */
internal class LevelSnapshot(
    private val sections: Long2ObjectOpenHashMap<CharArray?>,
    private val shapes: Long2ObjectOpenHashMap<VoxelShape>,
    private val borderMinX: Double,
    private val borderMaxX: Double,
    private val borderMinZ: Double,
    private val borderMaxZ: Double,
) : LevelBlocks {

    override val minY: Int get() = 0
    override val height: Int get() = LevelView.WORLD_HEIGHT
    override val mechanics: Mechanics get() = Mechanics.V1_8

    override fun isKnown(x: Int, y: Int, z: Int): Boolean =
        y in 0 until LevelView.WORLD_HEIGHT && sections.containsKey(sectionKey(x shr 4, y shr 4, z shr 4))

    override fun isInsideBorder(x: Double, z: Double): Boolean =
        x >= borderMinX && x < borderMaxX && z >= borderMinZ && z < borderMaxZ

    override fun getBlockState(pos: BlockPos): BlockState {
        if (pos.y !in 0 until LevelView.WORLD_HEIGHT) return GameTypes.state(0)
        val ids = sections.get(sectionKey(pos.x shr 4, pos.y shr 4, pos.z shr 4)) ?: return GameTypes.state(0)
        return GameTypes.state(ids[(pos.y and 15) shl 8 or ((pos.z and 15) shl 4) or (pos.x and 15)].code)
    }

    /**
     * The shape a block whose shape depends on its neighbours had at the copy; null for every other block.
     */
    fun placedShape(pos: BlockPos, state: StateView): VoxelShape? =
        if (state.isPlaced) shapes.get(pos.asLong()) else null

    companion object {
        fun sectionKey(chunkX: Int, sectionY: Int, chunkZ: Int): Long = BlockPos.asLong(chunkX, sectionY, chunkZ)
    }
}

/** @agent claude-opus-5-5 */
private fun TileEntity.type(): BlockEntityType = when (this) {
    is TileEntityChest -> BlockEntityType.CHEST
    is IInventory -> BlockEntityType.CONTAINER
    else -> BlockEntityType.OTHER
}

/** @agent claude-opus-5-5 */
private fun AABB.toMinecraft(): AxisAlignedBB = AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)
