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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3

/**
 * The route events for [listener], each passed on only when it changes what a renderer shows.
 *
 * @agent claude-opus-5-5
 */
internal class RouteReport(private val listener: Listener) {

    private enum class Shown { NOTHING, SEARCHING, FOLLOWING, DONE, INVALID }

    private var shown = Shown.NOTHING

    private var start: Vec3? = null
    private var goal: Vec3? = null
    private var radius = 0.0
    private var preview: List<Vec3>? = null
    private var points: List<Vec3>? = null
    private var progress: Progress? = null
    private var breaks: Collection<BlockPos> = emptyList()
    private var places: Collection<BlockPos> = emptyList()

    fun searching(start: Vec3, goal: Vec3, radius: Double, preview: List<Vec3>? = null) {
        if (shown == Shown.SEARCHING && start == this.start && goal == this.goal && radius == this.radius &&
            preview === this.preview
        ) {
            return
        }
        shown = Shown.SEARCHING
        this.start = start
        this.goal = goal
        this.radius = radius
        this.preview = preview
        points = null
        progress = null
        listener.routeSearching(start, goal, radius, preview)
    }

    fun following(points: List<Vec3>, progress: Progress) {
        if (shown == Shown.FOLLOWING && points === this.points && progress === this.progress) {
            return
        }
        shown = Shown.FOLLOWING
        this.points = points
        this.progress = progress
        listener.routeFollowing(points, progress)
    }

    fun blocks(breaks: Collection<BlockPos>, places: Collection<BlockPos>) {
        if (breaks === this.breaks && places === this.places) {
            return
        }
        this.breaks = breaks
        this.places = places
        listener.blocksAhead(breaks, places)
    }

    fun done() {
        if (shown != Shown.DONE) {
            shown = Shown.DONE
            listener.routeDone()
        }
    }

    fun invalid() {
        if (shown != Shown.INVALID) {
            shown = Shown.INVALID
            listener.routeInvalid()
        }
    }

    fun clear() {
        start = null
        goal = null
        preview = null
        points = null
        progress = null
        breaks = emptyList()
        places = emptyList()
        if (shown != Shown.NOTHING) {
            shown = Shown.NOTHING
            listener.routeCleared()
        }
    }

}
