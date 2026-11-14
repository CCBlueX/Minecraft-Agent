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
    }
}
