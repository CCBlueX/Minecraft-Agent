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

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.ElytraMotion
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Glides along a route through open space to [target], see [AirwaySearch], keeping [clearance] blocks from every
 * block. Every tick it flies a set of turns and pitches ahead with [ElytraMotion] over the blocks [terrain] gives,
 * then back onto the route, and takes the one that stays clear, close to the route and fast enough for longest.
 * While [landsAtTarget], touching down unhurt next to the target counts as the end of the glide.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public class Passage(
    override val target: Vec3,
    public val rockets: Rockets,
    private val clearance: Double,
    public var landsAtTarget: Boolean,
    private val terrain: () -> Terrain,
) : Steering {

    private var points: List<Vec3> = emptyList()
    private var arcs = DoubleArray(0)

    /**
     * Where the route ends along [points], which may go on past it.
     */
    private var endArc = 0.0
    private var segment = 0
    private var lastXRot = 0f
    private var reachesTarget = true

    /**
     * Whether the target in the air is close enough along the route to fly right through it.
     */
    private var passesTarget = false

    /**
     * The route followed: its corners as feet positions up to its end, and on past it unless it lands there.
     */
    public val route: List<Vec3>
        get() = points

    /**
     * Blocks from the feet to the route at the last [steer].
     */
    public var drift: Double = 0.0
        private set

    /**
     * Blocks along the route from the feet to its end at the last [steer].
     */
    public var remaining: Double = Double.POSITIVE_INFINITY
        private set

    /**
     * Follows [route] from now on, on to the target when it [reachesTarget], else to where it ends.
     */
    public fun follow(route: Route, reachesTarget: Boolean = route.canReach) {
        setPoints(route.points, reachesTarget)
    }

    override fun steer(position: Vec3, deltaMovement: Vec3): Controls {
        if (points.isEmpty()) {
            setPoints(listOf(position), reachesTarget = true)
        }
        val level = terrain()
        val here = project(position, segment)
        segment = here.segment
        drift = here.distance
        remaining = max(0.0, endArc - here.arc)
        passesTarget = reachesTarget && !landsAtTarget && remaining < PASS_DISTANCE

        val speed = deltaMovement.length()
        val below = here.point.y - position.y
        val fire = rockets.canFire && (speed < MIN_SPEED || below > FIRE_BELOW)
        if (fire) {
            rockets.fire()
        }

        val bearing = bearing(position, lookAhead(here, speed))
        var bestCost = Double.POSITIVE_INFINITY
        var bestYRot = bearing
        var bestXRot = 0f
        for (turn in TURNS) {
            for (xRot in X_ROTS) {
                val cost = rollout(level, position, deltaMovement, here, bearing + turn, xRot)
                if (cost < bestCost) {
                    bestCost = cost
                    bestYRot = bearing + turn
                    bestXRot = xRot
                }
            }
        }

        // Finer around the best of the coarse ones
        val coarseYRot = bestYRot
        val coarseXRot = bestXRot
        for (turn in FINE_TURNS) {
            for (pitch in FINE_X_ROTS) {
                val yRot = coarseYRot + turn
                val xRot = (coarseXRot + pitch).coerceIn(MIN_X_ROT, MAX_X_ROT)
                val cost = rollout(level, position, deltaMovement, here, yRot, xRot)
                if (cost < bestCost) {
                    bestCost = cost
                    bestYRot = yRot
                    bestXRot = xRot
                }
            }
        }

        lastXRot = bestXRot
        return Controls(Mth.wrapDegrees(bestYRot), bestXRot, fire, rockets.tick())
    }

    /**
     * What holding [yRot] for a few ticks and then turning back onto the route, at [xRot] throughout, costs: blocks
     * touched or come too close to, drifting from the route, too little speed, and the way not made along it.
     */
    private fun rollout(
        level: Terrain,
        start: Vec3,
        deltaMovement: Vec3,
        here: Projection,
        yRot: Float,
        xRot: Float,
    ): Double {
        var position = start
        var motion = deltaMovement
        var at = here
        val turn = abs(Mth.wrapDegrees(yRot - bearing(start, lookAhead(here, motion.length()))))
        var cost = abs(xRot - lastXRot) * CHANGE_COST + turn * TURN_COST
        var closest = Double.MAX_VALUE
        for (tick in 1..HORIZON_TICKS) {
            val look = if (tick <= TURN_TICKS) yRot else bearing(position, lookAhead(at, motion.length()))
            motion = ElytraMotion.updateFallFlyingMovement(motion, look, xRot, tick <= rockets.boostTicks)
            val next = position.add(motion)
            if (passesTarget) {
                closest = minOf(closest, next.distanceToSqr(target))
            }

            if (!level.isClear(box(position, clearance).expandTowards(motion))) {
                if (!level.isClear(box(position, 0.0).expandTowards(motion))) {
                    if (isLanding(level, position, motion, next)) {
                        return cost - LANDING_REWARD + next.horizontalDistanceTo(target) * LANDING_DISTANCE_COST +
                            motion.horizontalDistance() * LANDING_SPEED_COST
                    }
                    return cost + CONTACT_COST * (HORIZON_TICKS - tick + 1)
                }
                cost += SHELL_COST
            }

            position = next
            if (tick > TURN_TICKS || tick % SAMPLE_TICKS == 0) {
                at = project(position, at.segment)
            }
            if (tick % SAMPLE_TICKS == 0) {
                cost += deviation(position, at) * DRIFT_COST
            }
        }

        cost -= (at.arc - here.arc) * PROGRESS_REWARD
        cost += max(0.0, CRUISE_SPEED - motion.length()) * SLOW_COST
        if (passesTarget) {
            cost += sqrt(closest) * TARGET_COST
        }
        return cost
    }

    /**
     * How far [position] is off the route to the side, and below it, which is height lost for the way ahead.
     * Above it is height to spare.
     */
    private fun deviation(position: Vec3, at: Projection): Double {
        val aside = Mth.length(position.x - at.point.x, position.z - at.point.z)
        val below = at.point.y - position.y
        return max(0.0, aside - DRIFT_TOLERANCE) + max(0.0, below - BELOW_TOLERANCE)
    }

    /**
     * Whether the body coming down from [position] by [motion] to [next] touches down unhurt at the target.
     */
    private fun isLanding(level: Terrain, position: Vec3, motion: Vec3, next: Vec3): Boolean {
        if (!landsAtTarget || motion.y < Landing.SAFE_SINK || next.horizontalDistanceTo(target) > LANDING_REACH ||
            abs(next.y - target.y) > LANDING_HEIGHT) {
            return false
        }
        val bumped = !level.isClear(box(Vec3(next.x, position.y, next.z), 0.0))
        return !bumped || motion.horizontalDistance() <= SAFE_BUMP
    }

    private fun setPoints(route: List<Vec3>, reachesTarget: Boolean) {
        val corners = ArrayList(route)
        this.reachesTarget = reachesTarget
        if (reachesTarget && corners.last().distanceToSqr(target) > MERGE_DISTANCE * MERGE_DISTANCE) {
            corners += target
        }
        if (corners.size == 1) {
            corners += corners[0]
        }
        arcs = DoubleArray(corners.size + 1)
        for (index in 1 until corners.size) {
            arcs[index] = arcs[index - 1] + corners[index].distanceTo(corners[index - 1])
        }
        endArc = arcs[corners.lastIndex]

        // Unless the glide lands there, it goes on the way the route led to its end.
        val last = corners.last()
        val direction = last.subtract(corners[corners.lastIndex - 1])
        if ((!landsAtTarget || !reachesTarget) && direction.lengthSqr() > MIN_LENGTH_SQR) {
            corners += last.add(direction.normalize().scale(EXTENSION))
            arcs[corners.lastIndex] = endArc + EXTENSION
        } else {
            arcs = arcs.copyOf(corners.size)
        }
        points = corners
        segment = 0
    }

    /**
     * The point of the route closest to [position], searched from [from] a few segments on.
     */
    private fun project(position: Vec3, from: Int): Projection {
        var best: Projection? = null
        val first = max(0, from - 1)
        val last = minOf(points.lastIndex - 1, from + WINDOW)
        for (index in first..last) {
            val a = points[index]
            val ab = points[index + 1].subtract(a)
            val lengthSqr = ab.lengthSqr()
            val t = if (lengthSqr < 1.0E-9) 0.0 else (position.subtract(a).dot(ab) / lengthSqr).coerceIn(0.0, 1.0)
            val point = a.add(ab.scale(t))
            val distance = point.distanceTo(position)
            if (best == null || distance < best.distance) {
                best = Projection(index, arcs[index] + t * (arcs[index + 1] - arcs[index]), point, distance)
            }
        }
        return best ?: Projection(0, 0.0, points[0], points[0].distanceTo(position))
    }

    /**
     * The point of the route ahead of [at] the body reaches in a few ticks at [speed].
     */
    private fun lookAhead(at: Projection, speed: Double): Vec3 {
        val arc = at.arc + (speed * LOOK_TICKS).coerceIn(MIN_LOOK, MAX_LOOK)
        var index = at.segment
        while (index < points.lastIndex - 1 && arcs[index + 1] < arc) {
            index++
        }
        val length = arcs[index + 1] - arcs[index]
        val t = if (length < 1.0E-9) 1.0 else ((arc - arcs[index]) / length).coerceIn(0.0, 1.0)
        val a = points[index]
        return a.add(points[index + 1].subtract(a).scale(t))
    }

    private fun bearing(from: Vec3, to: Vec3): Float = ElytraMotion.yRotTowards(to.x - from.x, to.z - from.z)

    private class Projection(val segment: Int, val arc: Double, val point: Vec3, val distance: Double)

    @InternalAgentApi
    public companion object {
        /**
         * The fall-flying body with its feet at [feet] and [margin] blocks around it.
         */
        public fun box(feet: Vec3, margin: Double): AABB {
            val half = AirwaySearch.BODY_SIZE / 2.0 + margin
            return AABB(
                feet.x - half, feet.y - margin, feet.z - half,
                feet.x + half, feet.y + AirwaySearch.BODY_SIZE + margin, feet.z + half,
            )
        }

        private const val HORIZON_TICKS = 30
        private const val TURN_TICKS = 6
        private const val SAMPLE_TICKS = 5
        private const val WINDOW = 24

        private val TURNS = floatArrayOf(-75f, -45f, -25f, -12f, 0f, 12f, 25f, 45f, 75f)
        private val X_ROTS = floatArrayOf(-35f, -20f, -8f, 0f, 8f, 18f, 30f, 45f, 60f)
        private val FINE_TURNS = floatArrayOf(-5f, 0f, 5f)
        private val FINE_X_ROTS = floatArrayOf(-4f, 0f, 4f)
        private const val MIN_X_ROT = -60f
        private const val MAX_X_ROT = 80f

        private const val LOOK_TICKS = 10.0
        private const val MIN_LOOK = 6.0
        private const val MAX_LOOK = 32.0
        private const val EXTENSION = 64.0
        private const val MERGE_DISTANCE = 0.5
        private const val MIN_LENGTH_SQR = 1.0E-6

        private const val CONTACT_COST = 100.0
        private const val SHELL_COST = 8.0
        private const val DRIFT_COST = 1.0
        private const val DRIFT_TOLERANCE = 1.5
        private const val BELOW_TOLERANCE = 3.0
        private const val PROGRESS_REWARD = 2.0
        private const val TARGET_COST = 4.0
        private const val PASS_DISTANCE = 64.0
        private const val SLOW_COST = 10.0
        private const val CHANGE_COST = 0.05
        private const val TURN_COST = 0.02

        /**
         * Speed of the best glide ratio; slower, the glide sinks for little way made.
         */
        private const val CRUISE_SPEED = 1.6

        /**
         * Below this speed a rocket is fired, if there is one.
         */
        private const val MIN_SPEED = 0.6
        private const val FIRE_BELOW = 4.0

        private const val LANDING_REWARD = 5000.0
        private const val LANDING_DISTANCE_COST = 10.0
        private const val LANDING_SPEED_COST = 10.0
        private const val LANDING_REACH = 3.0
        private const val LANDING_HEIGHT = 1.5

        // Elytra wall damage starts at a horizontal speed lost of 0.3 blocks a tick.
        private const val SAFE_BUMP = 0.3
    }

}
