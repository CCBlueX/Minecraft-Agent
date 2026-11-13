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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.walk.Step
import net.minecraft.world.phys.Vec3
import kotlin.math.max

/**
 * Keeps the aim of a player moving fast from lying behind the way it moves, which shows it overshot the path.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L657-L678)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class Overshoot(private val context: NavigationContext) {

    /**
     * Times the aim lay behind the way the player moved fast.
     */
    var count = 0
        private set
    private var reversing = false

    /**
     * [aim], unless it lies behind the way the player moves fast: then the point [pointAhead] on the path the player
     * covers in [STEER_TICKS], and in the air no aim at all rather than one back, which only turns in an arc.
     */
    fun forwardAim(position: Vec3, aim: Vec3, step: Step, pointAhead: (Vec3, Double) -> Vec3?): Vec3 {
        val player = context.body.player
        val motion = player.deltaMovement
        val speed = motion.horizontalDistance()
        val ahead = (aim.x - position.x) * motion.x + (aim.z - position.z) * motion.z
        val reverses = speed > FAST_SPEED && ahead < 0.0
        if (reverses && !reversing) {
            count++
        }
        reversing = reverses
        if (!reverses || !step.isPlain) {
            return aim
        }

        val point = pointAhead(position, max(speed * STEER_TICKS, MIN_STEER))
        val isAhead = point != null &&
            (point.x - position.x) * motion.x + (point.z - position.z) * motion.z >= 0.0
        return when {
            isAhead -> point
            player.onGround() -> aim
            else -> position
        }
    }

    private companion object {
        /**
         * Blocks per tick from which an aim behind counts as overshooting, about Speed's pace.
         */
        const val FAST_SPEED = 0.3

        /**
         * Ticks of travel the aim lies ahead on the path after an overshoot, and at least this far.
         */
        const val STEER_TICKS = 3.0
        const val MIN_STEER = 1.0
    }
}
