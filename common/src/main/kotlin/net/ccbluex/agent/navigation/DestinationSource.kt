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

/**
 * Where a [Navigator] walks in [Navigator.follow], asked once per tick.
 *
 * @agent claude-opus-5-5
 */
public fun interface DestinationSource {

    /**
     * The goal of this tick, or null to stand still.
     */
    public fun goal(): Vec3?

    /**
     * How close to the goal is close enough, in blocks.
     */
    public fun radius(): Double = DEFAULT_RADIUS

    public companion object {
        public const val DEFAULT_RADIUS: Double = 1.0
    }
}
