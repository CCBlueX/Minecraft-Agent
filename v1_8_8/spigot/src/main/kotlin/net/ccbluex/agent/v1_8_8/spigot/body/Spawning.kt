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

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.ccbluex.agent.bukkit.BotSkin
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Adds a 1.8 bot player named [name] at [location], with the `NPC` metadata plugins check for NPCs, takes it out of
 * the world's player list again and tracks it with [Tracking].
 *
 * @agent claude-opus-5-5
 */
internal fun spawnBotPlayer(plugin: Plugin, location: Location, name: String, skin: BotSkin?): BotPlayer {
    require(name.length <= MAX_NAME_LENGTH) { "Bot name longer than $MAX_NAME_LENGTH characters: $name" }
    val world = (requireNotNull(location.world) { "Bot location without a world" } as CraftWorld).handle
    // 1.8 loads a chunk an entity is added to on the spot, outside the chunk loading a player's view does
    require(world.chunkProviderServer.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
        "Bot location in an unloaded chunk: $location"
    }
    val profile = GameProfile(npcId(), name)
    if (skin != null) profile.properties.put("textures", Property("textures", skin.value, skin.signature))
    val player = BotPlayer(world, profile, plugin)
    player.setPositionRotation(location.x, location.y, location.z, location.yaw, location.pitch)
    player.look(location.yaw, location.pitch)
    val bukkit = player.bukkitEntity
    bukkit.setMetadata(BotPlayer.NPC_METADATA, FixedMetadataValue(plugin, true))
    bukkit.isSleepingIgnored = true
    check(world.addEntity(player, CreatureSpawnEvent.SpawnReason.CUSTOM)) { "Bot body $name was not added to the world" }
    world.players.remove(player)
    Tracking.install(world, player)
    BotPlayer.spawned += player
    return player
}

// Longer names do not fit a game profile
private const val MAX_NAME_LENGTH = 16

// A version 2 UUID, which Citizens gives its NPCs and plugins take for one; real players have version 4 or 3
private fun npcId(): UUID {
    val random = UUID.randomUUID()
    return UUID(random.mostSignificantBits and 0xF000L.inv() or 0x2000L, random.leastSignificantBits)
}
