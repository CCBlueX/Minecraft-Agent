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

package net.ccbluex.agent.world

import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3

/**
 * The rules a game version moves and fights by, where they differ between versions.
 *
 * @property maxPacketsPerTick how many position packets a server takes in one tick
 * @property isMoveJudgedPerTick whether the k-th position packet of a tick is judged from where the tick started
 * (a limit of 9.5 blocks times the square root of k), or each packet from the one before
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public enum class Mechanics(public val maxPacketsPerTick: Int, public val isMoveJudgedPerTick: Boolean) {
    /**
     * Minecraft 26.3.
     */
    MODERN(5, true),

    /**
     * Minecraft 1.8: no poses or off hand, the 1.8 movement formula in float arithmetic, sword blocking. A server
     * judges every position packet alone, by the squared distance it moves the player.
     */
    V1_8(Int.MAX_VALUE, false);

    /**
     * The order the server moves a player along the axes of [movement]: 26.3 moves y, then the longer of x and z
     * first; 1.8 moves y, x, z.
     */
    public fun axisStepOrder(movement: Vec3): List<Direction.Axis> = when (this) {
        MODERN -> Direction.axisStepOrder(movement)
        V1_8 -> listOf(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z)
    }
}
