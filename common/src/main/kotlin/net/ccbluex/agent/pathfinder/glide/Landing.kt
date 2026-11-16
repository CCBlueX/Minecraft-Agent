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
import net.ccbluex.agent.simulation.ElytraMotion
import kotlin.math.abs

/**
 * Glides to [target]: every tick it takes the pitch that, held until touch-down, lands just short of it unhurt. Too
 * high, it circles down; too low or too far, it fires a rocket and climbs. With an [approach], it keeps as high as
 * it can until that close to the target, over ridges on the way.
 *
 * From LiquidBounce: [Landing.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Landing.kt#L33-L149)
 *
 * @agent claude-opus-5-5
 */
public class Landing(
    override val target: Vec3,
    public val rockets: Rockets,
    private val approach: Double = 0.0,
) : Steering {

    private val sweep = XRotSweep()

    override fun steer(position: Vec3, deltaMovement: Vec3): Controls {
        val dx = target.x - position.x
        val dz = target.z - position.z
        val distance = Mth.length(dx, dz)
        val bearing = ElytraMotion.yRotTowards(dx, dz)
        sweepXRots(position, deltaMovement, bearing, distance, dx, dz)

        if (approach > 0.0 && distance > approach && rockets.boostTicks == 0 && sweep.farthestAlong >= 0.0) {
            return Controls(bearing, sweep.farthestXRot, shouldFireRocket = false, isBoosted = false)
        }

        // No pitch comes down within the horizon: far too high, which no rocket helps with.
        val overhead = sweep.bestError == Double.POSITIVE_INFINITY && position.y > target.y
        val missed = sweep.bestError > LANDING_TOLERANCE
        val fire = missed && !overhead && sweep.bestAlong < 0.0 && rockets.canFire
        if (fire) {
            rockets.fire()
        }

        val isBoosted = rockets.tick()
        val orbit = Mth.wrapDegrees(bearing + ORBIT_TURN)
        return when {
            isBoosted -> {
                val climb = target.y + CLIMB_ABOVE + distance * CLIMB_SLOPE - position.y
                val xRot = ElytraMotion.xRotTowards(climb, distance).coerceIn(MIN_CLIMB_X_ROT, MAX_CLIMB_X_ROT)
                Controls(bearing, xRot, fire, isBoosted = true)
            }

            overhead -> Controls(orbit, SPIRAL_X_ROT, fire, isBoosted = false)
            missed && sweep.bestAlong > 0.0 -> Controls(orbit, PARACHUTE_X_ROT, fire, isBoosted = false)
            else -> Controls(bearing, sweep.bestXRot, fire, isBoosted = false)
        }
    }

    /**
     * Finds the pitch that lands just short of the target unhurt, and the one that lands farthest along the way.
     */
    private fun sweepXRots(position: Vec3, deltaMovement: Vec3, bearing: Float, distance: Double, dx: Double, dz: Double) {
        val unitX = if (distance > MIN_DISTANCE) dx / distance else 0.0
        val unitZ = if (distance > MIN_DISTANCE) dz / distance else 1.0
        sweep.reset()
        for (xRot in X_ROTS) {
            val touchdown = predict(position, deltaMovement, bearing, xRot) ?: continue
            val along = (touchdown.x - target.x) * unitX + (touchdown.z - target.z) * unitZ
            var error = abs(along + SHORT_OF_TARGET)
            if (touchdown.y < SAFE_SINK) {
                error += UNSAFE_PENALTY
            }
            if (error < sweep.bestError) {
                sweep.bestError = error
                sweep.bestXRot = xRot
                sweep.bestAlong = along
            }
            if (along > sweep.farthestAlong) {
                sweep.farthestAlong = along
                sweep.farthestXRot = xRot
            }
        }
    }

    /**
     * Where holding [yRot] and [xRot] comes down to the height of the target: x and z, with the sink speed as y.
     */
    private fun predict(position: Vec3, deltaMovement: Vec3, yRot: Float, xRot: Float): Vec3? {
        var x = position.x
        var y = position.y
        var z = position.z
        var motion = deltaMovement
        for (tick in 0 until HORIZON_TICKS) {
            motion = ElytraMotion.updateFallFlyingMovement(motion, yRot, xRot, tick < rockets.boostTicks)
            x += motion.x
            y += motion.y
            z += motion.z
            if (y <= target.y && motion.y < 0.0) {
                return Vec3(x, motion.y, z)
            }
        }
        return null
    }

    private class XRotSweep {
        var bestError = Double.POSITIVE_INFINITY
        var bestXRot = 0f
        var bestAlong = Double.NEGATIVE_INFINITY
        var farthestXRot = 0f
        var farthestAlong = Double.NEGATIVE_INFINITY

        fun reset() {
            bestError = Double.POSITIVE_INFINITY
            bestXRot = 0f
            bestAlong = Double.NEGATIVE_INFINITY
            farthestXRot = 0f
            farthestAlong = Double.NEGATIVE_INFINITY
        }
    }

    public companion object {
        /**
         * Below this vertical speed a touch-down hurts.
         */
        public const val SAFE_SINK: Double = -0.45

        private val X_ROTS = floatArrayOf(-45f, -35f, -25f, -15f, -10f, -5f, 0f, 5f, 10f, 20f)
        private const val HORIZON_TICKS = 400
        private const val MIN_DISTANCE = 1.0E-6
        private const val SHORT_OF_TARGET = 1.0
        private const val UNSAFE_PENALTY = 100.0
        private const val LANDING_TOLERANCE = 3.0
        private const val ORBIT_TURN = 90f
        private const val PARACHUTE_X_ROT = -45f
        private const val SPIRAL_X_ROT = 30f
        private const val CLIMB_ABOVE = 4.0
        private const val CLIMB_SLOPE = 0.15
        private const val MIN_CLIMB_X_ROT = -40f
        private const val MAX_CLIMB_X_ROT = 10f
    }

}
