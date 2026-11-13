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

package net.ccbluex.agent.pathfinder.ride

import net.ccbluex.agent.pathfinder.route.AStarSearch
import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Destination
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.WeightedEdge
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.min

/**
 * One point of a [Route]: where the vehicle rests in [node], and whether it jumps to get there.
 *
 * @agent claude-opus-5-5
 */
public class Point(public val node: BlockPos, public val position: Vec3, public val jump: Boolean)

/**
 * A ride from [start]. Incomplete, it ends at the closest spot reached; [exhausted], no closer one can be reached.
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/PathSearch.kt#L39-L42)
 *
 * @agent claude-opus-5-5
 */
public class Route(
    public val start: Vec3,
    public val points: List<Point>,
    public val canReach: Boolean,
    public val exhausted: Boolean,
) {

    /**
     * Rest positions of the path, the start included.
     */
    public val positions: List<Vec3> = listOf(start) + points.map(Point::position)

    public val end: Vec3
        get() = positions.last()

}

/**
 * Finds a route for a ridden [vehicle] from [start] to [goal], an A* over the nodes of [Moves].
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/PathSearch.kt#L50-L106)
 *
 * @agent claude-opus-5-5
 */
public class RouteSearch(
    terrain: Terrain,
    private val start: Vec3,
    private val goal: Destination,
    private val vehicle: Vehicle,
    limits: Limits,
) : AStarSearch<BlockPos, Route>(limits) {

    private val moves = Moves(terrain, vehicle)

    override fun preview(): List<Vec3>? = search?.closestPath()?.nodes?.mapNotNull { moves.spot(it)?.position }

    override fun begin(): Outcome<Route> {
        val startNode = startNode()
        return when {
            isGoal(startNode) -> Outcome.AlreadyThere
            moves.spot(startNode) == null -> Outcome.Failed(Failure.INVALID_DESTINATION)
            else -> searchFrom(startNode)
        }
    }

    private fun startNode(): BlockPos {
        val node = BlockPos.containing(start)
        return when {
            moves.spot(node) != null -> node
            moves.spot(node.above()) != null -> node.above()
            moves.spot(node.below()) != null -> node.below()
            else -> node
        }
    }

    // A boat is there when its rider is, who sits one block above the water block it floats in.
    override fun isGoal(node: BlockPos): Boolean =
        goal.isGoal(node) || vehicle.kind == VehicleKind.BOAT && goal.isGoal(node.x, node.y + 1, node.z)

    private fun distance(node: BlockPos): Double {
        val distance = goal.distance(node)
        return if (vehicle.kind == VehicleKind.BOAT) min(distance, goal.distance(node.x, node.y + 1, node.z)) else distance
    }

    override fun neighbors(node: BlockPos): Iterable<WeightedEdge<BlockPos>> = moves.neighbors(node)

    override fun heuristic(node: BlockPos): Double = distance(node) / vehicle.speed

    override fun route(nodes: List<BlockPos>): Outcome<Route> = path(nodes, canReach = true, exhausted = false)

    override fun closestOr(failure: Failure): Outcome<Route> {
        val nodes = closestNodes(::distance) ?: return Outcome.Failed(failure)
        return path(nodes, canReach = false, exhausted = failure == Failure.NO_PATH)
    }

    private fun path(nodes: List<BlockPos>, canReach: Boolean, exhausted: Boolean): Outcome<Route> {
        val points = ArrayList<Point>(nodes.size - 1)
        for (i in 1 until nodes.size) {
            val from = moves.spot(nodes[i - 1]) ?: return Outcome.Failed(Failure.NO_PATH)
            val to = moves.spot(nodes[i]) ?: return Outcome.Failed(Failure.NO_PATH)
            points += Point(nodes[i], to.position, moves.isJump(from, to))
        }
        return Outcome.Found(Route(start, points, canReach, exhausted))
    }

}
