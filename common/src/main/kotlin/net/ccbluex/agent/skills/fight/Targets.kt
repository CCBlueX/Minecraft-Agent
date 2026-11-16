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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.games.RoundSenses
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Who the agent fights, and how the round's fights went: the opponents around, hits taken (with who dealt them) and
 * landed, the weapons opponents were seen with, the reach they hit from, recent killers, and whether an opponent is
 * a bot. One per round, made and ticked by the game mode; it reads only the world and [senses], so it works the same
 * for a client and a server agent.
 *
 * From LiquidBounce: [Opponents.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Opponents.kt#L75-L196)
 *
 * @agent claude-opus-5-5
 */
public class Targets(private val senses: RoundSenses) {

    private class Opponent(val name: String) {
        var entity: Player? = null
        var prevHurtTime = 0
        var prevPosition: Vec3? = null
        var wasSwinging = false
        var swungAt = NEVER
        var seenTicks = 0
        var moved = false
        var damage = 0.0
        var hitsOnUs = 0
        var reachSum = 0.0
        val pendingAttacks = ArrayDeque<Long>()
    }

    private class Hit(val tick: Long, val attacker: String?)

    private var ticks = 0L
    private var self: Player? = null
    private var body: AgentBody? = null
    private var selfSwinging = false
    private val opponents = HashMap<String, Opponent>()
    private val hits = ArrayDeque<Hit>()
    private val landed = ArrayDeque<Long>()
    private val killedAt = HashMap<String, Long>()

    /**
     * Once a tick, before anything reads it.
     */
    public fun tick(agent: Agent) {
        ticks++
        val self = agent.body.player
        this.self = self
        body = agent.body
        while (hits.firstOrNull()?.let { ticks - it.tick > HISTORY_TICKS } == true) hits.removeFirst()
        while (landed.firstOrNull()?.let { ticks - it > HISTORY_TICKS } == true) landed.removeFirst()

        for (other in agent.body.players()) {
            if (other !== self && !other.isSpectator && self.distanceToSqr(other) <= WATCH_RANGE * WATCH_RANGE)
                watch(other)
        }
        val swinging = self.isSwinging
        if (swinging && !selfSwinging) swingTarget(self)?.pendingAttacks?.addLast(ticks)
        selfSwinging = swinging

        for (opponent in opponents.values) {
            val entity = opponent.entity ?: continue
            if (entity.isRemoved || !entity.isAlive || entity.isSpectator || entity.level() !== self.level()) {
                opponent.entity = null
                continue
            }
            noteHurt(opponent, entity)
            sample(opponent, entity)
        }
    }

    /**
     * The agent took a hit; the game mode passes on what [net.ccbluex.agent.skills.games.GameMode.damaged] got.
     */
    public fun damaged(attacker: Entity?) {
        val self = self ?: return
        val opponent = (attacker as? Player)?.let { watch(it) } ?: if (attacker == null) null else guessAttacker(self)
        opponent?.entity?.let { entity ->
            opponent.hitsOnUs++
            opponent.reachSum += sqrt(self.boundingBox.distanceToSqr(entity.eyePosition))
        }
        hits.addLast(Hit(ticks, opponent?.name))
    }

    /**
     * A kill feed line, when the host has one.
     */
    public fun killed(victim: String, killer: String) {
        killedAt[killer] = ticks
        if (victim != self?.scoreboardName) opponents[victim]?.entity = null
    }

    /**
     * The opponents in the agent's level.
     */
    public fun around(): List<Player> = body?.players()?.filter(::isOpponent) ?: emptyList()

    public fun isOpponent(player: Player): Boolean =
        player !== self && player.isAlive && !player.isSpectator && !player.isInvisible && senses.isOpponent(player)

    /**
     * Hits the agent took within the last [windowTicks]; with [byPlayer] only those a player is taken to have dealt.
     */
    @JvmOverloads
    public fun hitsTaken(windowTicks: Int, byPlayer: Boolean = false): Int =
        hits.count { ticks - it.tick <= windowTicks && (!byPlayer || it.attacker != null) }

    /**
     * Hits the agent landed within the last [windowTicks].
     */
    public fun hitsLanded(windowTicks: Int): Int = landed.count { ticks - it <= windowTicks }

    /**
     * The player who hit the agent last within [windowTicks], while still around.
     */
    public fun lastAttacker(windowTicks: Int): Player? =
        hits.lastOrNull { ticks - it.tick <= windowTicks && it.attacker != null }
            ?.let { opponents[it.attacker]?.entity }

    /**
     * Players the kill feed named as killer within the last [windowTicks], the latest first.
     */
    public fun recentKillers(windowTicks: Int): List<String> =
        killedAt.entries.filter { ticks - it.value <= windowTicks }.sortedByDescending { it.value }.map { it.key }

    /**
     * The best weapon [player] was seen holding this round, at least what it holds now, as a damage score.
     */
    public fun weaponDamage(player: Player): Double {
        val seen = opponents[player.scoreboardName]?.takeIf { it.entity === player }?.damage ?: 0.0
        return max(seen, GearScore.weaponScore(player.mainHandItem))
    }

    /**
     * The mean distance [player] hit the agent from this round; null before its first hit.
     */
    public fun reachOnUs(player: Player): Double? =
        opponents[player.scoreboardName]?.takeIf { it.hitsOnUs > 0 }?.let { it.reachSum / it.hitsOnUs }

    /**
     * Whether [player] is a bot or NPC: the host says so, or it has an NPC id, never moved while watched for a while,
     * or carries a name no account can have.
     */
    public fun isBot(player: Player): Boolean {
        if (senses.isBot(player) || player.uuid.version() == NPC_UUID_VERSION ||
            !VALID_NAME.matches(player.scoreboardName)
        ) return true
        val opponent = opponents[player.scoreboardName] ?: return false
        return !opponent.moved && opponent.seenTicks >= STATIC_TICKS
    }

    /**
     * For a new round.
     */
    public fun reset() {
        opponents.clear()
        hits.clear()
        landed.clear()
        killedAt.clear()
    }

    private fun watch(entity: Player): Opponent {
        val opponent = opponents.getOrPut(entity.scoreboardName) { Opponent(entity.scoreboardName) }
        if (opponent.entity !== entity) {
            opponent.entity = entity
            opponent.prevHurtTime = entity.hurtTime
            opponent.prevPosition = null
        }
        return opponent
    }

    private fun sample(opponent: Opponent, entity: Player) {
        opponent.seenTicks++
        val position = entity.position()
        if (opponent.prevPosition?.let { it != position } == true) opponent.moved = true
        opponent.prevPosition = position
        val swinging = entity.isSwinging
        if (swinging && !opponent.wasSwinging) opponent.swungAt = ticks
        opponent.wasSwinging = swinging
        opponent.damage = max(opponent.damage, GearScore.weaponScore(entity.mainHandItem))
    }

    /**
     * A hurt animation within a moment after the agent's swing at it, not caused by someone else, is a landed hit.
     */
    private fun noteHurt(opponent: Opponent, entity: Player) {
        if (entity.hurtTime > opponent.prevHurtTime) {
            val source = entity.lastHurtByMob
            val byOther = source != null && source !== self
            if (!byOther && opponent.pendingAttacks.any { ticks - it <= LAST_HIT_TICKS }) landed.addLast(ticks)
            // Attacks before this hurt either landed now or met the damage immunity
            opponent.pendingAttacks.clear()
        }
        opponent.prevHurtTime = entity.hurtTime
    }

    /**
     * The opponent the agent looks at most directly within reach, for its own swing.
     */
    private fun swingTarget(self: Player): Opponent? {
        val view = self.getViewVector(1f)
        return opponents.values.filter { opponent ->
            val entity = opponent.entity ?: return@filter false
            self.distanceToSqr(entity) <= SWING_RANGE * SWING_RANGE &&
                angle(view, entity.boundingBox.center.subtract(self.eyePosition)) <= SWING_ANGLE
        }.minByOrNull { opponent ->
            opponent.entity?.let { angle(view, it.boundingBox.center.subtract(self.eyePosition)) } ?: Double.MAX_VALUE
        }
    }

    /**
     * The closest opponent swinging and looking at the agent, for a hit without a named attacker.
     */
    private fun guessAttacker(self: Player): Opponent? = opponents.values.mapNotNull { opponent ->
        val entity = opponent.entity ?: return@mapNotNull null
        val reachSq = self.boundingBox.distanceToSqr(entity.eyePosition)
        if (reachSq > ATTACK_RANGE * ATTACK_RANGE) return@mapNotNull null
        val facing = angle(entity.getViewVector(1f), self.boundingBox.center.subtract(entity.eyePosition))
        val swingBonus = if (ticks - opponent.swungAt <= SWING_TICKS) SWING_BONUS else 0.0
        opponent to sqrt(reachSq) + facing / FACING_DEGREES_PER_BLOCK - swingBonus
    }.minByOrNull { it.second }?.first

    private fun angle(view: Vec3, towards: Vec3): Double =
        Math.toDegrees(acos(view.dot(towards.normalize()).coerceIn(-1.0, 1.0)))

    public companion object {

        private const val NEVER = Long.MIN_VALUE / 2
        private const val WATCH_RANGE = 12.0
        private const val ATTACK_RANGE = 8.0
        private const val SWING_RANGE = 6.0
        private const val SWING_ANGLE = 30.0
        private const val SWING_TICKS = 5
        private const val SWING_BONUS = 2.0
        private const val FACING_DEGREES_PER_BLOCK = 15.0
        private const val LAST_HIT_TICKS = 10
        private const val HISTORY_TICKS = 200
        private const val NPC_UUID_VERSION = 2
        private const val STATIC_TICKS = 160
        private val VALID_NAME = Regex("[A-Za-z0-9_]{3,16}")
    }
}
