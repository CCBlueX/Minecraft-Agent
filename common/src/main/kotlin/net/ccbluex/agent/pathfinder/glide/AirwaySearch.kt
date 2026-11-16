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

package net.ccbluex.agent.pathfinder.glide

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap
import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.fma
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.route.AStarSearch
import net.ccbluex.agent.pathfinder.route.Destination
import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.StageDestination
import net.ccbluex.agent.pathfinder.route.WeightedEdge
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Finds a way for a glide through open space from the feet at [start] to [goal]: an A* over the blocks where the
 * fall-flying body fits with [clearance] blocks to spare on every side, preferring room around it and shunning
 * climbs, its corners cut where a line keeps more room. Ends of a stage also lie next to blocks not loaded yet.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public class AirwaySearch(
    private val terrain: Terrain,
    private val start: Vec3,
    private val goal: Destination,
    private val clearance: Double,
    limits: Limits,
) : AStarSearch<BlockPos, Route>(limits) {

    private val startNode = node(start)
    private val open = Long2BooleanOpenHashMap()
    private val roomy = Long2BooleanOpenHashMap()
    private val stageDistance = goal.distance(feetBlock(node(start))) - MIN_PROGRESS
    private val edgeReach = ceil(clearance).toInt() + 2

    override fun preview(): List<Vec3>? = search?.closestPath()?.nodes?.map(::point)

    override fun begin(): Outcome<Route> {
        val node = node(start)
        if (goal.isGoal(feetBlock(node))) {
            return Outcome.AlreadyThere
        }

        val end = goal.ends.firstOrNull { goal.isGoal(BlockPos.containing(it)) && isClearLine(start, it, clearance) }
        if (end != null) {
            return Outcome.Found(Route(start, listOf(start, end), canReach = true))
        }
        return searchFrom(node)
    }

    override fun isGoal(node: BlockPos): Boolean {
        val feet = feetBlock(node)
        return goal.isGoal(feet) || goal is StageDestination && isNextToUnknown(node) &&
            goal.distance(feet) <= stageDistance
    }

    override fun neighbors(node: BlockPos): Iterable<WeightedEdge<BlockPos>> {
        val edges = ArrayList<WeightedEdge<BlockPos>>(NEIGHBORS)
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0 || !canMove(node, dx, dy, dz)) {
                        continue
                    }

                    val to = node.offset(dx, dy, dz)
                    var cost = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                    if (dy > 0) {
                        cost *= CLIMB_FACTOR
                    }
                    if (!isRoomy(to.x, to.y, to.z)) {
                        cost += TIGHT_COST
                    }
                    edges += WeightedEdge(to, cost)
                }
            }
        }
        return edges
    }

    override fun heuristic(node: BlockPos): Double = goal.distance(feetBlock(node)) * HEURISTIC_WEIGHT

    override fun route(nodes: List<BlockPos>): Outcome<Route> = Outcome.Found(path(nodes, canReach = true))

    override fun closestOr(failure: Failure): Outcome<Route> {
        val nodes = closestNodes { goal.distance(feetBlock(it)) } ?: return Outcome.Failed(failure)
        return Outcome.Found(path(nodes, canReach = false))
    }

    private fun path(nodes: List<BlockPos>, canReach: Boolean): Route {
        val points = ArrayList<Vec3>(nodes.size)
        points += start
        for (index in 1 until nodes.size) {
            points += point(nodes[index])
        }
        return Route(start, simplify(points), canReach)
    }

    /**
     * [points] without the corners that the line from the last kept corner passes with room to spare.
     */
    private fun simplify(points: List<Vec3>): List<Vec3> {
        val corners = arrayListOf(points.first())
        var anchor = 0
        while (anchor < points.lastIndex) {
            var next = anchor + 1
            while (next < points.lastIndex && isClearLine(points[anchor], points[next + 1], clearance + LINE_ROOM)) {
                next++
            }
            corners += points[next]
            anchor = next
        }
        return corners
    }

    /**
     * Whether the body flies straight from the feet at [from] to [to] with [margin] blocks to spare.
     */
    private fun isClearLine(from: Vec3, to: Vec3, margin: Double): Boolean {
        val delta = to.subtract(from)
        val samples = max(1, ceil(delta.length() / LINE_SPACING).toInt())
        for (sample in 0..samples) {
            if (!isClear(box(from.fma(sample.toDouble() / samples, delta), margin))) {
                return false
            }
        }
        return true
    }

    // The body has to fit in every node a diagonal move passes, so no corner is cut.
    private fun canMove(node: BlockPos, dx: Int, dy: Int, dz: Int): Boolean {
        for (x in minOf(0, dx)..maxOf(0, dx)) {
            for (y in minOf(0, dy)..maxOf(0, dy)) {
                for (z in minOf(0, dz)..maxOf(0, dz)) {
                    if ((x != 0 || y != 0 || z != 0) && !isOpen(node.x + x, node.y + y, node.z + z)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isOpen(x: Int, y: Int, z: Int): Boolean {
        val key = BlockPos.asLong(x, y, z)
        if (open.containsKey(key)) {
            return open.get(key)
        }
        // The start may lie closer to blocks than the clearance; the way out of there takes what room there is.
        val fromStart = maxOf(abs(x - startNode.x), abs(y - startNode.y), abs(z - startNode.z))
        val margin = minOf(clearance, (fromStart - 1).coerceAtLeast(0) * ESCAPE_ROOM)
        return isClear(nodeBox(x, y, z, margin)).also { open.put(key, it) }
    }

    private fun isRoomy(x: Int, y: Int, z: Int): Boolean {
        val key = BlockPos.asLong(x, y, z)
        if (roomy.containsKey(key)) {
            return roomy.get(key)
        }
        return isClear(nodeBox(x, y, z, clearance + ROOM)).also { roomy.put(key, it) }
    }

    // Unknown blocks above and below are the edge of the searched region, not the end of what is loaded.
    private fun isNextToUnknown(node: BlockPos): Boolean {
        val y = feetBlock(node).y
        return !terrain.isKnown(node.x + edgeReach, y, node.z) || !terrain.isKnown(node.x - edgeReach, y, node.z) ||
            !terrain.isKnown(node.x, y, node.z + edgeReach) || !terrain.isKnown(node.x, y, node.z - edgeReach)
    }

    /**
     * The node whose body box, [clearance] included, has its bottom in the block of [feet] lowered by [clearance].
     */
    private fun node(feet: Vec3) = BlockPos.containing(feet.x, feet.y - clearance, feet.z)

    private fun feetBlock(node: BlockPos) = BlockPos(node.x, Mth.floor(node.y + clearance), node.z)

    private fun point(node: BlockPos) = Vec3(node.x + 0.5, node.y + clearance, node.z + 0.5)

    private fun nodeBox(x: Int, y: Int, z: Int, margin: Double) = box(Vec3(x + 0.5, y + clearance, z + 0.5), margin)

    private fun box(feet: Vec3, margin: Double) = Passage.box(feet, margin)

    // Water ends a glide, so it is kept clear of like a block.
    private fun isClear(box: AABB) = terrain.isClear(box) && !terrain.containsAnyLiquid(box)

    @InternalAgentApi
    public companion object {
        /**
         * Width and height of the fall-flying body.
         */
        public const val BODY_SIZE: Double = 0.6

        private const val NEIGHBORS = 26
        private const val MIN_PROGRESS = 2.0
        private const val CLIMB_FACTOR = 4.0

        /**
         * Extra room around the body the route prefers, and what a node without it costs more.
         */
        private const val ROOM = 1.5
        private const val TIGHT_COST = 1.0

        /**
         * Extra room cut corners keep beyond the clearance.
         */
        private const val LINE_ROOM = 0.5
        private const val LINE_SPACING = 0.5
        private const val HEURISTIC_WEIGHT = 1.5

        /**
         * Clearance gained per block away from the start, for starts closer to blocks than the clearance.
         */
        private const val ESCAPE_ROOM = 0.25
    }

}
