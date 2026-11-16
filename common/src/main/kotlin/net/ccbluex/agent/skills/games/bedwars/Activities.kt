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

package net.ccbluex.agent.skills.games.bedwars

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.shop.Catalogue
import net.ccbluex.agent.skills.shop.Currency
import net.ccbluex.agent.skills.shop.Wishes
import net.ccbluex.agent.tick.TickScope
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import org.slf4j.LoggerFactory
import java.util.EnumMap
import java.util.Random

/**
 * The activities a Bed Wars agent takes up and carries out. Every two seconds or so it weighs what it could do by
 * the state of the round, its traits and what its teammates already do, and goes on with the best of it: the chosen
 * activity runs as suspend code on [scope] until it has nothing left to do or a new choice cancels it. Enemies close
 * by or at the bed pre-empt it every tick, which holds the running activity where it is.
 *
 * @agent claude-opus-5-5
 */
internal class Activities(
    val game: BedWars,
    val senses: Senses,
    val match: Match,
    val strategy: Strategy,
    private val callouts: Callouts,
    val random: Random,
) {

    lateinit var agent: Agent
    lateinit var team: BedWarsTeam
    lateinit var plan: TeamPlan
    lateinit var traits: Traits

    val self: Player
        get() = agent.body.player

    /**
     * The agent's player once it has played a tick.
     */
    val player: Player?
        get() = if (::agent.isInitialized) agent.body.player else null

    val level: Level
        get() = agent.body.level

    val scope = TickScope(LoggerFactory.getLogger("Agent/BedWars"))

    /**
     * The running activity, or the wait for solid ground after a respawn.
     */
    private var job: Job? = null
    private var landing: Job? = null
    private var tickTerrain: Terrain? = null

    var tick = 0L
    var goal: String? = "Waiting"
    var activity: Activity? = null

    /**
     * The enemy at or coming for the base this tick; null for none.
     */
    var intruder: Player? = null
    private var thinkAt = 0L

    /**
     * Until when the running activity is kept without weighing, unless an alarm ends the wait.
     */
    private var committedUntil = 0L
    private val taste = EnumMap<Activity, Double>(Activity::class.java)
    private var joined = false

    /**
     * The activity that just ran out of things to do, left out of the choice for a moment.
     */
    private var finished: Activity? = null
    private var finishedUntil = 0L

    /**
     * Whether the agent is back from a respawn and has not stood on the ground since.
     */
    private var backFromRespawn = true
    private var engaged = false
    private var fighting = false

    val threats = Threats(senses)
    val travel = Travel()

    // Rushing
    var rushing: String? = null
    var setOut = false
    var rushWool = 0
    var rushWoolFor: String? = null
    val avoided = HashMap<String, Long>()
    var huntAgainAt = 0L

    val oreAgainAt = EnumMap<Currency, Long>(Currency::class.java)

    // Placing and digging
    var placeAt = 0L
    val skipped = HashMap<BlockPos, Long>()
    val failures = HashMap<BlockPos, Int>()
    var digging: BlockPos? = null

    // Shopping and the team chest
    var wishes: Wishes? = null
    var wishesFor: Catalogue? = null
    var purchasing: String? = null
    val shopVisits = ShopVisits()
    var chestAgainAt = 0L

    // Team chat
    private var chatAt = -CHAT_TICKS.toLong()
    private val said = HashMap<Callout, Long>()

    fun tick(agent: Agent) {
        this.agent = agent
        tick++
        tickTerrain = null
        fighting = false
        if (!::traits.isInitialized) {
            traits = Traits.of(agent.personality, agent.skillLevel, strategy)
            for (each in Activity.entries) taste[each] = random.nextGaussian() * 0.08
        }
        play()
        if (!fighting) stand()
    }

    private fun play() {
        if (senses.state != RoundState.PLAYING) {
            idle("Waiting")
            return
        }
        if (!joined) {
            match.join(game)
            joined = true
        }
        val ownTeam = senses.ownTeam
        if (senses.isSpectator || senses.isRespawning || ownTeam == null || !self.isAlive) {
            val respawning = senses.isRespawning && ownTeam != null
            if (!respawning && ::plan.isInitialized) plan.leave(game)
            if (respawning) backFromRespawn = true
            idle(if (respawning) "Respawning" else "Out of the game")
            return
        }
        team = ownTeam
        plan = match.plan(team.name)
        team.bed?.let { bed -> plan.locate(level, bed, senses::canPlace) }
        if (isLanding()) return

        if (purchasing != null) {
            if (shopping()) return
            bought()
        }
        if (tick % Threats.APPROACH_TICKS == 0L) threats.trackApproach(self, team)
        val intruder = threats.intruder(self, team, tick)
        // A bed alarm is no time to finish the thought
        if (intruder != null && this.intruder == null) alarm()
        this.intruder = intruder
        if (activity == null || job?.isActive != true || tick >= thinkAt) think()
        val guardsBed = team.isBedAlive && (activity == Activity.DEFEND || isOnIsland())
        val enemy = threats.threat(self, team, guardsBed, terrain(), tick)
        if (enemy != null && agent.skills.fight != null) {
            fight(enemy)
            return
        }
        scope.tick()
    }

    /**
     * The client's respawn wait: back from a death, or at the start, the agent first stands on the ground for a
     * second, so it does not plan from where it hung in the air.
     */
    private fun isLanding(): Boolean {
        if (backFromRespawn) {
            backFromRespawn = false
            landing = scope.launch {
                waitTicks(MIN_RESPAWN_TICKS)
                while (!self.onGround()) waitTicks(1)
            }
        }
        if (landing?.isActive != true) return false
        goal = "Respawning"
        scope.tick()
        return landing?.isActive == true
    }

    /**
     * Thinks again on the next tick.
     */
    fun rethink() {
        thinkAt = tick
    }

    /**
     * The terrain of this tick, read lazily.
     */
    fun terrain(): Terrain = tickTerrain ?: Terrain.live(level).also { tickTerrain = it }

    private fun idle(text: String) {
        goal = text
        cancel()
        activity = null
        purchasing = null
        intruder = null
        threats.forget()
        stand()
        setOut = false
    }

    private fun cancel() {
        job?.cancel()
        job = null
        travel.forget()
        stopDigging()
        if (::plan.isInitialized) plan.bridge(game, null)
    }

    fun stop() {
        idle("Waiting")
        landing?.cancel()
        scope.cancel()
        match.leave(game)
        joined = false
    }

    fun damaged(attacker: Entity?) = threats.damaged(attacker, tick)

    private fun think() {
        thinkAt = tick + THINK_TICKS + random.nextInt(THINK_SPREAD) - (traits.skill * 15).toInt()
        val scores = scores(facts(intruder), traits)
        val committed = job?.isActive == true && tick < committedUntil
        val (best, score) =
            choose(scores, taste, traits, activity, finished.takeIf { tick < finishedUntil }, committed, random)
        if (best != activity || job?.isActive != true) {
            if (best != activity) {
                agent.decide(best.name, score, "think")
                agent.listener.phase(best.name)
                announce(best)
            }
            activity = best
            committedUntil = tick + COMMIT_TICKS
            cancel()
            job = scope.launch {
                carryOut(best)
                if (activity == best) {
                    finished = best
                    finishedUntil = tick + FINISHED_TICKS
                    thinkAt = tick + 1
                }
            }
        }
        if (best == Activity.RUSH) rushing = rushTarget()?.name
        plan.doing(game, best, if (best == Activity.RUSH) rushing else null)
    }

    /**
     * Carries the activity out until there is nothing left to do in it.
     */
    private suspend fun carryOut(activity: Activity) {
        when (activity) {
            Activity.COVER -> cover()
            Activity.DEFEND -> defend()
            Activity.GEAR -> gear()
            Activity.GATHER -> gatherUp()
            Activity.DIAMONDS -> ore(Currency.DIAMOND)
            Activity.EMERALDS -> ore(Currency.EMERALD)
            Activity.UPGRADE -> upgrade()
            Activity.RUSH -> rush()
            Activity.HUNT -> hunt()
            Activity.HELP -> help()
        }
    }

    private fun announce(activity: Activity) {
        when (activity) {
            Activity.COVER -> say(Callout.CoveringBed)
            Activity.DIAMONDS -> say(Callout.GettingDiamonds)
            Activity.EMERALDS -> say(Callout.GettingEmeralds)
            Activity.HELP -> say(Callout.ComingToHelp)
            else -> Unit
        }
    }

    /**
     * Fights [enemy] this tick, through the fight ability; without one the agent only stops what it was doing.
     */
    fun fight(enemy: Player) {
        stopDigging()
        plan.bridge(game, null)
        goal = "Fighting " + enemy.scoreboardName
        val fight = agent.skills.fight ?: return
        fighting = true
        if (!engaged || fight.target !== enemy) {
            fight.engage(enemy)
            agent.listener.engaged(enemy)
        }
        engaged = true
    }

    /**
     * Stops fighting, so the activity moves the agent again.
     */
    fun stand() {
        if (!engaged) return
        engaged = false
        agent.skills.fight?.disengage()
    }

    fun bedDestroyed(teamName: String, breaker: Player?) {
        if (!::team.isInitialized || !::agent.isInitialized) return
        if (breaker != null && breaker === self) {
            say(Callout.BrokeBed(teamName))
        } else if (teamName == team.name && (activity == Activity.COVER || activity == Activity.DEFEND)) {
            say(Callout.BedLost)
        }
        alarm()
    }

    /**
     * Thinks again on the next tick, free to drop the running activity.
     */
    private fun alarm() {
        thinkAt = tick
        committedUntil = tick
    }

    /**
     * A line for the team, at most every 12 s and the same one at most every 45 s.
     */
    fun say(callout: Callout) {
        if (tick - chatAt < CHAT_TICKS || tick - (said[callout] ?: -REPEAT_TICKS.toLong()) < REPEAT_TICKS) return
        chatAt = tick
        said[callout] = tick
        callouts.say(callout)
    }

    // Where it is

    fun isOnIsland(): Boolean = self.position().horizontalDistanceTo(team.spawn) < ISLAND + 2

    fun away(): Double =
        if (isOnIsland()) 0.0 else minOf(1.0, self.position().horizontalDistanceTo(team.spawn) / 60)

    /**
     * Whether no teammate, agent or player, is on the island.
     */
    fun isNobodyHome(): Boolean = team.members.none { mate ->
        mate !== self && mate.isAlive && !mate.isSpectator && mate.level() === self.level() &&
            mate.position().horizontalDistanceTo(team.spawn) < ISLAND + 2
    }

    /**
     * Teammates the agent serves who still play: its [BedWars.leaders].
     */
    fun leaders(): List<Player> =
        team.members.filter { it !== self && game.isLeader(it) && !it.isSpectator && it.level() === self.level() }

    fun eyes(): Vec3 = self.eyePosition

    fun look(point: Vec3) {
        agent.body.look(ViewRotation.fromVector(point.subtract(eyes())), LookPurpose.AIM)
    }

    fun teamNamed(name: String?): BedWarsTeam? = senses.teams.firstOrNull { it.name == name }

    companion object {
        const val THINK_TICKS = 40
        private const val THINK_SPREAD = 20

        /**
         * How long an activity that ran out of things to do stays out of the choice.
         */
        const val FINISHED_TICKS = 60

        /**
         * How long a new activity is kept before a think may swap it for another, unless it ends or an alarm.
         */
        const val COMMIT_TICKS = 200
        const val MIN_RESPAWN_TICKS = 20

        /**
         * How far around the team spawn the agent counts itself on its island.
         */
        const val ISLAND = 10
        const val CHAT_TICKS = 240
        const val REPEAT_TICKS = 900
    }
}
