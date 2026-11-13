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

package net.ccbluex.agent.pathfinder.glide

/**
 * The rockets [left] to fire on a glide, each pushing [burnTicks] ticks, the last one fired for [boostTicks] more.
 *
 * From LiquidBounce: [Rockets.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Rockets.kt#L23-L47)
 *
 * @agent claude-opus-5-5
 */
public class Rockets(left: Int, private val burnTicks: Int) {

    public var left: Int = left
        private set

    public var boostTicks: Int = 0
        private set

    public val canFire: Boolean
        get() = boostTicks == 0 && left > 0

    public fun fire() {
        left--
        boostTicks = burnTicks
    }

    /**
     * Whether a rocket pushes this tick.
     */
    public fun tick(): Boolean {
        if (boostTicks <= 0) {
            return false
        }

        boostTicks--
        return true
    }

}
