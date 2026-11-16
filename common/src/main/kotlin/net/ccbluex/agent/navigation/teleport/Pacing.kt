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

package net.ccbluex.agent.navigation.teleport

import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Search
import net.ccbluex.agent.pathfinder.teleport.DirectSearch
import net.ccbluex.agent.pathfinder.teleport.Route
import net.ccbluex.agent.pathfinder.teleport.WaypointSearch
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3

/**
 * How many packets of a route go out in one tick.
 *
 * @agent claude-sonnet-5-5
 */
public sealed interface Pacing {

    /**
     * [packetsPerTick] packets a tick (1 to [Route.MAX_PACKETS_PER_TICK]), as far as the server's allowance lets
     * through.
     */
    public data class Batches(public val packetsPerTick: Int = DEFAULT_PACKETS_PER_TICK) : Pacing

    /**
     * Every packet in one tick. A route that needs more than the server takes in a tick fails.
     */
    public data object Burst : Pacing

    /**
     * One packet straight to the end, checked with the server's own sweep of that move. A blocked or too long move
     * fails.
     */
    public data object Instant : Pacing

    public companion object {
        public const val DEFAULT_PACKETS_PER_TICK: Int = 3
    }

}

/**
 * The search that plans routes sent at this pacing.
 *
 * @agent claude-sonnet-5-5
 */
internal fun Pacing.search(
    terrain: Terrain,
    start: Vec3,
    goals: List<BlockPos>,
    limits: Limits,
    stepLength: Double,
): Search<Route> = when (this) {
    is Pacing.Batches -> WaypointSearch(terrain, start, goals, limits, stepLength)
    Pacing.Burst -> OneTick(WaypointSearch(terrain, start, goals, limits, stepLength))
    Pacing.Instant -> DirectSearch(terrain, start, goals, limits)
}

/**
 * How many steps of [route] go out in each tick. A route that does not fit a tick is sent at the most the server
 * takes, which only the way back of a longer excursion needs.
 *
 * @agent claude-sonnet-5-5
 */
internal fun Pacing.batches(route: Route): IntArray = when (this) {
    is Pacing.Batches -> route.batches(packetsPerTick)
    Pacing.Burst, Pacing.Instant -> route.batches(route.mechanics.maxPacketsPerTick)
}

private class OneTick(private val search: Search<Route>) : Search<Route> {

    override fun advance(): Outcome<Route> = when (val outcome = search.advance()) {
        is Outcome.Found -> if (outcome.value.fitsOneTick) outcome else Outcome.Failed(Failure.TOO_FAR)
        else -> outcome
    }

    override fun preview(): List<Vec3>? = search.preview()

}
