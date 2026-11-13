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

package net.ccbluex.agent.navigation.ride

import net.ccbluex.agent.math.distanceToSegment
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.navigation.ProgressWatch
import net.ccbluex.agent.pathfinder.ride.Moves
import net.ccbluex.agent.pathfinder.ride.Route
import net.ccbluex.agent.pathfinder.ride.RouteSearch
import net.ccbluex.agent.pathfinder.ride.Vehicle
import net.ccbluex.agent.pathfinder.ride.VehicleKind
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Replanner
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

/**
 * Drives the vehicle the player rides to a goal: a boat with its own controls, a horse or other mount by looking the
 * way. Where the vehicle gets no closer, or the player cannot steer it, the player gets off to walk the rest.
 *
 * From LiquidBounce: [Riding.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/Riding.kt#L59-L318)
 *
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
internal class Riding(
    private val context: NavigationContext,
    /**
     * @agent claude-opus-5-5
     * @agent claude-fable-5-2
     */
    private val maxLength: () -> Double,
) {

    private val routes = Replanner<Route>({ context.body.level }, VERTICAL_MARGIN)
    private val steering = Steering(context.body)

    private var mount: Entity? = null
    private var path: Route? = null
    private var index = 0

    private var stuck = false
    private var stuckCount = 0
    private val watch = ProgressWatch(MIN_PROGRESS, STUCK_TICKS)

    /**
     * Whether the vehicle is in the goal node, so only the last bit is left to ride straight.
     */
    private var atGoal = false
    private var approachTicks = 0
    private var dismountTicks = 0

    /**
     * Moves the ridden vehicle one tick towards [goal], within [radius], or gets off it; false when the player rides
     * nothing.
     */
    fun navigate(goal: Vec3, radius: Double): Boolean {
        val player = context.body.player
        if (!player.isPassenger) {
            if (mount != null || dismountTicks > 0) {
                halt()
            }
            return false
        }

        routes.tick()
        val mount = player.controlledVehicle
        val vehicle = mount?.let { Vehicle.of(player, it) }
        if (mount == null || vehicle == null || dismountTicks > 0) {
            dismount(goal)
            return true
        }
        this.mount = mount

        val position = mount.position()
        collectRoute()

        val path = path
        when {
            path != null && index < path.points.size -> follow(mount, vehicle, path)
            atGoal || path?.canReach == true -> approach(mount, vehicle, goal)
            stuckCount > MAX_STUCK || routes.failed || path?.exhausted == true -> dismount(goal)
            else -> {
                steering.stop()
                context.route.searching(position, goal, radius, routes.preview)
            }
        }

        if (dismountTicks == 0) {
            planIfNeeded(goal, radius, position, vehicle)
        }
        return true
    }

    /**
     * Stops riding to the goal until the next [navigate].
     */
    fun halt() {
        routes.reset()
        steering.reset()
        mount = null
        path = null
        index = 0
        stuck = false
        stuckCount = 0
        atGoal = false
        approachTicks = 0
        dismountTicks = 0
    }

    fun blockChanged(pos: BlockPos) {
        routes.blockChanged(pos)
    }

    fun levelChanged() {
        routes.levelChanged()
    }

    private fun follow(mount: Entity, vehicle: Vehicle, path: Route) {
        val position = mount.position()
        advance(position, vehicle, path)
        if (index >= path.points.size) {
            steering.stop()
            return
        }

        val point = path.points[index]
        trackProgress(position, point.position, path)

        val target = if (point.jump) point.position else straightAhead(position, vehicle, path)
        when (vehicle.kind) {
            VehicleKind.BOAT -> {
                val brake = target === path.end && position.horizontalDistanceTo(target) <
                    mount.deltaMovement.horizontalDistance() * BOAT_COAST_TICKS + 1.0
                steering.boat(mount, target, brake)
            }

            VehicleKind.MOUNT -> {
                val jumpTo = point.position.takeIf { point.jump }
                steering.mount(mount, target, jumpTo, vehicle.width / 2.0 + JUMP_REACH)
            }
        }

        context.route.following(path.positions, progress)
    }

    /**
     * The farthest of the next points the vehicle reaches in a straight line without a jump.
     */
    private fun straightAhead(position: Vec3, vehicle: Vehicle, path: Route): Vec3 {
        val moves = Moves(Terrain.live(context.body.level), vehicle)
        var target = path.points[index].position
        for (i in index + 1 until min(path.points.size, index + LOOKAHEAD)) {
            val next = path.points[i]
            if (next.jump || !moves.isStraight(position, next.position)) {
                break
            }
            target = next.position
        }
        return target
    }

    /**
     * Moves on past the points of [path] the vehicle has got to.
     */
    private fun advance(position: Vec3, vehicle: Vehicle, path: Route) {
        val arrive = if (vehicle.kind == VehicleKind.BOAT) BOAT_ARRIVE else MOUNT_ARRIVE
        var reached = -1
        for (i in index until min(path.points.size, index + JOIN_POINTS)) {
            val point = path.points[i]
            if (abs(position.y - point.position.y) > VERTICAL_ARRIVE) {
                continue
            }

            // A point the vehicle went past on the way to the next one counts as reached too.
            val next = path.points.getOrNull(i + 1)
            val distance = position.horizontalDistanceTo(point.position)
            val passed = next != null && !point.jump && !next.jump && distance <= arrive * 2.0 &&
                isCloserTo(next.position, position, point.position)
            if (passed || distance <= arrive) {
                reached = i
            }
        }

        if (reached >= 0) {
            index = reached + 1
            watch.reset()
        }
    }

    /**
     * Whether [first] is horizontally closer to [target] than [second].
     */
    private fun isCloserTo(target: Vec3, first: Vec3, second: Vec3): Boolean =
        target.horizontalDistanceTo(first) < target.horizontalDistanceTo(second)

    private fun trackProgress(position: Vec3, point: Vec3, path: Route) {
        val distance = position.horizontalDistanceTo(point)
        if (!watch.stalls(distance)) {
            return
        }

        watch.reset()
        // A boat pushed against the shore at the end of its route is there.
        if (index == path.points.lastIndex && distance < NEAR_END) {
            index = path.points.size
        } else {
            stuck = true
            stuckCount++
        }
    }

    /**
     * Rides straight at [goal] from within its node, and gets off when that does not get the player there.
     */
    private fun approach(mount: Entity, vehicle: Vehicle, goal: Vec3) {
        if (++approachTicks > APPROACH_TICKS) {
            dismount(goal)
            return
        }

        when (vehicle.kind) {
            VehicleKind.BOAT -> steering.boat(mount, goal, brake = true)
            VehicleKind.MOUNT -> steering.mount(mount, goal, jumpTo = null, reach = 0.0)
        }
        context.route.done()
    }

    /**
     * Gets off, facing [goal] so the player lands on its side.
     */
    private fun dismount(goal: Vec3) {
        if (dismountTicks == 0) {
            routes.reset()
            path = null
        }

        dismountTicks++
        steering.face(goal.subtract(context.body.player.position()).yRot)
        steering.stop(sneak = dismountTicks > DISMOUNT_DELAY)
        context.route.done()
    }

    private fun collectRoute() {
        when (val result = routes.poll()) {
            Outcome.Searching, is Outcome.Failed -> Unit

            Outcome.AlreadyThere -> {
                path = null
                atGoal = true
            }

            is Outcome.Found -> {
                path = result.value
                index = 0
                stuck = false
                atGoal = false
                approachTicks = 0
                watch.reset()
                routes.routeEnds(result.value.points.last().node)
            }
        }
    }

    /**
     * @agent claude-opus-5-5
     * @agent claude-fable-5-2
     */
    private fun planIfNeeded(goal: Vec3, radius: Double, position: Vec3, vehicle: Vehicle) {
        if (stuckCount > MAX_STUCK) {
            path = null
            return
        }

        val path = path
        val riding = path != null && index < path.points.size
        val offPath = path != null && riding && position.horizontal().distanceToSegment(
            path.positions[index].horizontal(), path.positions[index + 1].horizontal(),
        ) > OFF_PATH
        val finishedShort = path != null && !riding && !path.canReach && !path.exhausted
        val nearStage = path != null && routes.endsAtStage &&
            position.horizontalDistanceTo(path.end) < Replanner.STAGE_LEAD
        val urgent = stuck || offPath

        val needed = routes.hasGoalMoved(goal) || offPath || finishedShort || stuck || nearStage
        if (!needed || !routes.isDue(urgent)) {
            return
        }

        val maxLength = maxLength()
        val limits = Limits(maxLength)
        routes.request(position, NearDestination(goal, radius), maxLength, vehicle.kind) { terrain, searchGoal ->
            RouteSearch(terrain, position, searchGoal, vehicle, limits)
        }
        stuck = false
    }

    private val progress = object : Progress {
        override val isMoving: Boolean
            get() = path.let { it != null && index < it.points.size }

        override fun head(partialTicks: Float): Vec3 = (mount ?: context.body.player).getPosition(partialTicks)

        override val ahead: List<Vec3>
            get() = path?.positions?.drop(index + 1) ?: emptyList()
    }

    private companion object {
        const val VERTICAL_MARGIN = 16
        const val OFF_PATH = 3.0
        const val LOOKAHEAD = 6
        const val JOIN_POINTS = 4
        const val BOAT_ARRIVE = 1.0
        const val MOUNT_ARRIVE = 0.6
        const val VERTICAL_ARRIVE = 1.2
        const val JUMP_REACH = 1.0
        const val MIN_PROGRESS = 0.3
        const val STUCK_TICKS = 60
        const val MAX_STUCK = 4
        const val NEAR_END = 2.5
        const val APPROACH_TICKS = 60
        const val DISMOUNT_DELAY = 2

        /**
         * Ticks a boat in water takes to coast to a stop.
         */
        const val BOAT_COAST_TICKS = 10.0
    }

}
