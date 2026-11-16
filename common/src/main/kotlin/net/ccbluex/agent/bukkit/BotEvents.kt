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

package net.ccbluex.agent.bukkit

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * The server's events as they concern bot bodies on every platform: what hurts them, their deaths, their gear, and
 * the chunks and worlds they leave with.
 *
 * @agent claude-opus-5-5
 */
internal class BotEvents(private val bots: Bots) : Listener {

    private var unharmed: EntityDamageEvent? = null

    // After the anticheats, which still see the hit; the bot still hears of it
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val bot = bots.get(event.entity) ?: return
        if (bot.settings.damageable) return
        event.isCancelled = true
        unharmed = event
    }

    // A hit that went through, or one an undamageable body ignored, with its shooter for a projectile
    @EventHandler(priority = EventPriority.MONITOR)
    fun onHurt(event: EntityDamageEvent) {
        val ignored = event === unharmed
        unharmed = null
        if (event.isCancelled && !ignored) return
        val bot = bots.get(event.entity) ?: return
        val damager = (event as? EntityDamageByEntityEvent)?.damager
        val attacker = (damager as? Projectile)?.shooter as? Entity ?: damager
        bot.damaged(attacker?.let(bots.platform::toAgent))
    }

    // Unless it plays by the server's rules, gear comes from whoever set the bot up and nothing repairs it
    @EventHandler(ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val bot = bots.get(event.player) ?: return
        if (!bot.settings.isPlayerLike) event.isCancelled = true
    }

    // Unless it plays by the server's rules, a body is no player that loses anything: no drops, no death message
    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val bot = bots.get(event.entity) ?: return
        if (!bot.settings.isPlayerLike) {
            event.drops.clear()
            event.droppedExp = 0
            if (event is PlayerDeathEvent) {
                event.deathMessage = null
                event.keepInventory = true
            }
        }
        bot.agent?.listener?.died(event.entity.killer?.let(bots.platform::toAgent))
        bots.bodyDied(bot)
    }

    // A minigame sends players to cages and islands no player keeps loaded
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val bot = bots.get(event.player) ?: return
        bots.hold(bot, event.to)
    }

    // Paper fires it before its EntityPickupItemEvent and keeps its cancellation
    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: PlayerPickupItemEvent) {
        val bot = bots.get(event.player) ?: return
        if (!bot.settings.isPlayerLike) event.isCancelled = true
    }

    // Unless it plays by the server's rules, a body stays fed, so it can sprint and never starves
    @EventHandler(ignoreCancelled = true)
    fun onHunger(event: FoodLevelChangeEvent) {
        val bot = bots.get(event.entity) ?: return
        val player = event.entity as? Player ?: return
        if (!bot.settings.isPlayerLike && event.foodLevel < player.foodLevel) event.isCancelled = true
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
