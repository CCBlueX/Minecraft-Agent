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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.withLength
import net.ccbluex.agent.pathfinder.level.BlockRegion
import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Search
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.ElytraMotion
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_HORIZONTAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_JUMP_POWER
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_VERTICAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.DEFAULT_BASE_GRAVITY
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A glide from [start] with its take-off: where the feet pass and land, flown by a [Landing] with [approach].
 *
 * @agent claude-opus-5-5
 */
public class Route(
    public val start: Vec3,
    public val points: List<Vec3>,
    public val landing: Vec3,
    public val rockets: Int,
    public val approach: Double,
)

/**
 * Plans a glide by flying it tick by tick with a [Landing]: a jump towards [goal], the elytra opened in the air
 * and the glide until the feet touch down unhurt next to the goal; first directly, then keeping high until close.
 *
 * From LiquidBounce: [PathSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/PathSearch.kt#L50-L203)
 *
 * @agent claude-opus-5-5
 */
public class RouteSearch(
    private val terrain: Terrain,
    private val start: Vec3,
    private val goal: Vec3,
    private val radius: Double,
    private val abilities: GlideAbilities,
) : Search<Route> {

    private var attempt = Attempt(approach = 0.0)
    private var result: Outcome<Route> = Outcome.Searching

    override fun advance(): Outcome<Route> {
        repeat(TICKS_PER_SLICE) {
            if (result != Outcome.Searching) {
                return result
            }

            result = attempt.tick()
            if (result is Outcome.Failed && attempt.approach == 0.0) {
                attempt = Attempt(HIGH_APPROACH)
                result = Outcome.Searching
            }
        }
        return result
    }

    override fun preview(): List<Vec3> = ArrayList(attempt.points)

    /**
     * One flight of the glide, keeping high until [approach] blocks from the goal.
     */
    private inner class Attempt(val approach: Double) {

        val points = arrayListOf(start)
        private val rockets = Rockets(abilities.rockets, abilities.rocketTicks)
        private val control = Landing(goal, rockets, approach)
        private var position = start
        private var motion = Vec3.ZERO
        private var tick = 0

        fun tick(): Outcome<Route> {
            if (tick >= MAX_TICKS || tick >= abilities.glideTicks) {
                return Outcome.Failed(Failure.TOO_COMPLEX)
            }

            val takingOff = tick < TAKE_OFF_TICKS
            val next = if (takingOff) {
                jump()
            } else {
                val input = control.steer(position, motion)
                motion = ElytraMotion.updateFallFlyingMovement(motion, input.yRot, input.xRot, input.isBoosted)
                position.add(motion)
            }

            val height = if (takingOff) Terrain.PLAYER_HEIGHT else GLIDE_HEIGHT
            if (!terrain.isClear(terrain.box(next.x, next.y, next.z, height))) {
                return land(next)
            }
            if (next.y < terrain.minY) {
                return Outcome.Failed(Failure.NO_PATH)
            }

            position = next
            tick++
            if (tick % POINT_TICKS == 0) {
                points += position
            }
            return Outcome.Searching
        }

        /**
         * A sprint jump towards the goal and the ticks in the air before the elytra opens.
         */
        private fun jump(): Vec3 {
            if (tick == 0) {
                val toGoal = Vec3(goal.x - start.x, 0.0, goal.z - start.z).withLength(TAKE_OFF_SPEED)
                motion = Vec3(toGoal.x, BASE_JUMP_POWER.toDouble(), toGoal.z)
            }
            val next = position.add(motion)
            motion = Vec3(
                motion.x * BASE_HORIZONTAL_AIR_DRAG,
                (motion.y - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG,
                motion.z * BASE_HORIZONTAL_AIR_DRAG,
            )
            return next
        }

        /**
         * Lands where the glide meets the ground; a step brushed too slowly to get hurt is slid down along instead.
         */
        private fun land(next: Vec3): Outcome<Route> {
            val bumped = !terrain.isClear(terrain.box(next.x, position.y, next.z, GLIDE_HEIGHT))
            if (tick < TAKE_OFF_TICKS || motion.y < Landing.SAFE_SINK ||
                bumped && motion.horizontalDistance() > SAFE_BUMP) {
                return Outcome.Failed(Failure.NO_PATH)
            }

            val spot = if (bumped) position else next
            val ground = groundUnder(spot, if (bumped) position.y - BUMP_DROP else next.y)
            if (ground.isNaN()) {
                return Outcome.Failed(Failure.NO_PATH)
            }

            val landing = Vec3(spot.x, ground, spot.z)
            val near = landing.horizontalDistanceTo(goal) <= max(radius, LANDING_REACH) &&
                abs(landing.y - goal.y) <= LANDING_HEIGHT
            if (!near) {
                return Outcome.Failed(Failure.NO_PATH)
            }

            val used = abilities.rockets - rockets.left
            return Outcome.Found(Route(start, points + landing, landing, used, approach))
        }

        /**
         * The highest ground down to [bottom] under any part of the feet at [spot], which land on edges; or NaN.
         */
        private fun groundUnder(spot: Vec3, bottom: Double): Double {
            var ground = Double.NaN
            for (x in Mth.floor(spot.x - HALF_WIDTH)..Mth.floor(spot.x + HALF_WIDTH)) {
                for (z in Mth.floor(spot.z - HALF_WIDTH)..Mth.floor(spot.z + HALF_WIDTH)) {
                    for (y in Mth.floor(position.y) downTo Mth.floor(bottom)) {
                        val height = terrain.standHeight(x, y, z)
                        if (height <= position.y && height >= bottom - GROUND_TOLERANCE && !(height <= ground)) {
                            ground = height
                        }
                    }
                }
            }
            return ground
        }

    }

    public companion object {
        private const val TICKS_PER_SLICE = 200
        private const val MAX_TICKS = 2400
        private const val HIGH_APPROACH = 16.0

        // Elytra wall damage starts at a horizontal speed lost of 0.3 blocks a tick.
        private const val SAFE_BUMP = 0.3
        private const val BUMP_DROP = 2.0
        private const val TAKE_OFF_TICKS = 2
        private const val POINT_TICKS = 4
        private const val GLIDE_HEIGHT = 0.6
        private const val HALF_WIDTH = Terrain.PLAYER_WIDTH / 2.0
        private const val TAKE_OFF_SPEED = 0.3
        private const val GROUND_TOLERANCE = 0.01
        private const val LANDING_REACH = 3.0
        private const val LANDING_HEIGHT = 1.0
        private const val VERTICAL_MARGIN = 24
        private const val HORIZONTAL_MARGIN = 48

        /**
         * The blocks a glide can pass: around both ends, with room to circle down to the goal and to climb above.
         */
        public fun region(start: Vec3, goal: Vec3): BlockRegion {
            val margin = min(start.horizontalDistanceTo(goal), HORIZONTAL_MARGIN.toDouble()).toInt() + 2
            return BlockRegion(
                Mth.floor(min(start.x, goal.x)) - margin,
                Mth.floor(min(start.y, goal.y)) - 2,
                Mth.floor(min(start.z, goal.z)) - margin,
                Mth.floor(max(start.x, goal.x)) + margin,
                Mth.floor(max(start.y, goal.y)) + VERTICAL_MARGIN,
                Mth.floor(max(start.z, goal.z)) + margin,
            )
        }
    }

}
