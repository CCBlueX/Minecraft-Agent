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

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.ccbluex.agent.bukkit.BotSkin
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import java.util.UUID

// Longer names do not fit a game profile
private const val MAX_NAME_LENGTH = 16

// Paper deprecates metadata, but the NPC metadata is how plugins tell NPCs from players
/**
 * Adds a bot player named [name] at [location], with the `NPC` metadata plugins check for NPCs, and takes it out of
 * the level's player list again.
 *
 * @agent claude-opus-5-5
 */
@Suppress("DEPRECATION")
internal fun spawnBotPlayer(plugin: Plugin, location: Location, name: String, skin: BotSkin?): BotPlayer {
    require(name.length <= MAX_NAME_LENGTH) { "Bot name longer than $MAX_NAME_LENGTH characters: $name" }
    val level = (requireNotNull(location.world) { "Bot location without a world" } as CraftWorld).handle
    val properties: ImmutableMultimap<String, Property> = if (skin == null) {
        ImmutableMultimap.of()
    } else {
        ImmutableMultimap.of("textures", Property("textures", skin.value, skin.signature))
    }
    val player = BotPlayer(level, GameProfile(npcId(), name, PropertyMap(properties)))
    player.snapTo(location.x, location.y, location.z, location.yaw, location.pitch)
    player.setYHeadRot(location.yaw)
    player.setYBodyRot(location.yaw)
    val bukkit = player.bukkitEntity
    bukkit.setMetadata(BotPlayer.NPC_METADATA, FixedMetadataValue(plugin, true))
    bukkit.isSleepingIgnored = true
    bukkit.setAffectsSpawning(false)
    val added = withoutViewDistance(level) {
        level.addFreshEntity(player, CreatureSpawnEvent.SpawnReason.CUSTOM)
    }
    check(added) { "Bot body $name was not added to the world" }
    player.leavePlayerList()
    BotPlayer.spawned += player
    return player
}

// A version 2 UUID, which Citizens gives its NPCs and plugins take for one; real players have version 4 or 3
private fun npcId(): UUID {
    val random = UUID.randomUUID()
    return UUID(random.mostSignificantBits and 0xF000L.inv() or 0x2000L, random.leastSignificantBits)
}
