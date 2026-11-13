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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.simulation.PlayerMotion.Companion.STEP_HEIGHT
import kotlin.math.abs
import kotlin.math.sign

private const val EDGE_STEP = 0.05

/**
 * Player.maybeBackOffFromEdge: how much of a movement, held in [x] and [z], keeps a sneaking player over the ground
 * it is on.
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L313-L328)
 *
 * @agent claude-fable-5-2
 */
internal class EdgeBackOff {
    var x = 0.0
        private set
    var z = 0.0
        private set

    /**
     * Shortens [dx] and [dz] until [motion] no longer falls off.
     */
    fun backOff(motion: PlayerMotion, dx: Double, dz: Double) {
        x = dx
        z = dz
        while (x != 0.0 && motion.canFall(x, 0.0, STEP_HEIGHT.toDouble())) {
            x = shortened(x)
        }
        while (z != 0.0 && motion.canFall(0.0, z, STEP_HEIGHT.toDouble())) {
            z = shortened(z)
        }
        while (x != 0.0 && z != 0.0 && motion.canFall(x, z, STEP_HEIGHT.toDouble())) {
            x = shortened(x)
            z = shortened(z)
        }
    }

    private fun shortened(delta: Double) = if (abs(delta) <= EDGE_STEP) 0.0 else delta - sign(delta) * EDGE_STEP
}
