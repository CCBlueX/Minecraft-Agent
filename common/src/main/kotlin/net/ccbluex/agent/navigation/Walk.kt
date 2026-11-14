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

import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

/**
 * A walk the [Navigator] holds: where it goes, whether it ends on arrival, who hears how it ended, and for how
 * many ticks in a row no way closer was known.
 *
 * @agent claude-opus-5-5
 */
internal class Walk(
    val source: DestinationSource,
    private val endsOnArrival: Boolean,
    private val ended: Consumer<Ending>?,
) {

    var strandedTicks: Int = 0
        private set

    /**
     * How the walk ends after a tick it [arrived] or was [stranded] in, null while it goes on.
     */
    fun tick(arrived: Boolean, stranded: Boolean): Ending? {
        strandedTicks = if (stranded) strandedTicks + 1 else 0
        return when {
            !endsOnArrival -> null
            arrived -> Ending.ARRIVED
            strandedTicks >= Navigator.UNREACHABLE_TICKS -> Ending.UNREACHABLE
            else -> null
        }
    }

    fun end(ending: Ending) {
        ended?.accept(ending)
    }

    /**
     * @agent claude-opus-5-5
     */
    class Fixed(private val goal: Vec3, private val radius: Double) : DestinationSource {

        override fun goal(): Vec3 = goal

        override fun radius(): Double = radius
    }
}
