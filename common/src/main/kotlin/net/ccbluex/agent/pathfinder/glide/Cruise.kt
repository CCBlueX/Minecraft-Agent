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
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.simulation.ElytraMotion
import kotlin.math.ceil
import kotlin.math.max

/**
 * From LiquidBounce: [Cruise.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Cruise.kt#L28-L31)
 *
 * @agent claude-opus-5-5
 */
public fun interface ColumnHeights {
    /**
     * Height of the highest block that stops a glide in column [x] [z], or null while it is not loaded.
     */
    public fun top(x: Int, z: Int): Int?
}

/**
 * Flies an elytra to a far [goal] with rockets: climbs clear of every column of [heights] on the line, holds that
 * height, and hands over to a [Landing] near the goal or, out of rockets, to the ground [fallback] finds.
 *
 * From LiquidBounce: [Cruise.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Cruise.kt#L38-L128)
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public class Cruise(
    private val goal: Vec3,
    private val heights: ColumnHeights,
    private val rockets: Rockets,
    private val ceiling: Int,
    private val fallback: (Vec3) -> Vec3?,
) : Steering {

    private var altitude = 0.0
    private var ticks = 0
    private var landing: Landing? = null

    override val target: Vec3
        get() = landing?.target ?: goal

    override fun steer(position: Vec3, deltaMovement: Vec3): Controls {
        landing?.let { return it.steer(position, deltaMovement) }

        val dx = goal.x - position.x
        val dz = goal.z - position.z
        val distance = Mth.length(dx, dz)
        if (distance <= HANDOVER_DISTANCE) {
            return land(goal, position, deltaMovement)
        }
        if (rockets.left == 0 && rockets.boostTicks == 0) {
            return land(fallback(position) ?: goal, position, deltaMovement)
        }

        if (ticks++ % REPLAN_TICKS == 0) {
            altitude = cruiseAltitude(position, goal, heights).coerceAtMost(ceiling - CEILING_MARGIN.toDouble())
        }

        val below = altitude - position.y
        val fire = rockets.canFire && (deltaMovement.horizontalDistance() < MIN_SPEED || below > MAX_BELOW)
        if (fire) {
            rockets.fire()
        }

        val isBoosted = rockets.tick()
        val xRot = ElytraMotion.xRotTowards(below, AIM_AHEAD).coerceIn(MIN_X_ROT, MAX_X_ROT)
        return Controls(ElytraMotion.yRotTowards(dx, dz), xRot, fire, isBoosted)
    }

    private fun land(at: Vec3, position: Vec3, deltaMovement: Vec3): Controls =
        Landing(at, rockets).also { landing = it }.steer(position, deltaMovement)

    public companion object {
        public const val CLEARANCE: Int = 12
        public const val UNKNOWN_CLEARANCE: Int = 24

        private const val MIN_SPEED = 1.2
        private const val MAX_BELOW = 3.0
        private const val AIM_AHEAD = 24.0
        private const val MIN_X_ROT = -40f
        private const val MAX_X_ROT = 15f
        private const val HANDOVER_DISTANCE = 64.0
        private const val REPLAN_TICKS = 20
        private const val CEILING_MARGIN = 4
        private const val SAMPLE_SPACING = 4.0
        private const val CRUISE_SPEED = 1.5
        private const val EFFICIENCY = 0.7
        private const val CLIMB_RATE = 0.6
        private const val SPARE_ROCKETS = 3

        /**
         * [CLEARANCE] above both ends and every loaded column between, [UNKNOWN_CLEARANCE] more while some are not.
         */
        public fun cruiseAltitude(from: Vec3, goal: Vec3, heights: ColumnHeights): Double {
            var top = max(from.y, goal.y)
            var unknown = false
            val dx = goal.x - from.x
            val dz = goal.z - from.z
            val samples = ceil(Mth.length(dx, dz) / SAMPLE_SPACING).toInt()
            for (i in 0..samples) {
                val t = if (samples == 0) 0.0 else i.toDouble() / samples
                val column = heights.top(Mth.floor(from.x + dx * t), Mth.floor(from.z + dz * t))
                if (column == null) {
                    unknown = true
                } else {
                    top = max(top, column.toDouble())
                }
            }
            return top + CLEARANCE + if (unknown) UNKNOWN_CLEARANCE else 0
        }

        public fun rocketsNeeded(distance: Double, climb: Double, rocketTicks: Int): Int {
            if (rocketTicks <= 0) {
                return Int.MAX_VALUE
            }

            val perRocket = rocketTicks * CRUISE_SPEED * EFFICIENCY
            val climbPerRocket = rocketTicks * CRUISE_SPEED * CLIMB_RATE
            return ceil(distance / perRocket).toInt() + ceil(max(climb, 0.0) / climbPerRocket).toInt() + SPARE_ROCKETS
        }
    }

}
