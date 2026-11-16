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

package net.ccbluex.agent.pathfinder.level

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Copies of chunk sections for route searches, per level, kept until the section changes. The host reports changes
 * with [blockChanged], [columnChanged] and [forget]; every call belongs on the thread that ticks the level.
 *
 * From LiquidBounce: [SectionCache.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/world/SectionCache.kt#L43-L87)
 *
 * @agent claude-opus-5-5
 */
public object SectionCache {

    private const val MAX_SECTIONS = 4096

    private val levels = WeakHashMap<Level, Long2ObjectLinkedOpenHashMap<PalettedContainer<BlockState>?>>()

    public fun capture(level: Level, region: BlockRegion): LevelSnapshot {
        val copies = levels.getOrPut(level) { Long2ObjectLinkedOpenHashMap() }
        val minSectionY = max(region.minY, level.minY) shr 4
        val maxSectionY = min(region.maxY, level.maxY) shr 4
        val sections = Long2ObjectOpenHashMap<PalettedContainer<BlockState>?>()

        for (chunkX in (region.minX shr 4)..(region.maxX shr 4)) {
            for (chunkZ in (region.minZ shr 4)..(region.maxZ shr 4)) {
                val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
                for (sectionY in minSectionY..maxSectionY) {
                    val key = SectionPos.asLong(chunkX, sectionY, chunkZ)
                    val section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY))
                    sections.put(key, copies.copyOf(key, section))
                }
            }
        }

        while (copies.size > MAX_SECTIONS) {
            copies.removeFirst()
        }

        val border = level.worldBorder
        return LevelSnapshot(
            level.minY, level.height, sections,
            border.minX, border.maxX, border.minZ, border.maxZ,
        )
    }

    /**
     * A block changed, alone or as part of a section update.
     */
    public fun blockChanged(level: Level, pos: BlockPos) {
        levels[level]?.remove(SectionPos.asLong(pos))
    }

    /**
     * The chunk column was loaded or unloaded.
     */
    public fun columnChanged(level: Level, chunkX: Int, chunkZ: Int) {
        levels[level]?.keys?.removeIf { SectionPos.x(it) == chunkX && SectionPos.z(it) == chunkZ }
    }

    /**
     * The level was left or replaced.
     */
    public fun forget(level: Level) {
        levels.remove(level)
    }

    // Null for a section of only air.
    private fun Long2ObjectLinkedOpenHashMap<PalettedContainer<BlockState>?>.copyOf(
        key: Long,
        section: LevelChunkSection,
    ): PalettedContainer<BlockState>? {
        if (containsKey(key)) {
            return getAndMoveToLast(key)
        }

        val copy = if (section.hasOnlyAir()) null else section.states.copy()
        putAndMoveToLast(key, copy)
        return copy
    }

}
