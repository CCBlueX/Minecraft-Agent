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

package net.ccbluex.agent.navigation.walk

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.captureWalk
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Replanner
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

/**
 * Keeps the walking path of a navigator searched: asks for a new one when the goal moves, the walk leaves the path
 * or gets stuck, and hands found ones to [follower].
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L481-L575)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal class Planning(private val context: NavigationContext, private val follower: Follower) {

    private val routes = Replanner<Route>({ context.body.level }, VERTICAL_MARGIN, { Terrain(it, canOpenDoors = true) })
    private val stuckSpots = StuckSpots()

    /**
     * Searches in a row that found nothing; each widens the next one.
     */
    private var failures = 0
    private var stuck = false

    /**
     * Whether a stuck spot not avoided yet came up, which restarts a running search.
     */
    private var newStuckSpot = false
    private var wasOffPath = false

    /**
     * When a search last found nothing closer to walk to.
     */
    private var settledAt: Int? = null

    val isSearching: Boolean
        get() = routes.isSearching

    val failed: Boolean
        get() = routes.failed

    val preview: List<Vec3>?
        get() = routes.preview

    /**
     * Whether the last search found nothing closer to walk to.
     */
    val isSettled: Boolean
        get() = settledAt != null

    fun tick(goal: Vec3) {
        routes.tick()
        stuckSpots.tick(goal)
    }

    /**
     * Searches avoid [pos] for a while. A new spot makes the next search due at once; the same spot again waits for
     * the running one, which a restart every few ticks would never let finish.
     */
    fun stuckAt(pos: BlockPos) {
        newStuckSpot = stuckSpots.add(pos) || newStuckSpot
        stuck = true
    }

    /**
     * For a walk without a path.
     */
    fun drop() {
        routes.reset()
        stuck = false
        newStuckSpot = false
    }

    fun reset() {
        routes.reset()
        failures = 0
        stuck = false
        newStuckSpot = false
        wasOffPath = false
        settledAt = null
    }

    fun forgetStuckSpots() {
        stuckSpots.clear()
    }

    fun blockChanged(pos: BlockPos) {
        routes.blockChanged(pos)
    }

    fun levelChanged() {
        routes.levelChanged()
    }

    fun collect(position: Vec3) {
        when (val result = routes.poll()) {
            Outcome.Searching -> Unit

            Outcome.AlreadyThere -> {
                follower.clear()
                settledAt = routes.ticks
            }

            is Outcome.Found -> accept(result.value, position)

            is Outcome.Failed -> {
                failures++
                if (follower.isFinished) {
                    follower.clear()
                }
            }
        }
    }

    private fun accept(path: Route, position: Vec3) {
        failures = 0
        stuck = false
        if (path.steps.isEmpty()) {
            // Nothing closer to the goal can be reached from here.
            follower.clear()
            settledAt = routes.ticks
            return
        }

        // The player moved on while searching; the next search starts where the player is now.
        val startIndex = joinIndex(path, position) ?: return routes.forget()

        routes.routeEnds(path.steps.last().to)
        settledAt = null
        follower.follow(path, startIndex)
        context.logger.debug("Following a path of {} steps to {}", path.steps.size, path.end)
        context.listener.routeFound(path)
    }

    private fun joinIndex(path: Route, position: Vec3): Int? {
        val points = path.points
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        for (index in 0 until min(points.size, JOIN_POINTS)) {
            val point = points[index]
            val horizontal = position.horizontalDistanceTo(point)
            val near = horizontal <= JOIN_DISTANCE && abs(position.y - point.y) <= JOIN_DISTANCE
            if (near && horizontal < bestDistance) {
                best = index
                bestDistance = horizontal
            }
        }
        return best
    }

    fun planIfNeeded(goal: Vec3, radius: Double, position: Vec3) {
        val path = follower.path
        val offPath = follower.isOffPath()
        val leftPath = offPath && !wasOffPath
        wasOffPath = offPath

        val finishedShort = path != null && follower.isFinished
        val nearStage = path != null && routes.endsAtStage &&
            position.horizontalDistanceTo(path.end) < Replanner.STAGE_LEAD
        val settled = settledAt.let { it != null && routes.ticks - it < SETTLE_TICKS }

        val needed = (path == null && !settled) || routes.hasGoalMoved(goal) || offPath || finishedShort || stuck ||
            nearStage
        if (needed && routes.isDue(urgent = newStuckSpot || leftPath)) {
            request(goal, radius, position)
            stuck = false
            newStuckSpot = false
        }
    }

    private fun request(goal: Vec3, radius: Double, start: Vec3) {
        if (routes.isNewGoal(goal)) {
            failures = 0
        }
        // After searches that found nothing, the next ones look further around for a detour.
        val maxLength = context.options().maxLength * (1 shl min(failures, MAX_WIDENING))
        val abilities = context.captureWalk(stuckSpots.blocks)
        // Every jump considered is simulated, which makes routes with them slower to work out.
        val limits = if (abilities.canParkour) {
            Limits(maxLength, maxVisitedNodes = PARKOUR_EXPANSIONS, maxComputeNanos = PARKOUR_COMPUTE_NANOS)
        } else {
            Limits(maxLength)
        }

        routes.request(start, NearDestination(goal, radius), maxLength, stuckSpots.version) { terrain, searchGoal ->
            RouteSearch(terrain, start, searchGoal, abilities, limits)
        }

        if (follower.path == null && !routes.failed) {
            context.route.searching(start, goal, radius)
        }
    }

    private companion object {
        const val VERTICAL_MARGIN = 16
        const val SETTLE_TICKS = 100
        const val JOIN_POINTS = 8
        const val JOIN_DISTANCE = 1.0
        const val PARKOUR_EXPANSIONS = 40_000
        const val MAX_WIDENING = 2
        val PARKOUR_COMPUTE_NANOS = TimeUnit.SECONDS.toNanos(2)
    }
}

/**
 * Feet blocks the walk got stuck at, which searches avoid until [FORGET_TICKS] passed or the goal changed.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L646-L681)
 *
 * @agent claude-fable-5-2
 */
internal class StuckSpots {

    val blocks = LongOpenHashSet()

    /**
     * Changes with [blocks], so a search for the set before is not kept.
     */
    var version = 0
        private set

    private var goal: Vec3? = null
    private var age = 0

    /**
     * Whether [pos] was not avoided yet.
     *
     * @agent claude-opus-5-5
     */
    fun add(pos: BlockPos): Boolean {
        if (!blocks.add(pos.asLong())) return false
        version++
        return true
    }

    fun clear() = blocks.clear()

    fun tick(goal: Vec3) {
        val outdated = blocks.isNotEmpty() &&
            (++age > FORGET_TICKS || this.goal.let { it == null || it.distanceToSqr(goal) > Mth.square(GOAL_CHANGE) })
        if (outdated) {
            blocks.clear()
            version++
        }

        if (blocks.isEmpty()) {
            this.goal = goal
            age = 0
        }
    }

    private companion object {
        const val GOAL_CHANGE = 8.0
        const val FORGET_TICKS = 600
    }

}
