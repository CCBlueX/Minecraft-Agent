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

package net.ccbluex.agent.skills.games.blitz

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.fight.Outnumbered
import net.ccbluex.agent.skills.fight.Targets
import net.ccbluex.agent.skills.games.Progress
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.TickScope
import net.ccbluex.agent.skills.gear.WeaponMemory
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3

/**
 * One round of [Blitz] for one agent. The round itself is suspend code on [scope], read top to bottom in [play]; the
 * tick takes in what the senses and the players around tell, and the deathmatch teleport and start cut the running
 * act short for the deathmatch.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L973-L1049)
 *
 * @agent claude-opus-5-5
 */
internal class Round(val agent: Agent, val senses: Senses) {

    val scope = TickScope(agent.logger)
    val targets = Targets(senses)
    val outnumbered = Outnumbered(targets)
    val weapons = WeaponMemory()
    val plan = Plan()
    val progress = Progress()
    private var job: Job? = null

    var phase = Phase.IDLE
        private set
    var goal: String? = null
    var ticks = 0L
        private set

    var roundStartedAt = 0L
        private set
    var kitAt: Long? = null
    private var kitKnown = false

    /**
     * The deathmatch countdown was waited out this round, so the next teleport goes straight to fighting.
     */
    var prepared = false
    private var refills = 0

    var center: Vec3? = null
    var centerDone = false
    var centerSoughtAt = Long.MIN_VALUE / 2
    var hungerCheckAt = 0L
    private var lastPosition: Vec3? = null

    val visitedChunks = LongOpenHashSet()
    val lastSeen = HashMap<String, Sighting>()
    var roam: Vec3? = null
    var roamAngle = Double.NaN
    val unreachableUntil = HashMap<String, Long>()
    var target: String? = null
        private set
    var targetSince = 0L
        private set

    var opponent: Player? = null
    var calmTicks = 0
    var evading = false
    private var lootingChest: BlockPos? = null

    val player: Player
        get() = agent.body.player

    fun tick() {
        ticks++
        if (senses.isSpectator && phase.isPlaying || senses.state == RoundState.ENDED) {
            if (phase != Phase.END) end()
            return
        }
        when (senses.state) {
            RoundState.LOBBY, RoundState.ENDED -> Unit
            RoundState.COUNTDOWN -> if (phase == Phase.IDLE) enter(Phase.START, "countdown")
            RoundState.PLAYING -> if (phase == Phase.START || phase == Phase.IDLE && player.onGround()) {
                start(fromPedestal = phase == Phase.START)
            }
        }
        if (!phase.isPlaying) {
            goal = null
            return
        }
        noteRound()
        scope.tick()
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
        stopFight("stopped")
        outnumbered.stop()
        weapons.clear()
    }

    /**
     * Runs [act] from now on, instead of what ran so far.
     */
    private fun restart(act: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.start(act)
    }

    private fun start(fromPedestal: Boolean) {
        roundStartedAt = ticks
        prepared = false
        kitKnown = false
        kitAt = null
        syncChests()
        enter(Phase.CENTER, if (fromPedestal) "games began" else "joined running round")
        restart { play(fromPedestal) }
    }

    /**
     * What every playing tick takes in: kit and deathmatch timers, teleports, chests, players and places seen. The
     * deathmatch countdown running out and its start cut the running act short.
     */
    private fun noteRound() {
        val kitDelay = senses.kitDelaySeconds
        if (!kitKnown && kitDelay != null) {
            kitKnown = true
            kitAt = roundStartedAt + kitDelay * TICKS_PER_SECOND
        }
        checkTeleport()
        val toDeathmatch = senses.secondsToDeathmatch
        if (phase != Phase.PREPARE && toDeathmatch != null && toDeathmatch <= 0) startPrepare("deathmatch countdown over")
        if (senses.isDeathmatchStarted && phase != Phase.PREPARE) startDeathmatch("deathmatch started")
        if (ticks % SYNC_TICKS == 0L) syncChests()
        targets.tick(agent)
        outnumbered.tick(agent)
        visitedChunks.add(ChunkPos.pack(player.blockX shr CHUNK_SHIFT, player.blockZ shr CHUNK_SHIFT))
        for (other in targets.around()) {
            weapons.remember(other)
            lastSeen[other.scoreboardName] = Sighting(other.scoreboardName, other.position(), ticks)
        }
    }

    private fun syncChests() {
        val chests = agent.skills.loot?.chests ?: return
        val known = chests.all
        for (pos in senses.chests) {
            if (pos !in known) chests.add(pos)
        }
        if (senses.refills > refills) {
            refills = senses.refills
            chests.refilled()
        }
        chests.secondsToRefill = senses.secondsToRefill
    }

    /**
     * The deathmatch countdown starts, unless it was waited out already this round.
     */
    private fun startPrepare(reason: String) {
        if (prepared) return startDeathmatch(reason)
        if (!phase.isPlaying || phase == Phase.PREPARE) return
        stopFight("deathmatch prepare")
        enter(Phase.PREPARE, reason)
        restart {
            prepare()
            deathmatch()
        }
    }

    private fun startDeathmatch(reason: String) {
        if (!phase.isPlaying || phase == Phase.DEATHMATCH) return
        enter(Phase.DEATHMATCH, reason)
        restart { deathmatch() }
    }

    private fun end() {
        job?.cancel()
        stopFight("round over")
        enter(Phase.END, "round over")
    }

    /**
     * A jump the walk cannot make; one late in the round is taken as the teleport to the deathmatch.
     */
    private fun checkTeleport() {
        val position = player.position()
        val last = lastPosition
        lastPosition = position
        if (last == null || last.distanceTo(position) < TELEPORT_DISTANCE) return
        agent.logger.debug("Teleported {} blocks", last.distanceTo(position))
        roam = null
        resetTarget()
        if (ticks - roundStartedAt >= DEATHMATCH_MIN_TICKS) startPrepare("teleport")
    }

    /**
     * Lets the loot ability finish the chest it opens; true while it does.
     */
    fun isLooting(): Boolean {
        val loot = agent.skills.loot ?: return false
        val chest = lootingChest ?: return false
        if (!loot.isLooting) {
            lootingChest = null
            return false
        }
        loot.open(chest)
        targetSince = ticks
        progress.reset()
        goal = "Looting"
        return true
    }

    /**
     * The nearest chest not opened since the refill matching [where], not given up on.
     */
    fun pickChest(where: (BlockPos) -> Boolean): BlockPos? {
        val loot = agent.skills.loot ?: return null
        val position = player.position()
        return loot.chests.unopened()
            .filter { isReachable(chestLabel(it)) && where(it) }
            .minByOrNull { it.distToCenterSqr(position) }
    }

    /**
     * Has the loot ability walk up to [chest] and open it, giving it up after [CHEST_TICKS].
     */
    fun openChest(chest: BlockPos) {
        val loot = agent.skills.loot ?: return
        val label = chestLabel(chest)
        if (label == target && ticks - targetSince > CHEST_TICKS) {
            loot.chests.giveUp(chest)
            return resetTarget()
        }
        noteTarget(label, Vec3.atCenterOf(chest))
        goal = "Going to a chest"
        if (loot.open(chest)) {
            lootingChest = chest
        } else {
            loot.chests.giveUp(chest)
            resetTarget()
        }
    }

    /**
     * Walks to [goal] and gives it up for a while once the walk gets no closer.
     */
    fun walkTo(label: String, goal: Vec3, radius: Double) {
        noteTarget(label, goal)
        agent.walkTo(goal, radius)
        this.goal = "Going to $label"
        val distance = player.position().distanceTo(goal)
        if (!progress.check(label, distance, agent.navigator.isStranded, ticks)) {
            unreachableUntil[label] = ticks + RETRY_TICKS
            agent.logger.debug("Unreachable: {}", label)
            resetTarget()
        }
    }

    private fun noteTarget(label: String, goal: Vec3) {
        if (label == target) return
        target = label
        targetSince = ticks
        progress.reset()
        agent.decide("target", player.position().distanceTo(goal), label)
    }

    fun resetTarget() {
        target = null
        progress.reset()
    }

    fun isReachable(label: String): Boolean = (unreachableUntil[label] ?: 0L) <= ticks

    fun enter(next: Phase, reason: String) {
        if (phase != next) {
            agent.decide("phase " + next.name.lowercase(), 0.0, reason)
            agent.listener.phase(next.label)
        }
        phase = next
        resetTarget()
    }

    private fun chestLabel(pos: BlockPos) = "chest ${pos.x},${pos.y},${pos.z}"

    companion object {
        const val TICKS_PER_SECOND = 20
        const val SYNC_TICKS = 20
        const val CHUNK_SHIFT = 4
        const val CENTER_LABEL = "center"
        const val CENTER_ARRIVAL = 3.0
        const val CHEST_TICKS = 15 * TICKS_PER_SECOND
        const val RETRY_TICKS = 600
        const val TELEPORT_DISTANCE = 16.0
        const val DEATHMATCH_MIN_TICKS = 60 * TICKS_PER_SECOND
    }
}
