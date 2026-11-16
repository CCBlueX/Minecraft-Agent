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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.pathfinder.route.BackgroundSearch
import net.ccbluex.agent.pathfinder.route.BlockDestination
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.teleport.Route
import net.ccbluex.agent.tick.tickUntil
import java.util.function.Consumer

/**
 * A teleport of [owner] somewhere and back: the route search and its run through [teleports], told to [listener].
 * Where the server has the player meanwhile is [Teleports.serverPosition].
 *
 * The owner [request]s a route and calls [poll] every tick until the search ends, then [dispatch]es the route it
 * found; [route] does both of the first in suspend code.
 *
 * From LiquidBounce: [Trip.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/teleport/Trip.kt#L39-L114)
 *
 * @agent claude-opus-5-5
 */
public class Trip @JvmOverloads constructor(
    private val owner: Owner,
    private val body: Body,
    private val teleports: Teleports,
    options: Options = Options(),
    private val listener: Listener = Listener.NONE,
) {

    /**
     * Read for every search and dispatch; a new copy takes effect at once.
     */
    @Volatile
    public var options: Options = options

    private val search = BackgroundSearch<Route>({ body.level })

    /**
     * Where the last search requested starts and ends.
     */
    private var searchStart: Vec3? = null
    private var searchEnd: Vec3? = null

    /**
     * Hears how the search of the last [request] ended.
     */
    private var found: Consumer<Outcome<Route>>? = null

    /**
     * The run that was dispatched last, also after it ended.
     */
    public var run: Teleport? = null
        private set

    public val isAvailable: Boolean
        get() = teleports.isAvailable(owner)

    public val isAway: Boolean
        get() = teleports.holds(owner)

    /**
     * Where the server has the player.
     */
    public val position: Vec3
        get() = teleports.serverPosition?.takeIf { isAway } ?: body.player.position()

    public val eyePosition: Vec3
        get() = position.add(0.0, body.player.eyeHeight.toDouble(), 0.0)

    /**
     * Searches a route from [origin] to the first of [goals] it reaches, at most [maxLength] blocks long, in steps of
     * at most [Options.stepLength] the server's collision check lets through. [poll] advances it.
     *
     * [found] hears the outcome once, from the [poll] that ends the search: [Outcome.Found], [Outcome.AlreadyThere]
     * or [Outcome.Failed], or [Outcome.Searching] when the search was dropped first, by another request, [reset],
     * [comeBack] or the level going away.
     */
    @JvmOverloads
    public fun request(
        origin: Vec3,
        goals: List<BlockPos>,
        maxLength: Double,
        destination: Vec3 = Vec3.atBottomCenterOf(goals.first()),
        found: Consumer<Outcome<Route>>? = null,
    ) {
        val limits = Limits(maxLength)
        val goal = BlockDestination(goals)
        val stepLength = options.stepLength
        val pacing = options.pacing
        search.request(goal, goal.region(origin, maxLength, maxLength.toInt())) { terrain ->
            pacing.search(terrain, origin, goals, limits, stepLength)
        }
        searchStart = origin
        searchEnd = destination
        listener.routeSearching(origin, destination, 0.0, null)

        val dropped = this.found
        this.found = found
        dropped?.accept(Outcome.Searching)
    }

    /**
     * Advances the search by a tick; call it every tick from the game thread while one runs. It ends with the first
     * result that is not [Outcome.Searching]; without a search, the result is [Outcome.Searching].
     */
    public fun poll(): Outcome<Route> {
        val result = search.poll()
        if (result != Outcome.Searching) {
            search.cancel()
            report(result)
        } else if (search.isIdle) {
            report(Outcome.Searching)
        } else {
            showSearch()
        }
        return result
    }

    private fun report(result: Outcome<Route>) {
        val found = found ?: return
        this.found = null
        found.accept(result)
    }

    /**
     * Stops the search [found] waits for, if it is still the last one, without a report.
     */
    internal fun drop(found: Consumer<Outcome<Route>>) {
        if (this.found === found) {
            this.found = null
            search.cancel()
        }
    }

    private fun showSearch() {
        val start = searchStart ?: return
        val destination = searchEnd ?: return
        listener.routeSearching(start, destination, 0.0, search.preview)
    }

    /**
     * Starts sending [route] from where the server has the player; [run] follows it. Null while another run moves,
     * while riding, without a [Body.teleporter], or when [route] begins more than two blocks off.
     */
    public fun dispatch(route: Route, finish: Teleport.Finish): Teleport? =
        teleports.dispatch(owner, route, finish, options)?.also {
            run = it
            listener.routeFollowing(it.route.path, it)
        }

    /**
     * Brings the server back to the client; true if that takes a run, which is over once not [isAway].
     */
    public fun comeBack(): Boolean {
        stopSearch()
        if (!isAway) {
            return false
        }

        teleports.undo(owner)
        showReturn()
        return true
    }

    public fun showReturn() {
        teleports.run?.takeIf { it.owner === owner && it.finish == Teleport.Finish.RESYNC }?.let {
            listener.routeFollowing(it.route.path, it)
        }
    }

    /**
     * For block updates while a route is searched.
     */
    public fun blockChanged(pos: BlockPos) {
        search.blockChanged(pos)
    }

    public fun reset() {
        stopSearch()
        run = null
    }

    private fun stopSearch() {
        search.cancel()
        report(Outcome.Searching)
    }

}

/**
 * Searches a route as [Trip.request] does and polls it every tick until it ends, waiting on the coroutine's
 * [net.ccbluex.agent.tick.Ticker]; nothing else polls the trip meanwhile. [Outcome.Searching] when the search was
 * dropped first. Cancelling the caller or a failing requirement stops the search.
 *
 * @agent claude-opus-5-5
 */
public suspend fun Trip.route(
    origin: Vec3,
    goals: List<BlockPos>,
    maxLength: Double,
    destination: Vec3 = Vec3.atBottomCenterOf(goals.first()),
): Outcome<Route> {
    var outcome: Outcome<Route>? = null
    val found = Consumer<Outcome<Route>> { outcome = it }
    request(origin, goals, maxLength, destination, found)
    try {
        tickUntil {
            poll()
            outcome != null
        }
        return checkNotNull(outcome)
    } finally {
        if (outcome == null) drop(found)
    }
}
