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

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.server.level.ServerPlayer

/**
 * The player info entries a bot hands to the players that start tracking it. A client spawns a player only once it
 * has an entry for it, and takes the skin from that entry. The entry is unlisted, which clients before 1.19.3 do not
 * know, so each viewer gets it withdrawn again once the skin had time to load.
 *
 * @agent claude-opus-5-5
 */
internal class TabEntries(private val bot: BotPlayer) {

    private class Withdrawal(val viewer: ServerPlayer, val atTick: Int)

    // All entries stay equally long, so the oldest is always due first
    private val withdrawals = ArrayDeque<Withdrawal>()

    fun show(viewer: ServerPlayer) {
        viewer.connection.send(bot.infoPacket())
        withdrawals.addLast(Withdrawal(viewer, bot.tickCount + SKIN_LOAD_TICKS))
    }

    fun tick() {
        if (withdrawals.isEmpty() || withdrawals.first().atTick > bot.tickCount) return
        val packet = ClientboundPlayerInfoRemovePacket(listOf(bot.getUUID()))
        while (withdrawals.isNotEmpty() && withdrawals.first().atTick <= bot.tickCount) {
            val viewer = withdrawals.removeFirst().viewer
            if (!viewer.hasDisconnected()) viewer.connection.send(packet)
        }
    }

    fun forget() {
        withdrawals.clear()
    }

    private companion object {
        const val SKIN_LOAD_TICKS = 60
    }
}
