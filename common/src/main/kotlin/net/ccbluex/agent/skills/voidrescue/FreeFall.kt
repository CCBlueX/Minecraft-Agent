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

package net.ccbluex.agent.skills.voidrescue

/**
 * Tells from the feet height of each tick whether the body falls freely: it sinks faster every tick, as gravity
 * pulls it, for [TICKS] ticks in a row, or already sinks as fast as no flight does. A host flight that holds the
 * player rises, hovers or sinks at a steady speed and never counts.
 *
 * @agent claude-opus-5-5
 */
internal class FreeFall {

    private var lastY = Double.NaN
    private var lastDrop = 0.0
    private var speedingUp = 0

    var isFalling: Boolean = false
        private set

    /**
     * The feet height of this tick.
     */
    fun tick(y: Double) {
        val drop = if (lastY.isNaN()) 0.0 else lastY - y
        lastY = y
        speedingUp = if (drop > MIN_DROP && drop > lastDrop + MIN_SPEEDUP) speedingUp + 1 else 0
        lastDrop = drop
        isFalling = speedingUp >= TICKS || drop > FAST_DROP
    }

    fun reset() {
        lastY = Double.NaN
        lastDrop = 0.0
        speedingUp = 0
        isFalling = false
    }

    private companion object {
        const val TICKS = 3

        /**
         * Blocks a tick: gravity adds about 0.08 a tick while the fall is slow, drag takes less of it early on.
         */
        const val MIN_DROP = 0.05
        const val MIN_SPEEDUP = 0.03

        /**
         * Faster than any fly mode sinks on purpose.
         */
        const val FAST_DROP = 1.5
    }
}
