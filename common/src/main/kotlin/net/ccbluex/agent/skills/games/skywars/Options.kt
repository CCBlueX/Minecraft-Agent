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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.games.FlightSwitch

/**
 * How a [SkyWars] agent may get around.
 *
 * @property flight the host's flight for crossing long void stretches; null bridges everywhere
 * @property flyMaxDistance farthest goal, in blocks, a flight goes for
 * @property flyMaxSeconds how long a flight may take before it heads back
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public data class Options @JvmOverloads constructor(
    val flight: FlightSwitch? = null,
    val flyMaxDistance: Int = 30,
    val flyMaxSeconds: Int = 10,
)
