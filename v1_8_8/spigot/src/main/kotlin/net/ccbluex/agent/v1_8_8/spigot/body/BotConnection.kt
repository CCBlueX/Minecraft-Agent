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

package net.ccbluex.agent.v1_8_8.spigot.body

import io.netty.channel.AbstractChannel
import io.netty.channel.ChannelConfig
import io.netty.channel.ChannelMetadata
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.DefaultChannelConfig
import io.netty.channel.EventLoop
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity
import net.minecraft.server.v1_8_R3.EnumProtocolDirection
import net.minecraft.server.v1_8_R3.IChatBaseComponent
import net.minecraft.server.v1_8_R3.MinecraftServer
import net.minecraft.server.v1_8_R3.NetworkManager
import net.minecraft.server.v1_8_R3.Packet
import net.minecraft.server.v1_8_R3.PlayerConnection
import java.net.SocketAddress

/**
 * The connection of a [BotPlayer]: no client behind it, every packet dropped, as Citizens connects its NPCs, but the
 * velocity a client would take for its own player.
 *
 * @agent claude-opus-5-5
 */
internal object BotConnection {

    // EntityHuman.attack takes knockback back from a player it hit, whose client moves it by this packet instead
    private val VELOCITY_FIELDS = listOf("a", "b", "c", "d").map {
        PacketPlayOutEntityVelocity::class.java.getDeclaredField(it).apply { isAccessible = true }
    }
    private const val VELOCITY_SCALE = 8000.0

    // PlayerConnection sets itself as the player's connection
    fun connect(server: MinecraftServer, player: BotPlayer) {
        EmptyConnection(server, EmptyNetworkManager(), player)
    }

    private class EmptyNetworkManager : NetworkManager(EnumProtocolDirection.CLIENTBOUND) {
        init {
            channel = EmptyChannel()
            l = object : SocketAddress() {}
        }

        override fun g(): Boolean = true
    }

    private class EmptyConnection(server: MinecraftServer, network: NetworkManager, private val bot: BotPlayer) :
        PlayerConnection(server, network, bot) {

        override fun sendPacket(packet: Packet<*>?) {
            if (packet !is PacketPlayOutEntityVelocity) return
            val (id, x, y, z) = VELOCITY_FIELDS.map { it.getInt(packet) }
            if (id == bot.id) bot.knocked(x / VELOCITY_SCALE, y / VELOCITY_SCALE, z / VELOCITY_SCALE)
        }

        // A kick goes through the player list, which would save the player
        override fun disconnect(reason: String?) {}

        override fun a(reason: IChatBaseComponent?) {}
    }

    private class EmptyChannel : AbstractChannel(null) {
        private val config = DefaultChannelConfig(this)

        override fun config(): ChannelConfig = config.also { it.isAutoRead = true }
        override fun doBeginRead() {}
        override fun doBind(address: SocketAddress?) {}
        override fun doClose() {}
        override fun doDisconnect() {}
        override fun doWrite(buffer: ChannelOutboundBuffer?) {}
        override fun isActive(): Boolean = false
        override fun isCompatible(loop: EventLoop?): Boolean = false
        override fun isOpen(): Boolean = false
        override fun localAddress0(): SocketAddress? = null
        override fun metadata(): ChannelMetadata = ChannelMetadata(true)
        override fun newUnsafe(): AbstractUnsafe? = null
        override fun remoteAddress0(): SocketAddress? = null
    }
}
