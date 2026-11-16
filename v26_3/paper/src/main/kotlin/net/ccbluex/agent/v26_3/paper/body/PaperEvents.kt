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

import io.papermc.paper.event.player.PlayerTrackEntityEvent
import net.ccbluex.agent.bukkit.Bots
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier

/**
 * Paper's events as they concern bot bodies beyond what every platform handles: who sees them and their shield blocks.
 *
 * @agent claude-opus-5-5
 */
internal class PaperEvents(private val bots: Bots) : Listener {

    private var blocked: EntityDamageByEntityEvent? = null
    private var blockedModifiers: Map<DamageModifier, Double> = emptyMap()

    // Fires before the spawn packet; a client spawns a player only with an info entry for it. The entry stays,
    // unlisted, as a real player's does, unless the body has none: clients take players without one for server NPCs
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTrack(event: PlayerTrackEntityEvent) {
        val bot = (event.entity as CraftEntity).handle as? BotPlayer ?: return
        bot.trackedBy((event.player as CraftPlayer).handle)
    }

    // Intave drops a shield block unless the blocker's client reported raising it, which a bot has no client for
    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.LOWEST)
    fun onBlockedHit(event: EntityDamageByEntityEvent) {
        if (!event.isApplicable(DamageModifier.BLOCKING) || event.getDamage(DamageModifier.BLOCKING) >= 0) return
        if (!bots.isBot(event.entity)) return
        blocked = event
        blockedModifiers = DamageModifier.entries.filter { event.isApplicable(it) }.associateWith { event.getDamage(it) }
    }

    @Suppress("DEPRECATION")
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockedHitStripped(event: EntityDamageByEntityEvent) {
        if (event !== blocked) return
        val modifiers = blockedModifiers
        blocked = null
        blockedModifiers = emptyMap()
        if (event.getDamage(DamageModifier.BLOCKING) < 0) return
        for ((modifier, damage) in modifiers) event.setDamage(modifier, damage)
    }
}
