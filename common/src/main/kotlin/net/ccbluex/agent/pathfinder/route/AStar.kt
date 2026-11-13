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

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.util.PriorityQueue

/**
 * A way from one node to [node] that takes [cost].
 *
 * @agent claude-opus-5-5
 */
internal data class WeightedEdge<N>(val node: N, val cost: Double)

/**
 * Nodes from the start to the last one, which took [totalCost] to reach.
 *
 * @agent claude-opus-5-5
 */
internal data class ShortestPath<N>(val nodes: List<N>, val totalCost: Double)

/**
 * A* from [start] that runs in slices of [advance]. Ways costing more than [maxCost] are dropped. Ties in the open
 * list go by [PriorityQueue] order. Not thread-safe.
 *
 * From LiquidBounce: [GraphSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/block/GraphSearch.kt#L64-L113)
 *
 * @agent claude-opus-5-5
 */
internal class AStar<N>(
    private val start: N,
    private val isGoal: (N) -> Boolean,
    private val neighbors: (N) -> Iterable<WeightedEdge<N>>,
    private val heuristic: (N) -> Double,
    private val maxCost: Double = Double.POSITIVE_INFINITY,
) {

    private class Open<N>(val node: N, val cost: Double, val estimate: Double)

    private val bestCost = Object2DoubleOpenHashMap<N>()
    private val cameFrom = Object2ObjectOpenHashMap<N, N>()
    private val open = PriorityQueue(Comparator.comparingDouble<Open<N>> { it.estimate })

    /**
     * The node polled so far with the least heuristic left, and that heuristic.
     */
    private var nearest: Open<N>? = null
    private var nearestHeuristic = Double.POSITIVE_INFINITY

    private var found: ShortestPath<N>? = null

    /**
     * Polls from the open list so far, outdated entries included.
     */
    var iterations: Int = 0
        private set

    val isFinished: Boolean
        get() = found != null || open.isEmpty()

    init {
        bestCost.defaultReturnValue(Double.POSITIVE_INFINITY)
        bestCost.put(start, 0.0)
        open.add(Open(start, 0.0, heuristic(start)))
    }

    /**
     * Polls up to [maxIterations] entries; the path to the goal once one was polled, else null.
     */
    fun advance(maxIterations: Int): ShortestPath<N>? {
        var left = maxIterations
        while (found == null && left > 0) {
            val entry = open.poll() ?: break
            left--
            iterations++
            visit(entry)
        }
        return found
    }

    /**
     * The best known cost to reach [node], infinite before it was reached.
     */
    fun cost(node: N): Double = bestCost.getDouble(node)

    /**
     * Where the best known way to [node] comes from; null for the start and nodes not reached.
     */
    fun previous(node: N): N? = cameFrom[node]

    /**
     * The way to the polled node with the least heuristic, null while that is the start.
     */
    fun closestPath(): ShortestPath<N>? {
        val entry = nearest ?: return null
        if (entry.node == start) {
            return null
        }
        return ShortestPath(trace(entry.node), entry.cost)
    }

    private fun visit(entry: Open<N>) {
        if (entry.cost > maxCost || entry.cost > bestCost.getDouble(entry.node)) {
            return
        }

        // Taken from the estimate, as the open list ranks by it.
        val left = entry.estimate - entry.cost
        if (left < nearestHeuristic) {
            nearestHeuristic = left
            nearest = entry
        }

        if (isGoal(entry.node)) {
            found = ShortestPath(trace(entry.node), entry.cost)
            return
        }

        for (edge in neighbors(entry.node)) {
            require(edge.cost >= 0.0) { "A* needs edges that cost nothing or more, got ${edge.cost}" }
            val cost = entry.cost + edge.cost
            if (cost > maxCost || cost >= bestCost.getDouble(edge.node)) {
                continue
            }
            bestCost.put(edge.node, cost)
            cameFrom.put(edge.node, entry.node)
            open.add(Open(edge.node, cost, cost + heuristic(edge.node)))
        }
    }

    /**
     * Nodes from the start to [end]; empty when the chain back breaks.
     */
    private fun trace(end: N): List<N> {
        val reversed = ArrayList<N>()
        var node = end
        reversed += node
        while (node != start) {
            node = cameFrom[node] ?: return emptyList()
            reversed += node
        }
        reversed.reverse()
        return reversed
    }

}
