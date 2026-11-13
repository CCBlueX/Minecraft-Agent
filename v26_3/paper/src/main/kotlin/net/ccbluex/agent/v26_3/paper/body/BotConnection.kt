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

import io.netty.channel.AbstractChannel
import io.netty.channel.ChannelConfig
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelMetadata
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.DefaultChannelConfig
import io.netty.channel.EventLoop
import java.net.SocketAddress
import net.ccbluex.agent.bukkit.body.Reflect
import net.minecraft.network.Connection
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl

/**
 * The connection of a [BotPlayer]: no client behind it and every packet dropped, as Citizens connects its NPCs.
 *
 * @agent claude-opus-5-5
 */
internal class BotConnection private constructor() : Connection(PacketFlow.CLIENTBOUND) {

    init {
        channel = BotChannel()
        address = object : SocketAddress() {}
    }

    override fun flushChannel() {}

    override fun isConnected(): Boolean = true

    override fun send(packet: Packet<*>) {}

    override fun send(packet: Packet<*>, listener: ChannelFutureListener?) {}

    override fun send(packet: Packet<*>, listener: ChannelFutureListener?, flush: Boolean) {}

    companion object {
        private val PACKET_LISTENER = Reflect.field(Connection::class.java, "packetListener")

        fun connect(server: MinecraftServer, player: ServerPlayer): ServerGamePacketListenerImpl {
            val connection = BotConnection()
            val listener = BotPacketListener(server, connection, player)
            PACKET_LISTENER.set(connection, listener)
            return listener
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
private class BotPacketListener(server: MinecraftServer, connection: Connection, player: ServerPlayer) :
    ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(player.gameProfile, false)) {

    override fun tick() {}

    override fun resumeFlushing() {}

    override fun send(packet: Packet<*>) {}

    override fun send(packet: Packet<*>, listener: ChannelFutureListener?) {}

    // Otherwise the player stays immobile and invulnerable until the timeout, then fires PlayerClientLoadedWorldEvent
    override fun hasClientLoaded(): Boolean = true

    override fun tickClientLoadTimeout() {}

    // A kick goes through the player list, which would save the player
    override fun disconnect(details: DisconnectionDetails) {}

    override fun onDisconnect(details: DisconnectionDetails) {}
}

/**
 * @agent claude-opus-5-5
 */
private class BotChannel : AbstractChannel(null) {

    private val config = DefaultChannelConfig(this)

    override fun config(): ChannelConfig {
        config.setAutoRead(true)
        return config
    }

    override fun doBeginRead() {}

    override fun doBind(localAddress: SocketAddress) {}

    override fun doClose() {}

    override fun doDisconnect() {}

    override fun doWrite(buffer: ChannelOutboundBuffer) {}

    override fun isActive(): Boolean = false

    override fun isCompatible(loop: EventLoop): Boolean = false

    override fun isOpen(): Boolean = false

    override fun localAddress0(): SocketAddress? = null

    override fun metadata(): ChannelMetadata = ChannelMetadata(true)

    override fun newUnsafe(): AbstractChannel.AbstractUnsafe? = null

    override fun remoteAddress0(): SocketAddress? = null
}
