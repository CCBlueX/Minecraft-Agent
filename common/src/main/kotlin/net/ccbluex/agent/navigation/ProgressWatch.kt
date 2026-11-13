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

/**
 * Counts the ticks in a row a mover got no [minProgress] closer to its target than it has been.
 *
 * From LiquidBounce: [ProgressWatch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ProgressWatch.kt#L23-L43)
 *
 * @agent claude-opus-5-5
 */
internal class ProgressWatch(private val minProgress: Double, private val stuckTicks: Int) {

    var idleTicks = 0
        private set

    private var bestDistance = Double.MAX_VALUE

    /**
     * Whether the mover, now [distance] from its target, has gone [stuckTicks] ticks without progress.
     */
    fun stalls(distance: Double): Boolean {
        if (distance < bestDistance - minProgress) {
            bestDistance = distance
            idleTicks = 0
            return false
        }
        return ++idleTicks >= stuckTicks
    }

    /**
     * Forgives the ticks without progress so far, for a wait on the way.
     */
    fun excuse() {
        idleTicks = 0
    }

    fun reset() {
        bestDistance = Double.MAX_VALUE
        idleTicks = 0
    }

}
