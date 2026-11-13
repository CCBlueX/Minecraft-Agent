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

package net.ccbluex.agent.math

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

/**
 * A view direction in degrees, as Entity.yRot ([yRot]) and Entity.xRot ([xRot]) hold it.
 *
 * From LiquidBounce: [Rotation.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/data/Rotation.kt#L70-L71)
 *
 * @agent claude-opus-5-5
 */
public data class ViewRotation(public val yRot: Float, public val xRot: Float) {

    /**
     * The unit vector this rotation looks along.
     */
    public val viewVector: Vec3
        get() = Vec3.directionFromRotation(xRot, yRot)

    public companion object {
        @JvmField
        public val ZERO: ViewRotation = ViewRotation(0f, 0f)

        /**
         * The rotation that looks along [vector], both angles wrapped to -180 to 180.
         */
        @JvmStatic
        public fun fromVector(vector: Vec3): ViewRotation {
            val horizontal = Mth.length(vector.x, vector.z)
            val yRot = atan2(vector.z, vector.x).toDegrees().toFloat() - QUARTER_TURN
            val xRot = -atan2(vector.y, horizontal).toDegrees().toFloat()
            return ViewRotation(Mth.wrapDegrees(yRot), Mth.wrapDegrees(xRot))
        }

        private const val QUARTER_TURN = 90f
    }
}
