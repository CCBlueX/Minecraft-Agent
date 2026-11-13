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

/**
 * An A* route search over nodes [N] that runs in slices of [Limits.visitedNodesPerSlice] within [limits].
 *
 * From LiquidBounce: [AStarRouteSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/AStarRouteSearch.kt#L26-L90)
 *
 * @agent claude-fable-5-2
 */
public abstract class AStarSearch<N, T>(protected val limits: Limits) : Search<T> {

    internal var search: AStar<N>? = null
        private set
    private var computeNanos = 0L
    private var result: Outcome<T> = Outcome.Searching

    final override fun advance(): Outcome<T> {
        if (result != Outcome.Searching) {
            return result
        }

        val began = System.nanoTime()
        result = search?.let(::expand) ?: begin()
        computeNanos += System.nanoTime() - began

        if (result == Outcome.Searching && computeNanos > limits.maxComputeNanos) {
            result = closestOr(Failure.TIMED_OUT)
        }
        return result
    }

    private fun expand(search: AStar<N>): Outcome<T> {
        val path = search.advance(minOf(limits.visitedNodesPerSlice, limits.maxVisitedNodes - search.iterations))
        return when {
            path != null -> route(path.nodes)
            search.isFinished -> closestOr(Failure.NO_PATH)
            search.iterations >= limits.maxVisitedNodes -> closestOr(Failure.TOO_COMPLEX)
            else -> Outcome.Searching
        }
    }

    /**
     * The first slice: a result known without the A*, or [searchFrom] the start node.
     */
    protected abstract fun begin(): Outcome<T>

    protected fun searchFrom(start: N, maxCost: Double = Double.POSITIVE_INFINITY): Outcome<T> {
        search = AStar(
            start = start,
            isGoal = ::isGoal,
            neighbors = ::neighbors,
            heuristic = ::heuristic,
            maxCost = maxCost,
        )
        return Outcome.Searching
    }

    protected abstract fun isGoal(node: N): Boolean

    internal abstract fun neighbors(node: N): Iterable<WeightedEdge<N>>

    protected abstract fun heuristic(node: N): Double

    protected abstract fun route(nodes: List<N>): Outcome<T>

    /**
     * What a search returns that ends by [failure] short of the goal.
     */
    protected open fun closestOr(failure: Failure): Outcome<T> = Outcome.Failed(failure)

    /**
     * The path to the explored node closest to the goal, unless [distance] shrinks less than [MIN_PROGRESS].
     */
    @InternalAgentApi
    protected fun closestNodes(distance: (N) -> Double): List<N>? = search?.closestPath()?.nodes?.takeUnless {
        it.size < 2 || distance(it.first()) - distance(it.last()) < MIN_PROGRESS
    }

    private companion object {
        const val MIN_PROGRESS = 1.0
    }

}
