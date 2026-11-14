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

package net.ccbluex.agent.v26_3.paper

import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import net.ccbluex.agent.bukkit.BotSkin
import net.ccbluex.agent.v26_3.paper.body.BlockFeed
import net.ccbluex.agent.v26_3.paper.body.BotEvents
import net.minecraft.core.BlockPos
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory

/**
 * Every bot of the server: spawns their bodies, ticks their navigation before the entities move, and brings bodies
 * back at home after they died or their chunk unloaded. All calls on the server thread.
 *
 * @agent claude-opus-5-5
 */
public class Bots internal constructor(private val plugin: Plugin) {

    private val bots = LinkedHashMap<String, Bot>()
    private val bodies = HashMap<UUID, Bot>()
    private val respawnAt = HashMap<Bot, Long>()
    private val goneSince = HashMap<Bot, Long>()
    private val listeners = CopyOnWriteArrayList<BotListener>()
    private var task: BukkitTask? = null
    private var tick = 0L
    private val chunkHolds = ChunkHolds(plugin)

    public val all: List<Bot>
        get() = bots.values.toList()

    /**
     * A bot by name in any case.
     */
    public fun get(name: String): Bot? = bots[key(name)]

    /**
     * The bot whose body [entity] is.
     */
    public fun get(entity: Entity): Bot? = bodies[entity.uniqueId]

    public fun isBot(entity: Entity): Boolean = bodies.containsKey(entity.uniqueId)

    /**
     * A new bot with its body spawned at [location], which becomes its home. Names are unique in any case and at most
     * 16 characters long.
     */
    @JvmOverloads
    public fun spawn(location: Location, name: String, skin: BotSkin? = null, settings: BotSettings = BotSettings()): Bot {
        val key = key(name)
        require(key !in bots) { "A bot named $name exists" }
        val bot = Bot(name, location, settings, skin, this)
        bots[key] = bot
        try {
            spawnBody(bot)
        } catch (e: RuntimeException) {
            bots.remove(key)
            throw e
        }
        return bot
    }

    /**
     * Takes the bot's body away and forgets the bot.
     */
    public fun remove(bot: Bot) {
        despawn(bot)
        respawnAt.remove(bot)
        if (bots[key(bot.name)] !== bot) return
        bots.remove(key(bot.name))
        notify(bot, "removed") { it.removed(bot) }
    }

    /**
     * Takes the body away and spawns a new one at home; once the home chunk is loaded if it is not.
     */
    public fun respawn(bot: Bot) {
        check(bots[key(bot.name)] === bot) { "Bot ${bot.name} was removed" }
        despawn(bot)
        respawnAt.remove(bot)
        bot.isDead = false
        if (isLoaded(bot.home)) spawnBody(bot)
    }

    public fun addListener(listener: BotListener) {
        listeners.addIfAbsent(listener)
    }

    public fun removeListener(listener: BotListener) {
        listeners.remove(listener)
    }

    internal fun start() {
        val pluginManager = plugin.server.pluginManager
        pluginManager.registerEvents(BotEvents(this), plugin)
        pluginManager.registerEvents(BlockFeed(this), plugin)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    internal fun stop() {
        task?.cancel()
        task = null
        for (bot in all) despawn(bot)
    }

    private fun spawnBody(bot: Bot) {
        despawn(bot)
        val body = PaperBody.spawn(plugin, bot.home, bot.name, bot.skin)
        val entity = body.entity
        try {
            entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = max(1.0, bot.settings.health)
            entity.health = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
            entity.removeWhenFarAway = false
            val equipment = entity.equipment
            bot.equipment.forEach { (slot, item) -> equipment.setItem(slot, item.clone()) }
        } catch (e: RuntimeException) {
            body.remove()
            throw e
        }
        bodies[entity.uniqueId] = bot
        bot.attach(body)
        // Before the end of the tick, which would unload a chunk no player holds
        hold(bot, entity.location)
        bot.isDead = false
        notify(bot, "spawned") { it.spawned(bot) }
    }

    private fun despawn(bot: Bot) {
        goneSince.remove(bot)
        chunkHolds.release(bot)
        val entity = bot.player ?: return
        notify(bot, "despawned") { it.despawned(bot) }
        bodies.remove(entity.uniqueId)
        bot.detach()?.remove()
    }

    internal fun bodyDied(bot: Bot) {
        notify(bot, "died") { it.died(bot) }
        if (!bot.settings.respawns) bot.isDead = true
    }

    /**
     * A minigame plugin took the bot out of its round while its body lives on, as a spectator: told as the death it is,
     * with [killer] when the plugin knows one.
     *
     * @agent claude-sonnet-5-5
     */
    internal fun eliminated(bot: Bot, killer: net.minecraft.world.entity.Entity?) {
        bot.agent?.listener?.died(killer)
        notify(bot, "died") { it.died(bot) }
    }

    /**
     * Bodies standing in an unloading chunk go; they come back at home once the home chunk is loaded.
     */
    internal fun chunkUnloading(chunk: Chunk) {
        for (bot in all) {
            val at = bot.player?.location ?: continue
            if (at.world == chunk.world && at.blockX shr CHUNK_SHIFT == chunk.x && at.blockZ shr CHUNK_SHIFT == chunk.z) {
                despawn(bot)
            }
        }
    }

    /**
     * A minigame body keeps the chunks around [location] loaded, from its spawn and each teleport on.
     */
    internal fun hold(bot: Bot, location: Location) {
        if (!bot.settings.minigame) return
        chunkHolds.hold(bot, location.world, location.blockX shr CHUNK_SHIFT, location.blockZ shr CHUNK_SHIFT)
    }

    internal fun worldUnloading(world: World) {
        for (bot in all) {
            if (bot.player?.world == world) despawn(bot)
        }
    }

    internal fun blockChanged(world: World, pos: BlockPos) {
        for (bot in bodies.values) {
            val entity = bot.player ?: continue
            val navigator = bot.navigator ?: continue
            if (entity.world != world) continue
            val range = bot.settings.navigation.pathRange * 2.0
            val location = entity.location
            val dx = location.x - pos.x
            val dz = location.z - pos.z
            if (dx * dx + dz * dz <= range * range) navigator.blockChanged(pos)
        }
    }

    private fun tick() {
        tick++
        for (bot in all) {
            val body = bot.body
            if (body != null) {
                if (body.entity.isValid) {
                    if (bot.settings.minigame) hold(bot, body.entity.location)
                    try {
                        bot.tick()
                    } catch (e: RuntimeException) {
                        LOGGER.error("Bot {} failed to tick", bot.name, e)
                    }
                    continue
                }
                val since = goneSince.getOrPut(bot) { tick }
                if (tick - since < DEATH_TICKS) continue
                despawn(bot)
                respawnAt[bot] = since + bot.settings.respawnDelay
            }
            if (bot.isDead || bot.settings.minigame || bots[key(bot.name)] !== bot) continue
            val at = respawnAt[bot]
            if (at != null && at > tick) continue
            if (!isLoaded(bot.home)) continue
            respawnAt.remove(bot)
            try {
                spawnBody(bot)
            } catch (e: RuntimeException) {
                LOGGER.error("Bot {} failed to spawn", bot.name, e)
                respawnAt[bot] = tick + SPAWN_RETRY_TICKS
            }
        }
    }

    private inline fun notify(bot: Bot, what: String, call: (BotListener) -> Unit) {
        for (listener in listeners) {
            // Java listeners throw checked exceptions too, which must not stop a spawn or removal half way
            try {
                call(listener)
            } catch (e: Exception) {
                LOGGER.error("A listener failed on bot {} {}", bot.name, what, e)
            }
        }
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Agent/Bots")
        const val CHUNK_SHIFT = 4
        // How long a dead body lies before it goes, as a player's death animation
        const val DEATH_TICKS = 20L
        // A body that failed to spawn is tried again this much later, not every tick
        const val SPAWN_RETRY_TICKS = 200L

        fun key(name: String): String = name.lowercase(Locale.ROOT)

        fun isLoaded(location: Location): Boolean =
            location.isWorldLoaded && location.world.isChunkLoaded(location.blockX shr CHUNK_SHIFT, location.blockZ shr CHUNK_SHIFT)
    }
}
