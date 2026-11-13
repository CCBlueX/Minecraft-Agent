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

package net.ccbluex.agent.navigation.teleport

/**
 * How teleport routes are cut into position packets.
 *
 * @property stepLength blocks per packet, 1 to 9.5
 * @property pacing how many packets go in a tick
 * @property shouldClaimGroundOnDescent whether a step that descends is sent as on ground, which keeps the server from
 * adding it to the fall distance
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public data class Options @JvmOverloads constructor(
    public val stepLength: Double = DEFAULT_STEP_LENGTH,
    public val pacing: Pacing = Pacing.Batches(),
    public val shouldClaimGroundOnDescent: Boolean = false,
) {

    public companion object {
        public const val DEFAULT_STEP_LENGTH: Double = 5.0
    }

}

/**
 * Whoever teleports through [Teleports]; once it stops running, its excursion is undone.
 *
 * @agent claude-opus-5-5
 */
public fun interface Owner {
    public fun isRunning(): Boolean
}
