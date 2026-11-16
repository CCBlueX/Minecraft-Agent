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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.Player

/**
 * A prediction of where a player goes, advanced one game tick per [tick].
 *
 * From LiquidBounce: [PlayerSimulation.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulation.kt#L23-L33)
 */
public interface PlayerSimulation {

    /**
     * The feet after the ticks run so far.
     */
    public val pos: Vec3

    public fun tick()

    /**
     * A player that stays at [pos].
     *
     * From LiquidBounce: [PlayerSimulation.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulation.kt#L28-L32)
     */
    public data class Rigid(override val pos: Vec3) : PlayerSimulation {
        override fun tick() {
            // Stays where it is
        }
    }

    /**
     * A copy of a player that holds [input] for its ticks, as the game moves the player; what a body predicts its own
     * next ticks with. [player] shows the copy as it is after the ticks run so far.
     *
     * @agent claude-opus-5-5
     */
    public interface Steered : PlayerSimulation {

        public var input: SimulatedInput

        public val player: Player

        /**
         * Sneaking or safe walk would have held the player back at a ledge in the last tick.
         */
        public val hasBackedOffEdge: Boolean
    }
}
