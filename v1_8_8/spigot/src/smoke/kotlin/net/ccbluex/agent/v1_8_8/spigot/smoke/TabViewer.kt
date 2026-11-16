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

package net.ccbluex.agent.v1_8_8.spigot.smoke

import com.mojang.authlib.GameProfile
import net.minecraft.server.v1_8_R3.EntityPlayer
import net.minecraft.server.v1_8_R3.EnumProtocolDirection
import net.minecraft.server.v1_8_R3.NetworkManager
import net.minecraft.server.v1_8_R3.Packet
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo
import net.minecraft.server.v1_8_R3.PlayerConnection
import net.minecraft.server.v1_8_R3.PlayerInteractManager
import net.minecraft.server.v1_8_R3.WorldServer
import java.util.UUID

/**
 * A player in a world without a client, standing in for one: it records the tab list entries the server sends it.
 *
 * @agent claude-opus-5-5
 */
internal class TabViewer(private val world: WorldServer, x: Double, y: Double, z: Double) :
    EntityPlayer(world.minecraftServer, world, GameProfile(UUID.randomUUID(), "TabViewer"), PlayerInteractManager(world)) {

    /**
     * The names in the tab list as the packets so far left it.
     */
    val listed = LinkedHashSet<String>()

    /**
     * Every name that was in the tab list at some point.
     */
    val seen = LinkedHashSet<String>()

    init {
        setPosition(x, y, z)
        object : PlayerConnection(world.minecraftServer, NetworkManager(EnumProtocolDirection.CLIENTBOUND), this) {
            override fun sendPacket(packet: Packet<*>?) {
                if (packet is PacketPlayOutPlayerInfo) record(packet)
            }
        }
    }

    // As a joining player: in the world's players, its chunks taken as sent, then shown every tracked entity
    fun join() {
        world.players.add(this)
        world.playerChunkMap.addPlayer(this)
        chunkCoordIntPairQueue.clear()
        world.tracker.a(this)
    }

    fun leave() {
        world.playerChunkMap.removePlayer(this)
        world.players.remove(this)
    }

    private fun record(packet: PacketPlayOutPlayerInfo) {
        val action = ACTION.get(packet) as PacketPlayOutPlayerInfo.EnumPlayerInfoAction
        for (entry in ENTRIES.get(packet) as List<*>) {
            val name = (entry!!.javaClass.methods.first { it.returnType == GameProfile::class.java }.invoke(entry) as GameProfile).name
            when (action) {
                PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER -> {
                    listed += name
                    seen += name
                }
                PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER -> listed -= name
                else -> {}
            }
        }
    }

    private companion object {
        val ACTION = PacketPlayOutPlayerInfo::class.java.getDeclaredField("a").apply { isAccessible = true }
        val ENTRIES = PacketPlayOutPlayerInfo::class.java.getDeclaredField("b").apply { isAccessible = true }
    }
}
