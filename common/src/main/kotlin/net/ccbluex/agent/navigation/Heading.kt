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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.getInputYRot
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.walk.BlockActions
import net.ccbluex.agent.navigation.walk.Follower
import net.ccbluex.agent.world.Input
import kotlin.math.abs

/**
 * Where the walk of a [Navigator] heads this tick, whether it sprints there, and the way the player faces for it.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L173-L221)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal class Heading(
    private val context: NavigationContext,
    private val follower: Follower,
    private val actions: BlockActions,
    private val lookAhead: LookAhead,
) {

    private val body get() = context.body
    private val player get() = body.player

    var target: Vec3? = null
    var sprint = false
    private var lastLookYRot = Float.NaN

    /**
     * Where the walk heads: the aim of a block action, else the way to the next point, else the movement keys. Only
     * the yaw steers; the pitch stays where the view is, never a fixed angle.
     */
    fun rotation(lastInput: Input): ViewRotation {
        actions.rotation?.let { return it }
        follower.view?.let { return it }

        val target = target
        val yRot = target?.let(::lookYRot)
        if (target == null || yRot == null || yRot.isNaN()) {
            return ViewRotation(player.getInputYRot(lastInput), player.xRot)
        }
        return ViewRotation(yRot, if (follower.dive) diveXRot(target) else player.xRot)
    }

    // Looking the way the walk goes lets the keys press forward under movement correction, so the player can sprint.
    fun lookAlongPath() {
        actions.rotation?.let { aim ->
            lastLookYRot = aim.yRot
            return
        }
        follower.view?.let { view ->
            lastLookYRot = view.yRot
            body.look(view, LookPurpose.MOVEMENT)
            return
        }

        val target = target
        val yRot = if (target == null) lastLookYRot else lookYRot(target)
        if (yRot.isNaN()) {
            return
        }
        lastLookYRot = yRot
        val xRot = when {
            target == null -> player.xRot
            follower.dive -> diveXRot(target)
            // Between block actions the view stays on the next block rather than coming up to the horizon.
            else -> follower.nextBlock?.let(::xRotAt)
                ?: lookAhead.xRot(follower.pointAhead(player.position(), lookAhead.distance.toDouble()) ?: target)
        }
        body.look(ViewRotation(yRot, xRot), LookPurpose.MOVEMENT)
    }

    private fun xRotAt(pos: BlockPos) = ViewRotation.fromVector(Vec3.atCenterOf(pos).subtract(player.eyePosition)).xRot

    /**
     * NaN with nothing to look at yet. A target the player is about to pass would turn the look around, so the path
     * beyond it is looked at or the last look held; while building the player keeps facing the way of the last aim.
     */
    private fun lookYRot(target: Vec3): Float {
        val position = player.position()
        val look = target.takeIf { it.horizontalDistanceToSqr(position) >= LOOK_DISTANCE * LOOK_DISTANCE }
            ?: follower.lookAhead(target, position, LOOK_DISTANCE)
            ?: return lastLookYRot
        val yRot = look.subtract(position).yRot
        val backwards = follower.building && lastLookYRot.isFinite() &&
            abs(Mth.wrapDegrees(yRot - lastLookYRot)) > QUARTER_TURN
        return if (backwards) Mth.wrapDegrees(yRot + 2 * QUARTER_TURN) else yRot
    }

    private fun diveXRot(target: Vec3) = ViewRotation.fromVector(target.subtract(player.position())).xRot

    fun walkTowards(keys: MovementKeys, target: Vec3, sprint: Boolean) {
        this.target = target
        this.sprint = sprint
        keys.directionalInput = getDirectionalInputTowards(target.subtract(player.position()), body.inputYRot)
        keys.jump = player.isInWater
        keys.sneak = false
    }

    fun stop(keys: MovementKeys) {
        target = null
        sprint = false
        keys.directionalInput = Input.EMPTY
        keys.jump = player.isInWater
        keys.sneak = false
    }

    fun forgetLook() {
        lastLookYRot = Float.NaN
    }

    fun clear() {
        target = null
        sprint = false
        lastLookYRot = Float.NaN
    }

    private companion object {
        /**
         * How far ahead a point has to be for the look to follow it; a step passed by a bit lies right behind.
         */
        const val LOOK_DISTANCE = 0.75
        const val QUARTER_TURN = 90f
    }
}
