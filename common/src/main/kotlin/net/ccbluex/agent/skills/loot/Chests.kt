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

package net.ccbluex.agent.skills.loot

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ChestBlockEntity

/**
 * The chests of a round and which of them the agent opened or gave up on since the last refill. Hosts add the chests
 * they know (a minigame plugin's list, or [scan] of the loaded chunks) and tell about refills.
 *
 * From LiquidBounce: [Chests.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Chests.kt#L61-L77)
 *
 * @agent claude-opus-5-5
 */
public class Chests {

    private val known = LinkedHashSet<BlockPos>()
    private val done = HashSet<BlockPos>()

    /**
     * Every chest known.
     */
    public val all: Set<BlockPos>
        get() = known

    /**
     * Refills since the round started.
     */
    public var refills: Int = 0
        private set

    /**
     * Seconds until the next refill, as the host knows it; null when unknown or none comes.
     */
    public var secondsToRefill: Int? = null

    public fun add(pos: BlockPos) {
        known += pos.immutable()
    }

    public fun remove(pos: BlockPos) {
        known -= pos
        done -= pos
    }

    /**
     * Adds the chests in the loaded chunks within [chunkRadius] chunks of [center].
     */
    public fun scan(level: Level, center: BlockPos, chunkRadius: Int) {
        val chunkX = SectionPos.blockToSectionCoord(center.x)
        val chunkZ = SectionPos.blockToSectionCoord(center.z)
        for (x in chunkX - chunkRadius..chunkX + chunkRadius) {
            for (z in chunkZ - chunkRadius..chunkZ + chunkRadius) {
                val chunk = level.chunkSource.getChunkNow(x, z) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities) {
                    if (blockEntity is ChestBlockEntity) add(pos)
                }
            }
        }
    }

    /**
     * The agent looted [pos]; it stays done until the next [refilled].
     */
    public fun opened(pos: BlockPos) {
        done += pos.immutable()
    }

    /**
     * The agent could not open [pos]; it is left alone until the next [refilled].
     */
    public fun giveUp(pos: BlockPos) {
        done += pos.immutable()
    }

    public fun isOpened(pos: BlockPos): Boolean = pos in done

    public fun unopened(): List<BlockPos> = known.filter { it !in done }

    /**
     * The chests were filled anew, so every one is worth opening again.
     */
    public fun refilled() {
        refills++
        done.clear()
    }

    /**
     * For a new round.
     */
    public fun clear() {
        known.clear()
        done.clear()
        refills = 0
        secondsToRefill = null
    }
}
