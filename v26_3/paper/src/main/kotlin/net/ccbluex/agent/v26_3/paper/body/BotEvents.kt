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
import net.ccbluex.agent.v26_3.paper.Bots
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * The server's events as they concern bot bodies: who sees them, what hurts them, their deaths, and the chunks and
 * worlds they leave with.
 *
 * @agent claude-opus-5-5
 */
internal class BotEvents(private val bots: Bots) : Listener {

    // Fires before the spawn packet; a client spawns a player only with an info entry for it
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTrack(event: PlayerTrackEntityEvent) {
        val bot = (event.entity as CraftEntity).handle as? BotPlayer ?: return
        bot.tabEntries.show((event.player as CraftPlayer).handle)
    }

    // After the anticheats, which still see the hit
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val bot = bots.get(event.entity) ?: return
        if (!bot.settings.damageable) event.isCancelled = true
    }

    // A hit that went through, with its shooter for a projectile
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHurt(event: EntityDamageEvent) {
        val bot = bots.get(event.entity) ?: return
        val damager = (event as? EntityDamageByEntityEvent)?.damager
        val attacker = (damager as? Projectile)?.shooter as? Entity ?: damager
        bot.damaged(attacker?.let { (it as CraftEntity).handle })
    }

    // Outside a minigame a body is no player that loses anything: no drops, no death message
    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val bot = bots.get(event.entity) ?: return
        if (!bot.settings.minigame) {
            event.drops.clear()
            event.droppedExp = 0
            if (event is PlayerDeathEvent) {
                event.deathMessage(null)
                event.keepInventory = true
            }
        }
        val killer = event.entity.killer?.let { (it as CraftEntity).handle }
        bot.agent?.listener?.died(killer)
        bots.bodyDied(bot)
    }

    // A minigame sends players to cages and islands no player keeps loaded
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val bot = bots.get(event.player) ?: return
        bots.hold(bot, event.to)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val bot = bots.get(event.entity) ?: return
        if (!bot.settings.minigame) event.isCancelled = true
    }

    // Outside a minigame a body stays fed, so it can sprint and never starves
    @EventHandler(ignoreCancelled = true)
    fun onHunger(event: FoodLevelChangeEvent) {
        val bot = bots.get(event.entity) ?: return
        if (!bot.settings.minigame && event.foodLevel < event.entity.foodLevel) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        bots.chunkUnloading(event.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        bots.worldUnloading(event.world)
    }
}
