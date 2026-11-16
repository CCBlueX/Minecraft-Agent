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

import net.minecraft.server.v1_8_R3.EntityHuman
import net.minecraft.server.v1_8_R3.EntityPlayer
import net.minecraft.server.v1_8_R3.EntityTracker
import net.minecraft.server.v1_8_R3.EntityTrackerEntry
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo.EnumPlayerInfoAction
import net.minecraft.server.v1_8_R3.WorldServer

/**
 * Tracks a [BotPlayer] as the vanilla entry tracks a player, with a player info entry sent ahead of the spawn,
 * which a real player gets from the player list on join. Replaces the vanilla entry, as Citizens'
 * PlayerlistTrackerEntry does. The entry stays in the viewer's tab list while the body lives, as a real player's,
 * or only until the skin loaded when the body has no [BotPlayer.tabEntry].
 * Uses what EntityTracker uses for players: an update every second tick, no velocity updates.
 *
 * @agent claude-opus-5-5
 */
internal class Tracking private constructor(private val bot: BotPlayer, range: Int) :
    EntityTrackerEntry(bot, range, 2, false) {

    private val addInfo = PacketPlayOutPlayerInfo(EnumPlayerInfoAction.ADD_PLAYER, bot)
    private val removeInfo = PacketPlayOutPlayerInfo(EnumPlayerInfoAction.REMOVE_PLAYER, bot)
    private val tabRemovals = HashMap<EntityPlayer, Int>()
    private val listed = HashSet<EntityPlayer>()

    override fun updatePlayer(viewer: EntityPlayer) {
        if (viewer !== bot && viewer !in trackedPlayers && c(viewer) &&
            (viewer.u().playerChunkMap.a(viewer, bot.ae, bot.ag) || bot.attachedToPlayer) &&
            viewer.bukkitEntity.canSee(bot.bukkitEntity)
        ) {
            viewer.playerConnection.sendPacket(addInfo)
            super.updatePlayer(viewer)
            listed += viewer
            if (!bot.tabEntry) tabRemovals[viewer] = m + TAB_REMOVAL_DELAY
            return
        }
        super.updatePlayer(viewer)
    }

    override fun track(players: List<EntityHuman>) {
        super.track(players)
        tabRemovals.entries.removeIf { (viewer, at) ->
            if (at > m) return@removeIf false
            viewer.playerConnection.sendPacket(removeInfo)
            listed -= viewer
            true
        }
    }

    // Called once the entity is untracked: track() stops, so the info entries go now
    override fun a() {
        super.a()
        for (viewer in listed) viewer.playerConnection.sendPacket(removeInfo)
        listed.clear()
        tabRemovals.clear()
    }

    companion object {
        // A 1.8 client drops a player spawn without an info entry and loads the skin from that entry once it
        // renders the player, so the entry leaves the tab list only after this many ticks
        private const val TAB_REMOVAL_DELAY = 40

        private val TRACKER_ENTRIES = EntityTracker::class.java.getDeclaredField("c").apply { isAccessible = true }

        // The world tracked the body when it was added, without showing it to anyone (BotPlayer.a)
        fun install(world: WorldServer, bot: BotPlayer) {
            val tracker = world.tracker
            val vanilla = tracker.trackedEntities.d(bot.id)
                ?: throw IllegalStateException("Bot body ${bot.name} is not tracked")
            @Suppress("UNCHECKED_CAST")
            val entries = TRACKER_ENTRIES.get(tracker) as MutableSet<EntityTrackerEntry>
            entries.remove(vanilla)
            vanilla.a()
            val entry = Tracking(bot, vanilla.b)
            entries.add(entry)
            tracker.trackedEntities.a(bot.id, entry)
            bot.tracked = true
            entry.scanPlayers(world.players)
        }
    }
}
