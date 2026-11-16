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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.walk.Course.Companion.LANDING_PROBE
import net.ccbluex.agent.pathfinder.walk.Course.Companion.MAX_AIR_TICKS
import net.ccbluex.agent.simulation.PlayerMotion
import kotlin.math.abs

/**
 * The part of a jump on the ground along [course]: [motion] runs towards the aim, or hops towards the target, before
 * it takes off. Not thread-safe.
 *
 * From LiquidBounce: [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L389-L415), [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L481-L496)
 *
 * @agent claude-opus-5-5
 */
internal class RunUp(
    private val course: Course,
    private val motion: PlayerMotion,
    private val flight: Airtime,
    private val abilities: WalkAbilities,
) {

    /**
     * Where [Jumps.takeOff] puts the position after every simulated tick, or null.
     */
    var path: MutableList<Vec3>? = null

    fun tick(yRot: Float, jump: Boolean = false) {
        motion.tick(yRot, 1, 0, jump, abilities.canSprint)
        flight.noteObstruction()
        path?.add(motion.position)
    }

    fun yRotAt(tick: Int, firstYRot: Float?) =
        if (tick == 0 && firstYRot != null) firstYRot else flight.yRotTo(course.aimX, course.aimZ)

    /**
     * How far [hops] from rest at the back of the [runway] get along it; NaN when one leaves its floor.
     */
    fun hopTravel(runway: Double, hops: Int): Double {
        motion.reset(course.behind(runway), PlayerMotion.RESTING, onGround = true)
        val startX = motion.x
        val startZ = motion.z
        if (hop(hops) < 0) {
            return Double.NaN
        }
        return (motion.x - startX) * course.dirX + (motion.z - startZ) * course.dirZ
    }

    /**
     * Makes [motion] hop [hops] times in a row; the ticks it took, or -1 when a hop leaves the floor before the edge.
     */
    fun hop(hops: Int): Int {
        var ticks = 0
        repeat(hops) {
            var air = 0
            do {
                tick(flight.yRotToTarget(), jump = air == 0)
                air++
            } while (!motion.onGround && air < MAX_AIR_TICKS)
            val progress = (motion.x - course.fromX) * course.dirX + (motion.z - course.fromZ) * course.dirZ
            if (!motion.onGround || abs(motion.y - course.fromY) > LANDING_PROBE || progress > course.edge) {
                return -1
            }
            ticks += air
        }
        return ticks
    }
}
