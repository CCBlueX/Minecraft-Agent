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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.bukkit.Bot
import net.ccbluex.agent.bukkit.BotSettings
import net.ccbluex.agent.bukkit.MinecraftAgent
import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.world.InteractionHand
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/**
 * Builds a course next to the spawn of a flat 1.8.8 world, runs bots through it and logs one `SMOKE PASS` or
 * `SMOKE FAIL` line per check, then stops the server.
 *
 * @agent claude-opus-5-5
 */
internal class SmokePlugin : JavaPlugin(), Listener {

    private val hits = ArrayList<Pair<String, Double>>()
    private var pearlTeleports = 0
    private var failures = 0

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        server.scheduler.runTaskLater(this, Runnable {
            suspend { run() }.startCoroutine(Continuation(EmptyCoroutineContext) { result ->
                result.exceptionOrNull()?.let { logger.log(java.util.logging.Level.SEVERE, "SMOKE crashed", it) }
                logger.info("SMOKE done, $failures failed")
                server.shutdown()
            })
        }, START_DELAY)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHit(event: EntityDamageByEntityEvent) {
        hits += event.entity.name to event.finalDamage
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event.cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) pearlTeleports++
    }

    private fun report(check: String, passed: Boolean, detail: String) {
        if (!passed) failures++
        logger.info("SMOKE ${if (passed) "PASS" else "FAIL"} $check: $detail")
    }

    private suspend fun ticks(count: Long) = suspendCoroutine { continuation ->
        server.scheduler.runTaskLater(this, Runnable { continuation.resume(Unit) }, count)
    }

    /**
     * Waits up to [timeout] ticks for [done]; the ticks it took, or -1.
     */
    private suspend fun until(timeout: Int, done: () -> Boolean): Int {
        for (tick in 0..timeout) {
            if (done()) return tick
            ticks(1)
        }
        return -1
    }

    private suspend fun run() {
        val world = server.worlds[0]
        val x0 = world.spawnLocation.blockX
        val z0 = world.spawnLocation.blockZ
        val ground = world.getHighestBlockYAt(x0, z0)
        logger.info("SMOKE course at $x0 $ground $z0")
        // The smoke jar is relocated like the plugin jar, so this is the library's SLF4J
        val loggers = LoggerFactory.getILoggerFactory()
        LoggerFactory.getLogger(MinecraftAgent::class.java).info("SMOKE library log through {}", loggers.javaClass.simpleName)
        report("library logs", loggers !is NOPLoggerFactory, loggers.javaClass.name)
        buildCourse(world, x0, ground, z0)
        val bots = MinecraftAgent.bots()
        val walker = bots.spawn(Location(world, x0 + 0.5, ground.toDouble(), z0 + 0.5), "Walker")
        ticks(WARM_UP)
        report("hidden", walker.player !in server.onlinePlayers && walker.player !in world.players, "not online, not in the world's players")

        walk(walker, "walks a slab and a gap", Location(world, x0 + 14.5, ground.toDouble(), z0 + 0.5)) { false }
        var swam = false
        walk(walker, "swims", Location(world, x0 + 27.5, ground.toDouble(), z0 + 0.5)) {
            swam = swam || checkNotNull(walker.body).player.isInWater
            swam
        }
        report("swims: was in water", swam, "in water on the way: $swam")
        var climbed = false
        walk(walker, "climbs a ladder", Location(world, x0 + 32.5, ground + 4.0, z0 + 0.5)) {
            climbed = climbed || checkNotNull(walker.body).player.onClimbable()
            climbed
        }
        report("climbs a ladder: on the ladder", climbed, "on a ladder on the way: $climbed")
        walker.remove()

        fight(world, x0 - 20, ground, z0)
        tab(world, x0 - 60, ground, z0)
    }

    private suspend fun walk(bot: Bot, check: String, goal: Location, sample: () -> Boolean) {
        bot.moveTo(goal, 1.0)
        // Standing within the radius, not hanging on a ladder or swimming next to the goal
        val taken = until(WALK_TIMEOUT) {
            sample()
            val body = checkNotNull(bot.body)
            body.entity.location.distance(goal) <= 1.0 && body.isOnGround
        }
        val at = checkNotNull(bot.body).entity.location
        report(check, taken >= 0, "ended at %.2f %.2f %.2f, %.2f from the goal, %d ticks".format(at.x, at.y, at.z, at.distance(goal), taken))
        bot.stop()
    }

    private suspend fun fight(world: World, x: Int, ground: Int, z: Int) {
        val bots = MinecraftAgent.bots()
        val dummy = bots.spawn(Location(world, x + 0.5, ground.toDouble(), z + 0.5), "Dummy", null, BotSettings(immortal = true))
        // A kiter, the style that holds back for a rod or bow, carrying neither: it must still close in
        val kiter = BotSettings(personality = Personality.BALANCED.copy(fighting = FightingStyle.KITER))
        val fighter = bots.spawn(Location(world, x - 1.5, ground.toDouble(), z + 0.5, -90f, 0f), "Fighter", null, kiter)
        ticks(WARM_UP)
        val fighterBody = checkNotNull(fighter.body)
        val dummyBody = checkNotNull(dummy.body)
        fighterBody.entity.itemInHand = ItemStack(Material.DIAMOND_SWORD)
        // The held item's attack damage applies with the next living tick
        ticks(2)
        val before = dummyBody.entity.location
        hits.clear()
        val hurt = fighterBody.attack(dummyBody.entity)
        ticks(KNOCKBACK_TICKS)
        val pushed = dummyBody.entity.location.distance(before)
        report("knockback", hurt && pushed > MIN_KNOCKBACK, "hurt=$hurt, pushed %.2f blocks, damage ${hits.firstOrNull()?.second}".format(pushed))

        dummyBody.moveTo(x + 0.5, ground.toDouble(), z + 0.5, 90f, 0f)
        ticks(INVULNERABLE_TICKS)
        hits.clear()
        fighterBody.attack(dummyBody.entity)
        val open = hits.firstOrNull()?.second ?: 0.0
        dummyBody.entity.itemInHand = ItemStack(Material.DIAMOND_SWORD)
        val raised = dummyBody.startUsingItem(InteractionHand.MAIN_HAND)
        ticks(INVULNERABLE_TICKS)
        hits.clear()
        val blocking = dummyBody.isBlocking
        fighterBody.attack(dummyBody.entity)
        val blocked = hits.firstOrNull()?.second ?: 0.0
        dummyBody.stopUsingItem()
        report(
            "sword blocking", raised && blocking && blocked > 0 && blocked == (1 + open) / 2,
            "raised=$raised blocking=$blocking open hit $open, blocked hit $blocked",
        )

        fighterBody.entity.foodLevel = HUNGRY
        fighterBody.entity.itemInHand = ItemStack(Material.COOKED_BEEF)
        val eating = fighterBody.startUsingItem(InteractionHand.MAIN_HAND)
        val ate = until(EAT_TIMEOUT) { fighterBody.entity.foodLevel > HUNGRY }
        report("eats", eating && ate >= 0, "started=$eating, food ${fighterBody.entity.foodLevel} after $ate ticks")

        // Away from the dummy, which stands east of the fighter
        fighterBody.moveTo(x - 1.5, ground.toDouble(), z + 0.5, 90f, -30f)
        ticks(1)
        val from = fighterBody.entity.location
        pearlTeleports = 0
        val pearl = fighterBody.launch(ItemStack(Material.ENDER_PEARL))
        val landedAfter = until(PEARL_TIMEOUT) { pearlTeleports > 0 }
        val moved = fighterBody.entity.location.distance(from)
        report("throws a pearl", pearl != null && landedAfter >= 0 && moved > MIN_PEARL, "thrown=${pearl != null}, teleported after $landedAfter ticks, %.2f blocks".format(moved))

        fighterBody.moveTo(x - 1.5, ground.toDouble(), z + 0.5, 180f, 45f)
        ticks(1)
        val against = world.getBlockAt(x - 2, ground - 1, z - 2)
        val placed = fighterBody.placeBlock(against, BlockFace.UP, ItemStack(Material.COBBLESTONE))
        val block = against.getRelative(BlockFace.UP)
        val cobblestone = block.type == Material.COBBLESTONE
        report("places a block", placed && cobblestone, "placed=$placed, ${block.type} at ${block.x} ${block.y} ${block.z}")
        fighterBody.entity.itemInHand = ItemStack(Material.STONE_PICKAXE)
        ticks(1)
        val pos = BlockPos(block.x, block.y, block.z)
        fighterBody.startDestroy(pos, Direction.UP)
        val broken = until(DIG_TIMEOUT) {
            fighterBody.continueDestroy(pos, Direction.UP)
            block.type == Material.AIR
        }
        report("breaks a block", cobblestone && broken > 0, "${block.type} after $broken ticks")

        hits.clear()
        var blockHit = false
        fighterBody.entity.itemInHand = ItemStack(Material.DIAMOND_SWORD)
        fighter.fight(dummyBody.entity)
        until(FIGHT_TICKS) {
            blockHit = blockHit || fighterBody.isBlocking
            false
        }
        fighter.stop()
        val landed = hits.count { it.first == "Dummy" }
        report("fights a dummy", landed >= MIN_FIGHT_HITS, "$landed hits in $FIGHT_TICKS ticks, block-hit=$blockHit")

        fighter.remove()
        dummy.remove()
    }

    private suspend fun tab(world: World, x: Int, ground: Int, z: Int) {
        val bots = MinecraftAgent.bots()
        val hidden = bots.spawn(Location(world, x + 0.5, ground.toDouble(), z + 0.5), "Hidden")
        val listed = bots.spawn(Location(world, x + 0.5, ground.toDouble(), z + 3.5), "Listed")
        checkNotNull(hidden.body).tabEntry = false
        ticks(1)
        val viewer = TabViewer((world as CraftWorld).handle, x + 5.5, ground.toDouble(), z + 0.5)
        viewer.join()
        try {
            ticks(TAB_TICKS)
            report(
                "hidden from tab", "Hidden" in viewer.seen && "Hidden" !in viewer.listed && "Listed" in viewer.listed,
                "seen ${viewer.seen}, still listed ${viewer.listed}",
            )
        } finally {
            viewer.leave()
            hidden.remove()
            listed.remove()
        }
        ticks(1)
    }

    private fun buildCourse(world: World, x0: Int, ground: Int, z0: Int) {
        fun set(x: Int, y: Int, z: Int, type: Material, data: Int = 0) {
            @Suppress("DEPRECATION")
            world.getBlockAt(x, y, z).setTypeIdAndData(type.id, data.toByte(), false)
        }
        for (z in z0 - WIDTH..z0 + WIDTH) {
            set(x0 + 4, ground, z, Material.STEP)
            set(x0 + 5, ground, z, Material.STONE)
            for (x in x0 + 9..x0 + 10) for (y in ground - 3 until ground) set(x, y, z, Material.AIR)
            for (x in x0 + 18..x0 + 24) for (y in ground - 3 until ground) set(x, y, z, Material.STATIONARY_WATER)
            for (x in x0 + 30..x0 + 33) for (y in ground until ground + 4) set(x, y, z, Material.STONE)
        }
        // A ladder on the west face of the wall
        for (y in ground until ground + 4) set(x0 + 29, y, z0, Material.LADDER, LADDER_FACING_WEST)
    }

    private companion object {
        const val START_DELAY = 40L
        const val WARM_UP = 20L
        const val WALK_TIMEOUT = 600
        const val WIDTH = 8
        const val LADDER_FACING_WEST = 4
        const val KNOCKBACK_TICKS = 6L
        const val MIN_KNOCKBACK = 0.3
        const val INVULNERABLE_TICKS = 25L
        const val FIGHT_TICKS = 200
        const val MIN_FIGHT_HITS = 3
        const val HUNGRY = 10
        const val EAT_TIMEOUT = 60
        const val PEARL_TIMEOUT = 100
        const val MIN_PEARL = 3.0
        const val DIG_TIMEOUT = 100
        const val TAB_TICKS = 60L
    }
}
