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

package net.ccbluex.agent.skills.games.skywars

import kotlinx.coroutines.Job
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.fight.Outnumbered
import net.ccbluex.agent.skills.fight.OutnumberedPlan
import net.ccbluex.agent.skills.fight.Targets
import net.ccbluex.agent.skills.games.Progress
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.TickScope
import net.ccbluex.agent.skills.gear.WeaponMemory
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Random

/**
 * One round of [SkyWars] for one agent: waiting in the cage, then every tick fighting whoever is a threat, else
 * working on the plan the last decision picked, which runs as suspend code on [scope].
 *
 * From LiquidBounce: [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L146-L153)
 *
 * @agent claude-opus-5-5
 */
internal class Round(val agent: Agent, val senses: Senses, options: Options) {

    val scope = TickScope(agent.logger)
    val targets = Targets(senses)
    val outnumbered = Outnumbered(targets)
    val weapons = WeaponMemory()
    val crossing = options.flight?.let { Crossing(it, options) }
    val random = Random()
    val progress = Progress()
    private val cage = Cage()
    private val threats = Threats(this)

    /**
     * Ticks after the cages open before it moves, and the ticks it holds a spot at most over the round.
     */
    private val lootDelay = random.nextInt(LOOT_DELAY_MIN, LOOT_DELAY_MAX + 1)
    val holdTicks = random.nextInt(HOLD_MIN, HOLD_MAX + 1)

    /**
     * How long after a real player closed a chest it takes what they left.
     */
    val leftoverDelay = random.nextInt(LEFTOVER_MIN, LEFTOVER_MAX + 1)

    var phase = "Waiting"
    var goal: String? = null
    var ticks = 0L
        private set

    var island: Vec3 = Vec3.ZERO
        private set
    private var startedAt = -1L
    private var lootAt = 0L

    var plan: Plan? = null
        private set
    private var job: Job? = null
    private var planAt = 0L
    var decideAt = 0L

    /**
     * Targets given up on until when: entities by id, places and chests by block.
     */
    val avoided = HashMap<Any, Long>()
    val unreachable = HashSet<BlockPos>()

    /**
     * The chests left to a real player standing close by, until when.
     */
    val firstPick = HashMap<BlockPos, Long>()

    /**
     * When the first chest left to a real player comes free, as of the last decision.
     */
    var chestFreeAt = Long.MAX_VALUE

    /**
     * The chest the plan heads for or loots now.
     */
    var chest: BlockPos? = null
    var openedAny = false
    var heldTicks = 0
    var pearlAt = 0L
    var dugCount = 0
    private var middleGround: Vec3? = null
    private var refills = 0
    private var engaged: Entity? = null
    private var terrainTick = -1L
    private var terrainOfTick: Terrain? = null

    val player: Player
        get() = agent.body.player

    /**
     * The blocks around, read once a tick.
     */
    val terrain: Terrain
        get() {
            val cached = terrainOfTick
            if (cached != null && terrainTick == ticks) return cached
            return Terrain.live(agent.body.level).also {
                terrainOfTick = it
                terrainTick = ticks
            }
        }

    fun tick() {
        ticks++
        if (senses.isSpectator || senses.state == RoundState.ENDED) {
            phase = if (senses.isSpectator) "Out" else "Round over"
            dropPlan()
            return
        }
        if (senses.ownSpawn == null) cage.tick(player)
        if (senses.state != RoundState.PLAYING && !cage.dropped) {
            phase = if (senses.state == RoundState.COUNTDOWN || cage.position != null) "Caged" else "Waiting"
            goal = null
            return
        }
        if (startedAt < 0) start()
        if (ticks % SYNC_TICKS == 0L) syncChests()
        targets.tick(agent)
        outnumbered.tick(agent)
        targets.around().forEach(weapons::remember)

        // A flight over the void ends before anything else: a fight up there drops the agent.
        if (crossing?.isFlying == true) {
            if (job?.isActive == true) scope.tick() else crossing.land(agent, terrain, ticks)
            return
        }
        if (agent.skills.voidRescue?.isRescuing == true) {
            phase = "Rescuing"
            return
        }
        (outnumbered.plan as? OutnumberedPlan.Retreat)?.let {
            retreat(it.point)
            return
        }
        val enemy = if (agent.skills.fight != null) threats.pick() else null
        if (enemy != null) {
            fight(enemy)
            return
        }
        release()
        if (ticks < lootAt) {
            phase = "Waiting to loot"
            goal = null
            return
        }
        if (agent.skills.loot?.isLooting != true && (plan == null || ticks >= decideAt || job?.isActive != true)) {
            decide()
        }
        scope.tick()
    }

    fun damaged(attacker: Entity?) {
        targets.damaged(attacker)
    }

    fun stop() {
        dropPlan()
        release()
        crossing?.stop(agent, "stopped", ticks)
        outnumbered.stop()
        scope.cancel()
        weapons.clear()
    }

    private fun start() {
        startedAt = ticks
        lootAt = ticks + lootDelay
        island = senses.ownSpawn ?: cage.position ?: player.position()
        phase = "Playing"
        agent.listener.phase(phase)
    }

    /**
     * Hands the host's chests, opens and refills to the loot ability.
     */
    private fun syncChests() {
        val chests = agent.skills.loot?.chests ?: return
        val known = chests.all
        for (pos in senses.chests) {
            if (pos !in known) chests.add(pos)
        }
        if (senses.refills > refills) {
            refills = senses.refills
            chests.refilled()
            unreachable.clear()
        }
        chests.secondsToRefill = senses.secondsToRefill
    }

    private fun fight(enemy: Player) {
        dropPlan()
        decideAt = ticks
        val fight = agent.skills.fight ?: return
        fight.engage(enemy)
        if (engaged !== enemy) {
            engaged = enemy
            agent.listener.engaged(enemy)
        }
        phase = "Fighting"
        goal = "Fighting " + enemy.scoreboardName
    }

    private fun retreat(point: Vec3) {
        dropPlan()
        release()
        decideAt = ticks
        agent.walkTo(point, RETREAT_RADIUS)
        phase = "Retreating"
        goal = "Backing away from a group"
    }

    private fun release() {
        if (engaged == null) return
        engaged = null
        agent.skills.fight?.disengage()
    }

    private fun decide() {
        decideAt = ticks + DECIDE_TICKS + random.nextInt(DECIDE_SPREAD)
        avoided.values.removeIf { it <= ticks }
        threats.forgetStalled()
        chestFreeAt = Long.MAX_VALUE
        val options = Plans.score(situation(), agent.personality)
        // Back to a chest left to a real player as soon as it is its turn.
        decideAt = minOf(decideAt, chestFreeAt)
        val current = plan
        val best = Plans.pick(options, current, agent.personality.whim, random, chestUnderway = chest != null)
        plan = best
        if (best.same(current) && job?.isActive == true) return

        job?.cancel()
        planAt = ticks
        progress.reset()
        chest = null
        agent.decide(best.activity.name.lowercase(), best.score, best.reason)
        if (phase != best.activity.label) {
            phase = best.activity.label
            agent.listener.phase(phase)
        }
        job = scope.start {
            val avoid = act(best)
            if (best.same(plan)) finish(avoid)
        }
    }

    /**
     * Drops the plan, leaving [avoid] alone for a while when it could not be got to.
     */
    private fun finish(avoid: Any?) {
        if (avoid != null) avoided[avoid] = ticks + AVOID_TICKS
        plan = null
        chest = null
        decideAt = ticks
        progress.reset()
    }

    private fun dropPlan() {
        job?.cancel()
        job = null
        chest = null
        crossing?.stop(agent, "plan dropped", ticks)
    }

    fun isAvoided(key: Any): Boolean = (avoided[key] ?: return false) > ticks

    fun enemies(): List<Player> = targets.around().filter { it.isAlive && !it.isSpectator }

    fun teammates(): List<Player> = agent.body.players().filter {
        it !== player && it.isAlive && !it.isSpectator && senses.isTeammate(it)
    }

    /**
     * On a cage's island or the middle, rather than out on a bridge.
     */
    fun onIsland(at: Vec3): Boolean = senses.cages.any { it.horizontalDistanceTo(at) <= SkyWars.ISLAND_RADIUS } ||
        middle()?.let { it.horizontalDistanceTo(at) <= SkyWars.ISLAND_RADIUS + 2 } == true

    /**
     * The ground of the middle island, under the host's middle point or the middle of all chests.
     */
    fun middle(): Vec3? {
        middleGround?.let { return it }
        val point = senses.middle ?: chestMiddle() ?: return null
        val x = Mth.floor(point.x)
        val z = Mth.floor(point.z)
        var y = Mth.floor(point.y)
        val terrain = terrain
        repeat(MIDDLE_DROP) {
            if (y <= terrain.minY) return null
            if (!terrain.isPassable(x, y - 1, z) && terrain.isPassable(x, y, z)) {
                return Vec3(x + 0.5, y.toDouble(), z + 0.5).also { middleGround = it }
            }
            y--
        }
        return null
    }

    private fun chestMiddle(): Vec3? {
        val chests = senses.chests
        if (chests.size < MIN_MIDDLE_CHESTS) return null
        return Vec3(
            chests.sumOf { it.x + 0.5 } / chests.size,
            chests.sumOf { it.y.toDouble() } / chests.size + 1,
            chests.sumOf { it.z + 0.5 } / chests.size,
        )
    }

    companion object {
        const val DECIDE_TICKS = 30
        const val DECIDE_SPREAD = 20
        const val AVOID_TICKS = 400
        const val SYNC_TICKS = 20
        const val RETREAT_RADIUS = 1.0
        const val MIN_MIDDLE_CHESTS = 6
        const val MIDDLE_DROP = 200
        const val LOOT_DELAY_MIN = 20
        const val LOOT_DELAY_MAX = 120
        const val HOLD_MIN = 300
        const val HOLD_MAX = 1200
        const val LEFTOVER_MIN = 40
        const val LEFTOVER_MAX = 80
    }
}
