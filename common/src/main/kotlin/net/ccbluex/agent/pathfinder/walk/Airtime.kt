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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.pathfinder.walk.Course.Companion.HALF_BLOCK
import net.ccbluex.agent.pathfinder.walk.Course.Companion.MAX_AIR_TICKS
import net.ccbluex.agent.pathfinder.walk.Course.Companion.MAX_BELOW
import net.ccbluex.agent.simulation.PlayerMotion
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/**
 * The air part of a jump along [course]: flies [motion] on to the ground, steered onto the target, and scores the
 * landing. A player that [holdsYRot] pulls back without sprinting once past the middle of the target. Not thread-safe.
 *
 * From LiquidBounce: [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L558-L613)
 *
 * @agent claude-opus-5-5
 */
internal class Airtime(
    private val course: Course,
    private val motion: PlayerMotion,
    private val abilities: WalkAbilities,
    private val holdsYRot: Boolean,
) {

    private val maxFall = abilities.safeFall + if (abilities.riskyJumps) RISKY_EXTRA_FALL else 0.0

    /**
     * Ticks in the air of the last [land].
     */
    var airTicks = 0
        private set

    /**
     * The jump that lands from [motion] in the air, or null when it does not land on the target.
     */
    fun jump(runUp: Vec3, takeoff: Int, jumps: Boolean, hops: Int): Jump? {
        val margin = land()
        if (margin.isNaN()) {
            return null
        }
        return Jump(runUp, takeoff, jumps, airTicks, margin, hops, Vec3(course.aimX, course.targetHeight, course.aimZ))
    }

    /**
     * Flies [motion] on to the ground; the margin of its landing on the target, NaN for any other end.
     */
    fun land(): Double {
        val floor = min(course.targetHeight, course.fromY) - MAX_BELOW
        val catches = course.catches
        airTicks = 1
        while (!motion.onGround && motion.y > floor && airTicks < MAX_AIR_TICKS) {
            if (catches && course.isInTarget(motion.x, motion.y, motion.z)) {
                return course.margin(motion.x, motion.z)
            }
            val back = !catches && overshoots()
            // Moving away from the middle of the target, a player that holds its yaw faces away and pulls back, which
            // does not sprint.
            val pullsBack = holdsYRot &&
                motion.deltaX * (course.targetX - motion.x) + motion.deltaZ * (course.targetZ - motion.z) < 0.0
            val sprint = abilities.canSprint && !back && !pullsBack
            motion.tick(yRotToTarget(), if (back) -1 else 1, 0, jump = false, sprint = sprint)
            noteObstruction()
            airTicks++
        }

        if (catches || !motion.onGround || motion.landingFall > maxFall) {
            return Double.NaN
        }
        val touchdown = footing()
        // A landing on the lip of a block is followed by a few careful steps onto it, as into a hole between walls.
        repeat(SETTLE_TICKS) {
            motion.tick(yRotToTarget(), 1, 0, jump = false, sprint = false, sneak = true)
        }
        val settled = footing()
        return if (touchdown.isNaN() || settled > touchdown) settled else touchdown
    }

    /**
     * Marks the course obstructed when [motion] hit something other than the side of the target.
     */
    fun noteObstruction() {
        val reach = HALF_BLOCK + PlayerMotion.HALF_WIDTH + SIDE_SLACK
        if (motion.horizontalCollision &&
            (abs(motion.x - course.targetX) > reach || abs(motion.z - course.targetZ) > reach)
        ) {
            course.obstructed = true
        }
    }

    fun yRotToTarget() = yRotTo(course.targetX, course.targetZ)

    fun yRotTo(x: Double, z: Double): Float =
        (atan2(z - motion.z, x - motion.x) * Mth.RAD_TO_DEG - QUARTER_TURN).toFloat()

    private fun footing(): Double {
        val target = course.target
        return when {
            !motion.onGround || abs(motion.y - course.targetHeight) > HEIGHT_TOLERANCE -> Double.NaN
            motion.standsOn(target.x, target.y, target.z) -> motion.footing(target.x, target.y, target.z)
            motion.standsOn(target.x, target.y - 1, target.z) -> motion.footing(target.x, target.y - 1, target.z)
            else -> Double.NaN
        }
    }

    private fun overshoots(): Boolean {
        val toX = course.targetX - motion.x
        val toZ = course.targetZ - motion.z
        val remaining = Mth.length(toX, toZ)
        if (remaining < 1.0E-4) {
            return false
        }
        val speed = (motion.deltaX * toX + motion.deltaZ * toZ) / remaining
        return course.overshoots(remaining, speed, motion.y, motion.deltaY)
    }

    private companion object {
        const val HEIGHT_TOLERANCE = 1.0E-3
        const val SETTLE_TICKS = 3
        const val QUARTER_TURN = 90.0
        const val RISKY_EXTRA_FALL = 4.5

        /**
         * How close to the side of the target a run into it counts as a landing short of it.
         */
        const val SIDE_SLACK = 0.05
    }
}
