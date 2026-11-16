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

package net.ccbluex.agent.navigation.flight

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.FollowState
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.flight.FlyAbilities
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.flight.RouteSearch
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Replanner
import net.ccbluex.agent.world.Input

/**
 * Flies to goals for a walking navigator while the player can fly, see [captureFlight].
 *
 * From LiquidBounce: [Flying.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/Flying.kt#L39-L145), [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L389-L443)
 *
 * @agent claude-opus-5-5
 */
internal class Flying(private val context: NavigationContext) {

    private val routes = Replanner<Route>({ context.body.level }, VERTICAL_MARGIN)

    /**
     * How the flight answers the keys, kept across routes.
     */
    val handling = Handling()
    private val follower = Follower(context, handling)
    private var stuck = false

    /**
     * Whether the last tick was flown.
     */
    var isActive = false
        private set

    val target: Vec3?
        get() = follower.target

    val sprint: Boolean
        get() = follower.sprint

    /**
     * Flies one tick towards [goal], within [radius], searching routes up to [maxLength] long.
     */
    fun navigate(
        goal: Vec3,
        radius: Double,
        maxLength: Double,
        abilities: FlyAbilities,
        frameYRot: Float,
    ) {
        routes.tick()
        isActive = true
        val position = context.body.player.position()
        collectRoute(goal, position)

        val route = context.route
        val path = follower.path
        if (path != null && !follower.isFinished) {
            if (follower.tick(frameYRot, abilities) == FollowState.STUCK) {
                stuck = true
            }
            route.following(path.points, follower)
        } else {
            follower.clear()
            context.body.press(Input.EMPTY, true)
            when {
                routes.failed -> route.invalid()
                routes.isSearching -> route.searching(position, goal, radius, routes.preview)
                else -> route.done()
            }
        }

        planIfNeeded(goal, radius, maxLength, position, abilities)
    }

    /**
     * Stops flying routes until the next [navigate].
     */
    fun halt() {
        routes.reset()
        follower.clear()
        isActive = false
        stuck = false
    }

    fun blockChanged(pos: BlockPos) {
        routes.blockChanged(pos)
    }

    fun levelChanged() {
        routes.levelChanged()
    }

    /**
     * @agent claude-opus-5-5
     * @agent claude-fable-5-2
     */
    private fun collectRoute(goal: Vec3, position: Vec3) {
        when (val result = routes.poll()) {
            Outcome.Searching -> Unit

            // The search measures from block centers; the rest of the way is a straight line.
            Outcome.AlreadyThere -> accept(Route(position, listOf(position, goal), canReach = true))

            is Outcome.Found -> accept(result.value)

            is Outcome.Failed -> if (follower.isFinished) {
                follower.clear()
            }
        }
    }

    private fun accept(path: Route) {
        stuck = false
        routes.routeEnds(BlockPos.containing(path.end))
        follower.follow(path)
    }

    private fun planIfNeeded(
        goal: Vec3,
        radius: Double,
        maxLength: Double,
        position: Vec3,
        abilities: FlyAbilities,
    ) {
        val path = follower.path
        val finished = path != null && follower.isFinished
        val nearStage = path != null && routes.endsAtStage && position.distanceTo(path.end) < Replanner.STAGE_LEAD
        val urgent = stuck || follower.isOffPath()

        val needed = path == null || routes.hasGoalMoved(goal) || finished || nearStage || urgent
        if (!needed || !routes.isDue(urgent)) {
            return
        }

        val limits = Limits(maxLength, maxVisitedNodes = MAX_EXPANSIONS)
        routes.request(position, NearDestination(goal, radius), maxLength, abilities.control) { terrain, searchGoal ->
            RouteSearch(terrain, position, searchGoal, abilities, limits)
        }
        stuck = false
    }

    private companion object {
        const val VERTICAL_MARGIN = 32
        const val MAX_EXPANSIONS = 20_000
    }

}
