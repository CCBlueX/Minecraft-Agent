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
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.LevelBlocks
import net.ccbluex.agent.world.LiveLevel
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * Searches routes for a navigator whose goal is given anew every tick. Goals beyond half the route length or in
 * chunks not loaded are searched in stages. The owner reports changes of [level] with [blockChanged] and
 * [levelChanged].
 *
 * From LiquidBounce: [Replanner.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/Replanner.kt#L36-L159)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public class Replanner<T>(
    private val level: () -> Level?,
    private val verticalMargin: Int,
    newTerrain: (LevelBlocks) -> Terrain = ::Terrain,
    executor: Executor = ForkJoinPool.commonPool(),
) {

    private val background = BackgroundSearch<T>(level, newTerrain, executor)

    public var ticks: Int = 0
        private set
    private var lastRequest = Int.MIN_VALUE / 2
    private var retryAt = 0

    private var requested: Request? = null

    private var routed: Request? = null

    public var failed: Boolean = false
        private set

    /**
     * Whether the route in use ends at a stage on the way to the goal.
     */
    public var endsAtStage: Boolean = false
        private set

    public val isSearching: Boolean
        get() = !background.isIdle

    public val preview: List<Vec3>?
        get() = background.preview

    public fun tick() {
        ticks++
    }

    /**
     * The result of the search, once. After a route was found, tell where it ends with [routeEnds] or [forget] it.
     */
    public fun poll(): Outcome<T> {
        val result = background.poll()
        when (result) {
            Outcome.Searching -> return result

            is Outcome.Failed -> {
                failed = true
                retryAt = ticks + RETRY_TICKS
            }

            else -> {
                failed = false
                endsAtStage = false
                routed = requested
            }
        }

        background.cancel()
        return result
    }

    public fun routeEnds(end: BlockPos) {
        endsAtStage = routed?.stage?.isGoal(end) == false
    }

    public fun forget() {
        routed = null
    }

    public fun hasGoalMoved(goal: Vec3): Boolean = !routed.isFor(goal)

    public fun isNewGoal(goal: Vec3): Boolean = !requested.isFor(goal)

    /**
     * A new search waits [REPLAN_INTERVAL] ticks after the last one unless [urgent], [RETRY_TICKS] after a failure.
     */
    public fun isDue(urgent: Boolean): Boolean =
        ticks >= retryAt && (urgent || !isSearching && ticks - lastRequest >= REPLAN_INTERVAL)

    /**
     * Searches routes of at most [maxLength] from [start] to [goal], or to a stage [stageRange] out on the way when
     * the goal is further than half of [maxLength] or not loaded. A running search for the same start block, goal and
     * [variant] goes on. Does nothing without a level.
     */
    public fun request(
        start: Vec3,
        goal: NearDestination,
        maxLength: Double,
        variant: Any? = null,
        // A stage with its detours stays within the route length.
        stageRange: Double = maxLength / 2.0,
        search: (terrain: Terrain, goal: Destination) -> Search<T>,
    ) {
        val level = level() ?: return
        val staged = StageDestination.isNeeded(LiveLevel(level), start, goal.center, maxLength / 2.0)
        val region = if (staged) {
            StageDestination.region(start, stageRange, verticalMargin)
        } else {
            goal.region(start, maxLength, verticalMargin)
        }

        background.request(SearchKey(BlockPos.containing(start), goal, staged, variant), region) { terrain ->
            search(terrain, if (staged) StageDestination(goal, start, stageRange, terrain) else goal)
        }

        if (isNewGoal(goal.center)) {
            failed = false
        }
        requested = Request(goal.center, goal.takeIf { staged })
        lastRequest = ticks
    }

    public fun reset() {
        background.cancel()
        requested = null
        routed = null
        failed = false
        endsAtStage = false
    }

    /**
     * A block changed, alone or as part of a section update.
     */
    public fun blockChanged(pos: BlockPos) {
        background.blockChanged(pos)
    }

    /**
     * The level was left or replaced.
     */
    public fun levelChanged() {
        background.levelChanged()
    }

    /**
     * @agent claude-fable-5-2
     * @agent claude-opus-5-5
     */
    private fun Request?.isFor(goal: Vec3): Boolean {
        val request = this ?: return false
        return request.goal.distanceToSqr(goal) < GOAL_MOVED * GOAL_MOVED
    }

    private class Request(val goal: Vec3, val stage: NearDestination?)

    private data class SearchKey(val start: BlockPos, val goal: NearDestination, val staged: Boolean, val variant: Any?)

    public companion object {
        /**
         * Blocks before the end of a stage at which the next one is searched, so the navigator does not stop there.
         */
        public const val STAGE_LEAD: Double = 12.0

        private const val GOAL_MOVED = 1.5
        private const val REPLAN_INTERVAL = 5
        private const val RETRY_TICKS = 20
    }

}
