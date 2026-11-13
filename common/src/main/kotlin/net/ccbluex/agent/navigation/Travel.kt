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

import net.ccbluex.agent.navigation.flight.Flying
import net.ccbluex.agent.navigation.flight.captureFlight
import net.ccbluex.agent.navigation.glide.Gliding
import net.ccbluex.agent.navigation.glide.RoofBounce
import net.ccbluex.agent.navigation.glide.captureGlide
import net.ccbluex.agent.navigation.ride.Riding
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * The ways of a [Navigator] besides walking: riding a vehicle, flying, gliding and bouncing along a roof. Taking one
 * up stops the walk through [stopWalking]; a ride only [resetWalk]s, as it shows its own route.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L330-L387)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class Travel(
    private val context: NavigationContext,
    private val stopWalking: () -> Unit,
    private val resetWalk: () -> Unit,
) {

    private val flight = Flying(context)
    private val glide = Gliding(context)
    private val bounce = RoofBounce(context)
    private val ride = Riding(context) { context.options().maxLength }

    /**
     * Where the way taken this tick steers to, null where it turns the player itself.
     */
    var target: Vec3? = null
        private set

    /**
     * Whether the way taken this tick sprints.
     */
    var sprint = false
        private set

    /**
     * Drives a ridden vehicle one tick towards [goal]; false without one.
     */
    fun ride(goal: Vec3, radius: Double): Boolean {
        if (!ride.navigate(goal, radius)) {
            return false
        }

        haltFlying()
        resetWalk()
        return true
    }

    /**
     * Flies, glides or bounces one tick towards [goal]; false to walk. [straight] tells a straight walk gets there.
     */
    fun fly(goal: Vec3, radius: Double, straight: Boolean): Boolean =
        fly(goal, radius, straight, context.body.inputYRot) ||
            glide(goal, radius, straight, context.body.inputYRot) ||
            bounce(goal, context.body.inputYRot)

    private fun fly(goal: Vec3, radius: Double, straight: Boolean, frameYRot: Float): Boolean {
        // Goals a straight walk reaches are walked to unless the player flies already.
        val abilities = context.captureFlight(flight.handling)?.takeUnless { straight && !it.isFlying }
        if (abilities == null) {
            flight.halt()
            return false
        }

        if (!flight.isActive) {
            glide.halt()
            bounce.halt()
            stopWalking()
        }

        flight.navigate(goal, radius, context.options().maxLength, abilities, frameYRot)
        target = flight.target
        sprint = flight.sprint
        return true
    }

    private fun glide(goal: Vec3, radius: Double, straight: Boolean, frameYRot: Float): Boolean {
        val wasGliding = glide.isActive
        if (!glide.navigate(goal, radius, context.captureGlide(), straight, frameYRot)) {
            return false
        }

        if (!wasGliding) {
            stopWalking()
        }
        target = null
        sprint = glide.sprint
        return true
    }

    private fun bounce(goal: Vec3, frameYRot: Float): Boolean {
        val wasBouncing = bounce.isActive
        if (!bounce.navigate(goal, context.captureGlide(), frameYRot)) {
            return false
        }

        if (!wasBouncing) {
            stopWalking()
        }
        target = null
        sprint = true
        return true
    }

    fun halt() {
        ride.halt()
        haltFlying()
    }

    fun haltFlying() {
        flight.halt()
        glide.halt()
        bounce.halt()
    }

    fun blockChanged(pos: BlockPos) {
        flight.blockChanged(pos)
        glide.blockChanged(pos)
        ride.blockChanged(pos)
    }

    fun levelChanged() {
        flight.levelChanged()
        glide.levelChanged()
        ride.levelChanged()
    }
}
