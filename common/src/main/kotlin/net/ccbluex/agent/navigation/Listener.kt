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

import net.ccbluex.agent.navigation.walk.PlacedBlock
import net.ccbluex.agent.pathfinder.walk.Route
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * What a [Navigator] or a [net.ccbluex.agent.navigation.teleport.Trip] reports while it moves; every method is
 * optional and called on the ticking thread.
 *
 * A host draws the route from the route events alone ([routeSearching], [routeFollowing], [blocksAhead],
 * [routeDone], [routeInvalid], [routeCleared]). A navigator sends them when what they show changes, a trip on every
 * update.
 *
 * @agent claude-opus-5-5
 */
public interface Listener {

    /**
     * A walking path to follow was found: its steps with their kind of move, and [Route.points], its corners.
     */
    public fun routeFound(path: Route) {}

    /**
     * The step that ends in [pos] was walked; [index] is its place in the path.
     */
    public fun stepReached(index: Int, pos: BlockPos) {}

    /**
     * The walk got stuck before [pos]; searches avoid it for a while.
     */
    public fun stuck(pos: BlockPos) {}

    /**
     * The player came within the radius of [goal].
     */
    public fun arrived(goal: Vec3) {}

    /**
     * Searches found no way to [goal].
     */
    public fun failed(goal: Vec3) {}

    /**
     * A block was placed; its verdict follows in [blockKept] or [blockRejected].
     */
    public fun blockPlaced(block: PlacedBlock) {}

    public fun blockKept(block: PlacedBlock) {}

    /**
     * The server took a placed block back.
     */
    public fun blockRejected(block: PlacedBlock) {}

    /**
     * A route from [start] to [goal], within [radius], is searched; [preview] holds the corners of the best one so far.
     */
    public fun routeSearching(start: Vec3, goal: Vec3, radius: Double, preview: List<Vec3>?) {}

    /**
     * The route along [points], its corners as feet positions, is followed; [progress] tells each frame how far.
     */
    public fun routeFollowing(points: List<Vec3>, progress: Progress) {}

    /**
     * The blocks the route followed still has to break and to place.
     */
    public fun blocksAhead(breaks: Collection<BlockPos>, places: Collection<BlockPos>) {}

    /**
     * The route ended at its goal, or there is nothing left to search or follow.
     */
    public fun routeDone() {}

    /**
     * No route was found, or the server or the world stopped the one followed.
     */
    public fun routeInvalid() {}

    /**
     * The navigation stopped; nothing of the route is shown anymore.
     */
    public fun routeCleared() {}

    public companion object {
        @JvmField
        public val NONE: Listener = object : Listener {}
    }
}
