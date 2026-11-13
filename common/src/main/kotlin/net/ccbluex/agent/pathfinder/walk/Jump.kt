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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.math.horizontalDistanceTo
import net.minecraft.world.phys.Vec3

/**
 * A jump between two feet blocks: sprint from [runUp] towards [aim], jump [takeoff] ticks later, steer in the air.
 *
 * From LiquidBounce: [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L54-L64)
 *
 * @agent claude-opus-5-5
 */
public class Jump(
    /**
     * Where the run starts, at rest.
     */
    public val runUp: Vec3,
    public val takeoff: Int,

    /**
     * False to run off the edge without jumping.
     */
    public val jumps: Boolean,
    public val airTicks: Int,

    /**
     * How far the landing could still slide and stay on its block, or how deep it gets into a ladder caught.
     */
    public val margin: Double,

    /**
     * Jumps in a row on the floor of the run-up before the run, for speed.
     */
    public val hops: Int,
    public val aim: Vec3,
) {

    /**
     * Estimated duration in ticks, the walk from [from] back to [runUp] included.
     */
    public fun ticks(from: Vec3): Double {
        val back = from.horizontalDistanceTo(runUp)
        val backTicks = if (back > RUN_UP_TOLERANCE) back * Costs.WALK + STOP_TICKS else 0.0
        return backTicks + takeoff + airTicks + LANDING_TICKS
    }

    public companion object {
        public const val RUN_UP_TOLERANCE: Double = 0.15
        private const val STOP_TICKS = 4.0
        private const val LANDING_TICKS = 2.0
    }
}
