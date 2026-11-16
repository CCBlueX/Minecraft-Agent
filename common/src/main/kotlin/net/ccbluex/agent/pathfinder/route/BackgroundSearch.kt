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

package net.ccbluex.agent.pathfinder.route

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.level.BlockRegion
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.LevelBlocks
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * Runs one route search at a time on [executor], a slice per [poll]. The search reads a copy of its region of
 * [level] through the terrain [newTerrain] makes, and starts over when a block it looked at changes. The owner
 * reports changes with [blockChanged] and [levelChanged].
 *
 * From LiquidBounce: [BackgroundSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/BackgroundSearch.kt#L43-L177)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
@InternalAgentApi
public class BackgroundSearch<T>(
    private val level: () -> Level?,
    private val newTerrain: (LevelBlocks) -> Terrain = ::Terrain,
    private val executor: Executor = ForkJoinPool.commonPool(),
) {

    private var job: Job<T>? = null
    private val changes = ArrayList<BlockPos>()
    private var changesOverflowed = false

    public val isIdle: Boolean
        get() = job == null

    /**
     * The best route of the running search so far, see [Search.preview].
     */
    public var preview: List<Vec3>? = null
        private set

    /**
     * Searches with [newSearch] over [region], unless a search for an equal [key] is running or done.
     */
    public fun request(key: Any, region: BlockRegion, newSearch: (Terrain) -> Search<T>) {
        if (job?.key == key) {
            return
        }

        cancel()
        job = start(key, region, newSearch)
    }

    /**
     * Collects the last slice and starts the next one. Call once per tick from the game thread.
     */
    public fun poll(): Outcome<T> {
        var job = job ?: return Outcome.Searching
        job.polls++

        job.slice?.let { slice ->
            if (!slice.isDone) {
                return Outcome.Searching
            }
            job.result = slice.join()
            job.slice = null
            if (job.result == Outcome.Searching) {
                job.search.preview()?.let { preview = it }
            }
        }

        // Blocks changing all the time, like a command block running every tick, would restart the search forever.
        if (job.polls >= MIN_SEARCH_POLLS && applyChanges(job.terrain)) {
            job = start(job.key, job.region, job.newSearch) ?: run {
                cancel()
                return Outcome.Searching
            }
            this.job = job
        }

        if (job.result == Outcome.Searching) {
            job.slice = CompletableFuture.supplyAsync(job.search::advance, executor)
                .exceptionally { throwable ->
                    LOGGER.error("Route search failed", throwable)
                    Outcome.Failed(Failure.NO_PATH)
                }
        }
        return job.result
    }

    public fun cancel() {
        job?.slice?.cancel(false)
        job = null
        preview = null
        changes.clear()
        changesOverflowed = false
    }

    /**
     * A block changed, alone or as part of a section update.
     */
    public fun blockChanged(pos: BlockPos) {
        recordChange(pos.immutable())
    }

    /**
     * The level was left or replaced.
     */
    public fun levelChanged() {
        cancel()
    }

    private fun start(key: Any, region: BlockRegion, newSearch: (Terrain) -> Search<T>): Job<T>? {
        val level = level() ?: return null
        val terrain = newTerrain(
            level.snapshot(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ),
        )
        return Job(key, region, newSearch, terrain, newSearch(terrain))
    }

    /**
     * Whether a recorded change alters how a block the search looked at is moved through.
     */
    private fun applyChanges(terrain: Terrain): Boolean {
        val level = level() ?: return false

        var affected = changesOverflowed
        for (pos in changes) {
            affected = terrain.invalidate(pos, level.getBlockState(pos)) || affected
        }
        changes.clear()
        changesOverflowed = false
        return affected
    }

    private fun recordChange(pos: BlockPos) {
        val region = job?.region ?: return
        if (changesOverflowed || pos !in region) {
            return
        }

        if (changes.size >= MAX_PENDING_CHANGES) {
            changesOverflowed = true
            changes.clear()
        } else {
            changes += pos
        }
    }

    private class Job<T>(
        val key: Any,
        val region: BlockRegion,
        val newSearch: (Terrain) -> Search<T>,
        val terrain: Terrain,
        val search: Search<T>,
    ) {
        var slice: CompletableFuture<Outcome<T>>? = null
        var result: Outcome<T> = Outcome.Searching
        var polls = 0
    }

    private companion object {
        const val MAX_PENDING_CHANGES = 1024
        const val MIN_SEARCH_POLLS = 20
        val LOGGER = LoggerFactory.getLogger("Agent/BackgroundSearch")
    }

}
