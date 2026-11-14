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

import net.ccbluex.agent.math.ViewRotation
import net.minecraft.util.Mth
import kotlin.math.abs

/**
 * How a body that turns itself turns towards a [LookPurpose.MOVEMENT], [LookPurpose.AIM] or [LookPurpose.STEER]
 * rotation, a tick at a time; see [Body.look] for when STEER is taken as given. Bodies call it about once a tick, so
 * an implementation may keep state, as an accelerating turn does. A host with a rotation system of its own, which
 * paces turns itself, gives its body [instant].
 *
 * @agent claude-opus-5-5
 */
public fun interface Turning {

    /**
     * The rotation one tick after [from] on the way to [to].
     */
    public fun turn(from: ViewRotation, to: ViewRotation, purpose: LookPurpose): ViewRotation

    public companion object {

        /**
         * At most [degrees] a tick on the yaw and [xRotDegrees] on the pitch, at constant speed; the yaw takes the
         * short way round and goes on from [from]'s without wrapping.
         */
        @JvmStatic
        @JvmOverloads
        public fun linear(degrees: Float, xRotDegrees: Float = degrees): Turning = Turning { from, to, _ ->
            ViewRotation(
                from.yRot + Mth.wrapDegrees(to.yRot - from.yRot).coerceIn(-degrees, degrees),
                from.xRot + (to.xRot - from.xRot).coerceIn(-xRotDegrees, xRotDegrees),
            )
        }

        /**
         * A turn as a hand eases into a look: [share] of what is left a tick, at least [least] degrees and at most
         * [most], the pitch at most [xRotMost]; the yaw takes the short way round.
         */
        @JvmStatic
        @JvmOverloads
        public fun eased(
            most: Float = CALM_TURN,
            xRotMost: Float = most * X_ROT_SHARE,
            share: Float = EASE_SHARE,
            least: Float = EASE_LEAST,
        ): Turning = Turning { from, to, _ ->
            ViewRotation(
                from.yRot + easedStep(Mth.wrapDegrees(to.yRot - from.yRot), most, share, least),
                from.xRot + easedStep(to.xRot - from.xRot, xRotMost, share, least),
            )
        }

        /**
         * [turning], never more a tick than a person turns: [CALM_TURN] degrees on each axis for walking, looking
         * and aiming, [FLICK_TURN] for a [LookPurpose.STEER] flick. Client bodies put every turn through it, since
         * anticheats see a turn no hand makes; [instant] stays as it is, as the host's own rotation system paces it.
         */
        @JvmStatic
        public fun humanPaced(turning: Turning): Turning {
            if (turning === INSTANT) {
                return INSTANT
            }
            return Turning { from, to, purpose ->
                val turned = turning.turn(from, to, purpose)
                val most = if (purpose == LookPurpose.STEER) FLICK_TURN else CALM_TURN
                ViewRotation(
                    from.yRot + Mth.wrapDegrees(turned.yRot - from.yRot).coerceIn(-most, most),
                    from.xRot + (turned.xRot - from.xRot).coerceIn(-most, most),
                )
            }
        }

        /**
         * Straight to the rotation asked for: the turn is left to a rotation system of the host that paces it.
         */
        @JvmStatic
        public fun instant(): Turning = INSTANT

        private val INSTANT = Turning { _, to, _ -> to }

        /**
         * Degrees a tick a calm turn goes at most: walking, looking about, aiming.
         */
        public const val CALM_TURN: Float = 40f

        /**
         * Degrees a tick a flick goes at most.
         */
        public const val FLICK_TURN: Float = 90f

        private const val X_ROT_SHARE = 0.7f
        private const val EASE_SHARE = 0.45f
        private const val EASE_LEAST = 1.5f

        private fun easedStep(left: Float, most: Float, share: Float, least: Float): Float {
            val size = abs(left)
            val step = (size * share).coerceIn(minOf(least, size, most), most)
            return if (left < 0f) -step else step
        }
    }
}
