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

package net.ccbluex.agent.navigation.glide

import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.glide.AirwaySearch
import net.ccbluex.agent.pathfinder.glide.Passage
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Replanner
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.TimeUnit

/**
 * Searches the routes a [Passage] follows: from where the glide is, again when it drifts off, nears the end of a
 * route that stops where the loaded blocks end, the goal moves, or a while has passed and more blocks are known.
 *
 * @agent claude-opus-5-5
 */
internal class Airways(private val context: NavigationContext) {

    private val routes = Replanner<Route>({ context.body.level }, VERTICAL_MARGIN)
    private var hasRoute = false
    private var reachesGoal = false
    private var foundAt = 0

    /**
     * Hands [passage] the routes found for it so far and searches the next one when due.
     */
    fun tick(passage: Passage, goal: Vec3, radius: Double) {
        routes.tick()
        when (val result = routes.poll()) {
            is Outcome.Found -> {
                val route = result.value
                routes.routeEnds(BlockPos.containing(route.end))
                reachesGoal = route.canReach && !routes.endsAtStage
                accept(passage, route)
            }

            // Close enough that the rest is a straight line
            Outcome.AlreadyThere -> {
                reachesGoal = true
                val position = context.body.player.position()
                accept(passage, Route(position, listOf(position, goal), canReach = true))
            }

            Outcome.Searching, is Outcome.Failed -> Unit
        }

        val urgent = !hasRoute || passage.drift > MAX_DRIFT || routes.hasGoalMoved(goal)
        val due = urgent || !reachesGoal && passage.remaining < END_LEAD || routes.ticks - foundAt > REFRESH_TICKS
        if (!due || !routes.isDue(urgent)) {
            return
        }

        val start = context.body.player.position()
        val clearance = context.options().glideClearance
        // The search ends where the clearance lets the body come; feet on the ground are below that.
        val destination = NearDestination(goal, radius + clearance + 1.0)
        routes.request(start, destination, MAX_LENGTH, clearance) { terrain, searchGoal ->
            AirwaySearch(terrain, start, searchGoal, clearance, LIMITS)
        }
    }

    private fun accept(passage: Passage, route: Route) {
        passage.follow(route, reachesGoal)
        hasRoute = true
        foundAt = routes.ticks
    }

    fun reset() {
        routes.reset()
        hasRoute = false
        reachesGoal = false
    }

    fun blockChanged(pos: BlockPos) {
        routes.blockChanged(pos)
    }

    fun levelChanged() {
        routes.levelChanged()
    }

    private companion object {
        const val VERTICAL_MARGIN = 64
        const val MAX_LENGTH = 320.0
        const val MAX_DRIFT = 4.0
        const val END_LEAD = 64.0
        const val REFRESH_TICKS = 60
        val LIMITS = Limits(
            MAX_LENGTH,
            maxVisitedNodes = 120_000,
            visitedNodesPerSlice = 8_000,
            maxComputeNanos = TimeUnit.MILLISECONDS.toNanos(600),
        )
    }

}
