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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.distanceToSegment
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.navigation.FollowState
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.navigation.ProgressWatch
import net.ccbluex.agent.pathfinder.flight.Acceleration
import net.ccbluex.agent.pathfinder.flight.FlyAbilities
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.world.Input

/**
 * Flies a [Route] with movement input: the keys towards a point ahead on the route, jump to rise and sneak to
 * sink. The keys aim where the player would come to rest with them released, as [handling] measured the flight, so
 * a flight that coasts slows down in time for sharp corners and one that stops at once is not pushed past its point.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/Follower.kt#L41-L227)
 *
 * @agent claude-opus-5-5
 */
internal class Follower(private val context: NavigationContext, private val handling: Handling) : Progress {

    private var route: Leg? = null
    private var takeOffTicks = 0

    /**
     * The keys the flight pressed last tick, for [handling] to measure what they did.
     */
    private var pressed: Input? = null

    var target: Vec3? = null
        private set

    var sprint = false
        private set

    val path: Route?
        get() = route?.path

    val isFinished: Boolean
        get() = route?.isFinished == true

    override val isMoving: Boolean
        get() = route?.isFinished == false

    override val ahead: List<Vec3>
        get() {
            val route = route ?: return emptyList()
            return route.points.drop(route.corner)
        }

    override fun head(partialTicks: Float): Vec3 = context.body.player.getPosition(partialTicks)

    fun follow(path: Route) {
        route = Leg(path)
    }

    fun clear() {
        route = null
        takeOffTicks = 0
        target = null
        sprint = false
        pressed = null
    }

    fun isOffPath(): Boolean {
        val route = route?.takeUnless { it.isFinished } ?: return false
        val points = route.points
        val offPath = maxOf(OFF_PATH, OFF_PATH_TICKS * handling.speed)
        return context.body.player.position().distanceToSegment(points[route.corner - 1], points[route.corner]) >
            offPath
    }

    fun tick(frameYRot: Float, abilities: FlyAbilities): FollowState {
        val player = context.body.player
        handling.record(player, pressed)
        pressed = null
        target = null
        sprint = false

        val route = route?.also(::advance)
        if (route == null || route.isFinished) {
            context.body.press(Input.EMPTY, true)
            return FollowState.FINISHED
        }

        if (!abilities.isFlying) {
            // A double jump starts creative flight.
            val jump = takeOffTicks++ % 2 == 0
            context.body.press(Input(false, false, false, false, jump, false, false), true)
            return FollowState.WALKING
        }

        takeOffTicks = 0
        val aim = aim(route)
        target = aim
        fly(frameYRot, aim, abilities, isFinal = aim == route.path.end)
        return progress(route)
    }

    private fun progress(route: Leg): FollowState {
        val distance = context.body.player.position().distanceTo(route.points[route.corner])
        val missed = route.misses(distance, MISS_SHARE * finalReach())
        if (route.watch.stalls(distance) || missed) {
            route.passed()
            return FollowState.STUCK
        }
        return FollowState.WALKING
    }

    /**
     * Presses the keys that bring the point the player would come to rest at with them released towards [aim]; none
     * on an axis that one more tick of the keys would push past [aim] by more than it closes.
     */
    private fun fly(frameYRot: Float, aim: Vec3, abilities: FlyAbilities, isFinal: Boolean) {
        val player = context.body.player
        val error = aim.subtract(handling.rest(player))

        val horizontalTolerance = handling.step / 2.0
        val riseTolerance = handling.verticalSpeed / 2.0
        // Sinking stops short of the ground on the way, as touching it ends creative flight.
        val sinkTolerance = if (abilities.control == Acceleration.MOMENTUM && !isFinal) SINK_TOLERANCE else riseTolerance

        val moves = error.horizontalDistanceSqr() > horizontalTolerance * horizontalTolerance
        val direction = if (moves) getDirectionalInputTowards(error, frameYRot) else Input.EMPTY
        // A flight sagging a little below a route that grazes a wall's top stalls against it: only rising gets past
        val isStalled = moves && player.horizontalCollision && (handling.lastMove?.horizontalDistance() ?: 0.0) < 0.01
        val jump = error.y > riseTolerance || isStalled
        // On ground the aim lies a block or more below, sneaking only holds the player at the edge it has to leave
        val leaves = player.onGround() && error.y < -1.0
        val sneak = error.y < -sinkTolerance && !jump && !leaves
        val input =
            Input(direction.forward, direction.backward, direction.left, direction.right, jump, sneak, false)
        context.body.press(input, true)
        pressed = input
        sprint = moves && error.horizontalDistance() > SPRINT_DISTANCE
    }

    /**
     * The next corner for a flight that stops at once. One that coasts aims as far ahead on the route as it coasts
     * from full speed, and a bit, so it keeps going, but never past a sharp corner.
     */
    private fun aim(route: Leg): Vec3 {
        val points = route.points
        if (!handling.coasts) {
            return points[route.corner]
        }

        var remaining = handling.coastDistance + LOOKAHEAD_MARGIN
        var from = context.body.player.position()
        for (corner in route.corner until points.size) {
            val point = points[corner]
            val length = from.distanceTo(point)
            if (length >= remaining) {
                return from.lerp(point, remaining / length)
            }
            if (isSharp(points, corner)) {
                return point
            }
            remaining -= length
            from = point
        }
        return points.last()
    }

    private fun advance(route: Leg) {
        val points = route.points
        val position = context.body.player.position()
        val start = route.corner
        val finalReach = finalReach()
        while (!route.isFinished) {
            val reach = when {
                route.corner == points.lastIndex -> finalReach
                !handling.coasts -> maxOf(finalReach, handling.speed)
                isSharp(points, route.corner) -> maxOf(SHARP_REACH, finalReach)
                else -> PASS_REACH
            }
            val outOfReach = points[route.corner].distanceToSqr(position) >= reach * reach
            if (outOfReach) {
                break
            }
            route.corner++
        }

        if (route.corner != start) {
            route.passed()
        }
    }

    /**
     * How close the end counts as reached: within the tolerances [fly] stops in, which a fast flight has wide.
     */
    private fun finalReach(): Double =
        maxOf(FINAL_REACH, Mth.length(handling.step / 2.0, handling.verticalSpeed / 2.0) + REACH_MARGIN)

    /**
     * A path being flown, the [corner] flown to next and the progress towards it.
     */
    private class Leg(val path: Route) {
        val points = path.points
        var corner = 1
        val watch = ProgressWatch(MIN_PROGRESS, STUCK_TICKS)
        private var nearest = Double.MAX_VALUE
        private var missed = 0

        val isFinished: Boolean
            get() = corner >= points.size

        /**
         * Whether the player, now [distance] from the corner, has come within [range] of it and gone [range] past
         * that again [MAX_MISSES] times, as a flight that keeps overshooting it does.
         */
        fun misses(distance: Double, range: Double): Boolean {
            if (distance < nearest) {
                nearest = distance
                return false
            }
            if (nearest < range && distance > nearest + range) {
                missed++
                nearest = distance
            }
            return missed >= MAX_MISSES
        }

        /**
         * The corner was passed or given up: the progress towards the next one starts over.
         */
        fun passed() {
            watch.reset()
            nearest = Double.MAX_VALUE
            missed = 0
        }
    }

    private companion object {
        const val LOOKAHEAD_MARGIN = 3.0
        const val SINK_TOLERANCE = 0.8
        const val SPRINT_DISTANCE = 3.0
        const val FINAL_REACH = 0.5
        const val REACH_MARGIN = 0.1
        const val SHARP_REACH = 1.0
        const val PASS_REACH = 2.0
        const val OFF_PATH = 2.5

        /**
         * Ticks of flight at full speed the player may be off the route before it counts as off.
         */
        const val OFF_PATH_TICKS = 2.0
        const val MIN_PROGRESS = 0.1
        const val STUCK_TICKS = 60
        const val SHARP_TURN_COS = 0.7
        const val MISS_SHARE = 2.0
        const val MAX_MISSES = 3

        /**
         * Whether the player stops at [corner] instead of flying through it: the last one, or a sharp turn.
         */
        fun isSharp(points: List<Vec3>, corner: Int): Boolean {
            if (corner <= 0 || corner >= points.lastIndex) {
                return corner >= points.lastIndex
            }

            val inbound = points[corner].subtract(points[corner - 1])
            val outbound = points[corner + 1].subtract(points[corner])
            val lengths = inbound.length() * outbound.length()
            return lengths > 1.0E-9 && inbound.dot(outbound) / lengths < SHARP_TURN_COS
        }
    }

}
