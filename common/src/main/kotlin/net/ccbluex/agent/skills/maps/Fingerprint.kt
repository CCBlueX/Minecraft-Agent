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

package net.ccbluex.agent.skills.maps

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot

/**
 * Tells a map by its blocks: blocks sampled around a place, compared with what the level holds there now. Only
 * loaded chunks count, and only blocks, never air: a room in the void tells as much as a valley.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal object Fingerprint {

    /**
     * Up to [SAMPLES] blocks spread over a 3-apart grid 36 wide and 18 high around [center]; air and unloaded chunks
     * are left out.
     */
    fun sample(level: Level, center: BlockPos): List<KnownMap.Sample> {
        val blocks = ArrayList<KnownMap.Sample>()
        for (dx in -18..18 step 3) for (dz in -18..18 step 3) for (dy in -6..12 step 3) {
            val x = center.x + dx
            val y = center.y + dy
            val z = center.z + dz
            blockAt(level, x, y, z)?.takeIf { it !in AIR }?.let { blocks += KnownMap.Sample(x, y, z, it) }
        }
        val every = (blocks.size + SAMPLES - 1) / SAMPLES
        return if (every <= 1) blocks else blocks.filterIndexed { index, _ -> index % every == 0 }
    }

    /**
     * The block id at a place, or null where the chunk is not loaded or the place is outside the level.
     */
    fun blockAt(level: Level, x: Int, y: Int, z: Int): String? {
        if (y < level.minY || y > level.maxY || !level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return null
        }
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos(x, y, z)).block).toString()
    }

    /**
     * How alike [samples] are to the level now, from 0 to 1, with how many places were compared. [blockAt] is null
     * for places not loaded.
     */
    fun similarity(samples: List<KnownMap.Sample>, blockAt: (Int, Int, Int) -> String?): Pair<Double, Int> {
        var compared = 0
        var same = 0
        for (sample in samples) {
            val now = blockAt(sample.x, sample.y, sample.z) ?: continue
            if (sample.block in AIR && now in AIR) continue
            compared++
            if (now == sample.block) same++
        }
        return (if (compared == 0) 0.0 else same.toDouble() / compared) to compared
    }

    /**
     * The text of every sign within [radius] blocks of [center], each sign's lines joined by spaces.
     */
    fun signs(level: Level, center: BlockPos, radius: Int): List<KnownMap.Sign> {
        val signs = ArrayList<KnownMap.Sign>()
        val from = SectionPos.blockToSectionCoord(center.x - radius)..SectionPos.blockToSectionCoord(center.x + radius)
        val to = SectionPos.blockToSectionCoord(center.z - radius)..SectionPos.blockToSectionCoord(center.z + radius)
        for (cx in from) for (cz in to) {
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
            for (entity in chunk.blockEntities.values) {
                val sign = entity as? SignBlockEntity ?: continue
                if (!sign.blockPos.closerThan(center, radius.toDouble())) continue
                val text = SignTextSlot.entries.flatMap { sign.getText(it).getMessages(false) }
                    .joinToString(" ") { (ChatFormatting.stripFormatting(it.string) ?: "").trim() }.trim().replace(SPACES, " ")
                if (text.isNotEmpty()) {
                    signs += KnownMap.Sign(sign.blockPos.x, sign.blockPos.y, sign.blockPos.z, text)
                }
            }
        }
        return signs
    }

    private val AIR = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")
    private const val SAMPLES = 96
    private val SPACES = Regex("""\s+""")
}
