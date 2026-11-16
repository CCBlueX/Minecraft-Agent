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

package net.ccbluex.agent.pathfinder.flight

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.math.fma
import net.ccbluex.agent.pathfinder.route.AStarSearch
import net.ccbluex.agent.pathfinder.route.Destination
import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.WeightedEdge
import net.ccbluex.agent.pathfinder.terrain.Terrain
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Straight lines through the air between [points], the start first; incomplete, to the closest spot reached.
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/PathSearch.kt#L41-L42)
 *
 * @agent claude-opus-5-5
 */
public class Route(public val start: Vec3, public val points: List<Vec3>, public val canReach: Boolean) {

    public val end: Vec3
        get() = points.last()

}

/**
 * Finds a flight route: the straight line when it is clear, else an A* over [Moves] whose corners are cut
 * where the line between them is clear, else, when the A* gives up, a climb straight over whatever is in the way.
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/PathSearch.kt#L53-L170)
 *
 * @agent claude-opus-5-5
 */
public class RouteSearch(
    private val terrain: Terrain,
    private val start: Vec3,
    private val goal: Destination,
    private val abilities: FlyAbilities,
    limits: Limits,
) : AStarSearch<BlockPos, Route>(limits) {

    private val moves = Moves(terrain, abilities, goal)
    private val minCost = min(abilities.horizontalCost, abilities.verticalCost)

    /**
     * Feet points of the goal ends in reach of the start that the body fits in.
     */
    private val goalPoints by lazy {
        val reachSqr = limits.maxLength * limits.maxLength
        val points = ArrayList<Vec3>()
        for (end in goal.ends) {
            if (end.distanceToSqr(start) >= reachSqr) {
                continue
            }
            val node = BlockPos.containing(end)
            if (goal.isGoal(node) && moves.isOpen(node.x, node.y, node.z)) {
                points += moves.point(node)
            }
        }
        points
    }

    override fun preview(): List<Vec3>? = search?.closestPath()?.nodes?.map(moves::point)

    override fun begin(): Outcome<Route> {
        val feet = BlockPos.containing(start)
        val fitsAbove = !moves.isOpen(feet.x, feet.y, feet.z) && moves.isOpen(feet.x, feet.y + 1, feet.z)
        val startNode = if (fitsAbove) feet.above() else feet
        if (goal.isGoal(startNode)) {
            return Outcome.AlreadyThere
        }
        return straightRoute() ?: searchFrom(startNode)
    }

    override fun isGoal(node: BlockPos): Boolean = goal.isGoal(node)

    override fun neighbors(node: BlockPos): Iterable<WeightedEdge<BlockPos>> = moves.neighbors(node)

    // Ticks to the goal, split the way its nearest end lies, as flying up and down is slower than flying on.
    override fun heuristic(node: BlockPos): Double {
        val distance = goal.distance(node)
        val center = Vec3.atBottomCenterOf(node)
        val end = goal.ends.minByOrNull(center::distanceToSqr) ?: return distance * minCost
        val delta = end.subtract(center)
        val length = delta.length()
        if (length < 1.0E-6) {
            return 0.0
        }

        val horizontal = distance * delta.horizontalDistance() / length * abilities.horizontalCost
        val vertical = distance * abs(delta.y) / length * abilities.verticalCost
        return max(horizontal, vertical) * HEURISTIC_WEIGHT
    }

    override fun route(nodes: List<BlockPos>): Outcome<Route> = Outcome.Found(path(nodes, canReach = true))

    override fun closestOr(failure: Failure): Outcome<Route> {
        climbRoute()?.let { return it }

        val nodes = closestNodes { goal.distance(it) } ?: return Outcome.Failed(failure)
        return Outcome.Found(path(nodes, canReach = false))
    }

    private fun straightRoute(): Outcome<Route>? {
        val point = goalPoints.firstOrNull { isClearLine(terrain, start, it) } ?: return null
        return Outcome.Found(Route(start, listOf(start, point), canReach = true))
    }

    /**
     * Up, across and down at the lowest height where that is clear, for obstacles too wide to search around.
     */
    private fun climbRoute(): Outcome<Route>? {
        for (point in goalPoints) {
            val base = Mth.floor(max(start.y, point.y))
            for (height in base + 1..min(base + MAX_CLIMB, terrain.maxY)) {
                val up = start.copy(y = height.toDouble())
                if (!isClearLine(terrain, start, up)) {
                    break
                }

                val over = point.copy(y = height.toDouble())
                if (isClearLine(terrain, up, over) && isClearLine(terrain, over, point)) {
                    return Outcome.Found(Route(start, simplify(listOf(start, up, over, point)), true))
                }
            }
        }
        return null
    }

    private fun path(nodes: List<BlockPos>, canReach: Boolean): Route {
        val points = ArrayList<Vec3>(nodes.size)
        points += start
        for (index in 1 until nodes.size) {
            points += moves.point(nodes[index])
        }
        return Route(start, simplify(points), canReach)
    }

    /**
     * [points] without the corners that the line from the last kept corner passes clear.
     */
    private fun simplify(points: List<Vec3>): List<Vec3> {
        val corners = arrayListOf(points.first())
        var anchor = 0
        while (anchor < points.lastIndex) {
            var next = anchor + 1
            while (next < points.lastIndex && isClearLine(terrain, points[anchor], points[next + 1])) {
                next++
            }
            corners += points[next]
            anchor = next
        }
        return corners
    }

    private companion object {
        const val MAX_CLIMB = 32

        // Finds a route much sooner for one a little longer than the shortest; its corners are cut anyway.
        const val HEURISTIC_WEIGHT = 1.5
    }

}

private const val LINE_SPACING = 0.25
private const val LINE_MARGIN = 0.1
private const val END_LENGTH = 0.5

/**
 * Whether the body flies straight from [from] to [to] touching nothing, with a side margin away from both ends.
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/PathSearch.kt#L173-L181)
 *
 * @agent claude-opus-5-5
 */
public fun isClearLine(terrain: Terrain, from: Vec3, to: Vec3): Boolean {
    val delta = to.subtract(from)
    val length = delta.length()
    val samples = max(1, ceil(length / LINE_SPACING).toInt())
    for (sample in 0..samples) {
        val along = length * sample / samples
        val margin = if (along < END_LENGTH || length - along < END_LENGTH) 0.0 else LINE_MARGIN
        val point = from.fma(sample.toDouble() / samples, delta)
        if (!terrain.isClear(terrain.box(point).inflate(margin, 0.0, margin))) {
            return false
        }
    }
    return true
}
