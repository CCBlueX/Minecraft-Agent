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

import net.minecraft.world.entity.player.Input
import org.slf4j.Logger

/**
 * What the parts of a [Navigator] share. [body] holds back the keys pressed in a tick, so the navigator adds
 * sprinting before the host gets them.
 *
 * @agent claude-opus-5-5
 */
internal class NavigationContext(
    val body: Body,
    val route: RouteReport,
    val options: () -> Options,
    val listener: Listener,
    val logger: Logger,
)

/**
 * [host] with [press] held back until [takePressed].
 *
 * @agent claude-opus-5-5
 */
internal class TickBody(val host: Body) : Body by host {

    private var pressed: Input? = null

    override fun press(input: Input) {
        pressed = input
    }

    fun takePressed(): Input? = pressed.also { pressed = null }
}
