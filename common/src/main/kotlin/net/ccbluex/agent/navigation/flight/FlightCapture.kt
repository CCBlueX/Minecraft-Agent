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

package net.ccbluex.agent.navigation.flight

import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.flight.FlyAbilities

/**
 * How the body flies now: the host's [net.ccbluex.agent.navigation.FlightControl] while it runs, else creative
 * flight, at the speeds [handling] measured; null when [net.ccbluex.agent.navigation.Options.canFly] is off or the
 * body cannot fly.
 *
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.captureFlight(handling: Handling): FlyAbilities? {
    if (!options().canFly) {
        return null
    }

    val control = body.flight
    val base = if (control != null) {
        FlyAbilities(control.control, HOST_SPEED, HOST_VERTICAL_SPEED, isFlying = true)
    } else {
        FlyAbilities.of(body.player.abilities) ?: return null
    }
    return handling.abilities(base)
}

/**
 * Blocks a tick a host's flight is taken to fly level until measured.
 */
private const val HOST_SPEED = 1.0
private const val HOST_VERTICAL_SPEED = 0.5
