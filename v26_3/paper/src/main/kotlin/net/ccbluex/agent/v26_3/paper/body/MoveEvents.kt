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

package net.ccbluex.agent.v26_3.paper.body

import org.bukkit.Location
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import kotlin.math.abs

/**
 * Fires [PlayerMoveEvent] for a body's moves as the server does for a client's move packets (with the same
 * thresholds), since a body sends none: minigames kill players falling into the void, hand out regions and watch for
 * AFK players by it. A cancelled move puts the body back, a changed destination teleports it there.
 *
 * @agent claude-opus-5-5
 */
internal class MoveEvents(private val player: BotPlayer) {

    private var last: Location? = null

    fun tick() {
        val bukkit = player.bukkitEntity
        val to = bukkit.location
        // A jump this far is a teleport, which fires its own event
        val from = last?.takeIf { it.world == to.world && it.distanceSquared(to) <= TELEPORT_SQUARED }
        if (from == null) {
            last = to
            return
        }
        val moved = from.distanceSquared(to) > MIN_MOVE_SQUARED
        val turned = abs(from.yaw - to.yaw) + abs(from.pitch - to.pitch) > MIN_TURN
        if (!moved && !turned || player.isImmobile) return
        last = to
        val event = PlayerMoveEvent(bukkit, from, to.clone())
        bukkit.server.pluginManager.callEvent(event)
        when {
            event.isCancelled -> {
                bukkit.teleport(from, PlayerTeleportEvent.TeleportCause.PLUGIN)
                last = from
            }
            event.to != to -> {
                bukkit.teleport(event.to, PlayerTeleportEvent.TeleportCause.PLUGIN)
                last = event.to.clone()
            }
        }
    }

    private companion object {
        // ServerGamePacketListenerImpl's thresholds for firing the event
        const val MIN_MOVE_SQUARED = 1.0 / 256
        const val MIN_TURN = 10f
        const val TELEPORT_SQUARED = 8.0 * 8.0
    }
}
