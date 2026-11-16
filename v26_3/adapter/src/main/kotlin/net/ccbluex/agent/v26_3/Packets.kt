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

package net.ccbluex.agent.v26_3

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.games.bedwars.LevelSenses
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket

/**
 * A packet the server sent. Block and section updates are the server's word on which blocks stand: [this] takes them
 * as [LevelSenses.blockUpdated] does. Other packets are ignored.
 *
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public fun LevelSenses.received(packet: Packet<*>) {
    when (packet) {
        is ClientboundBlockUpdatePacket -> blockUpdated(packet.pos.toAgent(), packet.blockState.toAgent())
        is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state ->
            blockUpdated(pos.toAgent(), state.toAgent())
        }
    }
}
