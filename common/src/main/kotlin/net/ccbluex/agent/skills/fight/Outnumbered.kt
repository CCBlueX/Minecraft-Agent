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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * What to do against two or more opponents at once.
 *
 * @agent claude-opus-5-5
 */
public sealed interface OutnumberedPlan {

    /**
     * Fight the one standing apart from the others alone: [Outnumbered.allows] leaves the rest out.
     */
    public class Focus(public val target: Player) : OutnumberedPlan

    /**
     * Walk away from the group to [point], over ground, to heal.
     */
    public class Retreat(public val point: Vec3) : OutnumberedPlan
}

/**
 * Notices when two or more opponents close in while the agent is low or loses the trade, and decides how to get out:
 * [OutnumberedPlan.Focus] on the one standing apart, or [OutnumberedPlan.Retreat] away from the group over ground.
 * The plan ends once the fight is one on one again or the agent healed. The game mode ticks it after [targets] and
 * follows [plan].
 *
 * From LiquidBounce: [Outnumbered.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Outnumbered.kt#L54-L199)
 *
 * @agent claude-opus-5-5
 */
public class Outnumbered(private val targets: Targets) {

    public var plan: OutnumberedPlan? = null
        private set

    /**
     * Who the plan was made against; still counted while the focus is fought alone.
     */
    private var group = emptySet<String>()
    private var decidedAt = 0L
    private var ticks = 0L

    /**
     * Ticks in a row the trigger held before a plan, and the group stayed away during one.
     */
    private var triggerTicks = 0
    private var clearTicks = 0

    public fun tick(agent: Agent) {
        ticks++
        val self = agent.body.player
        val hp = self.health
        val current = plan
        if (current == null) {
            val near = closeOpponents(agent.body, GROUP_RANGE)
            val triggered = near.size >= MIN_GROUP && (hp < LOW_HP || isLosing())
            triggerTicks = if (triggered) triggerTicks + 1 else 0
            if (triggerTicks >= TRIGGER_TICKS) decide(agent, near, hp)
            return
        }
        val near = closeOpponents(agent.body, CLEAR_RANGE)
        clearTicks = if (near.size < MIN_GROUP) clearTicks + 1 else 0
        val review = current is OutnumberedPlan.Focus && !current.target.isAlive || ticks - decidedAt >= REVIEW_TICKS
        when {
            current is OutnumberedPlan.Retreat && hp >= HEALED_HP -> end(agent, "healed")
            clearTicks >= CLEAR_TICKS -> end(agent, "one on one")
            review && near.size >= MIN_GROUP -> decide(agent, near, hp)
        }
    }

    /**
     * Whether the agent may fight [entity] now: while focusing, the rest of the group is left alone.
     */
    public fun allows(entity: Entity): Boolean {
        val focus = (plan as? OutnumberedPlan.Focus)?.target ?: return true
        return entity === focus || entity !is Player || entity.scoreboardName !in group
    }

    /**
     * For when the round or the agent stops.
     */
    public fun stop() {
        plan = null
        group = emptySet()
        triggerTicks = 0
        clearTicks = 0
    }

    private fun closeOpponents(body: AgentBody, range: Double): List<Player> {
        val self = body.player
        return body.players().filter { other ->
            val counted = if (plan == null) targets.isOpponent(other) else other.scoreboardName in group
            counted && other !== self && other.isAlive && !other.isSpectator && other.distanceTo(self) <= range
        }
    }

    private fun isLosing() = targets.hitsLanded(TRADE_TICKS) < targets.hitsTaken(TRADE_TICKS)

    private fun decide(agent: Agent, near: List<Player>, hp: Float) {
        decidedAt = ticks
        group = near.mapTo(HashSet()) { it.scoreboardName }
        // The opponent farthest from its closest ally
        val apart = near.maxByOrNull { other ->
            near.filter { it !== other }.minOfOrNull { it.distanceTo(other) } ?: 0f
        }
        // Low on health it retreats, and keeps doing so until healed or the group stayed away; losing the trade with
        // health left, it takes on the one standing apart
        val retreating = plan as? OutnumberedPlan.Retreat
        val retreat = if (retreating != null || hp < LOW_HP) retreatPoint(agent, near) ?: retreating?.point else null
        val next = when {
            retreat != null -> OutnumberedPlan.Retreat(retreat)
            apart != null -> OutnumberedPlan.Focus(apart)
            else -> return end(agent, "no way out")
        }
        if (describe(next) != describe(plan)) {
            agent.listener.decision(
                "outnumbered", near.size.toDouble(), "hp=%.1f -> %s".format(Locale.ROOT, hp, describe(next)),
            )
        }
        plan = next
    }

    private fun describe(plan: OutnumberedPlan?) = when (plan) {
        is OutnumberedPlan.Retreat -> "retreat"
        is OutnumberedPlan.Focus -> "focus ${plan.target.scoreboardName}"
        null -> null
    }

    private fun end(agent: Agent, reason: String) {
        if (plan != null) agent.listener.decision("outnumbered over", 0.0, reason)
        stop()
    }

    /**
     * A spot [RETREAT_DISTANCE] away from [near], reached over ground; the way turns aside where it is not.
     */
    public fun retreatPoint(agent: Agent, near: List<Player>): Vec3? {
        if (near.isEmpty()) return null
        val self = agent.body.player
        val position = self.position()
        val centerX = near.sumOf { it.x } / near.size
        val centerZ = near.sumOf { it.z } / near.size
        val away = atan2(position.z - centerZ, position.x - centerX)
        for (offset in RETREAT_OFFSETS) {
            val angle = away + Math.toRadians(offset)
            val dx = cos(angle)
            val dz = sin(angle)
            if ((1..RETREAT_DISTANCE).all { step ->
                hasGround(self, position.x + dx * step, position.y, position.z + dz * step)
            }) {
                return Vec3(position.x + dx * RETREAT_DISTANCE, position.y, position.z + dz * RETREAT_DISTANCE)
            }
        }
        return null
    }

    /**
     * Ground within [GROUND_DEPTH] blocks below, so the way does not lead over a void edge.
     */
    private fun hasGround(self: Player, x: Double, y: Double, z: Double): Boolean {
        val box = AABB(x - HALF_WIDTH, y - GROUND_DEPTH, z - HALF_WIDTH, x + HALF_WIDTH, y, z + HALF_WIDTH)
        return self.level().getBlockCollisions(self, box).iterator().hasNext()
    }

    private companion object {
        const val MIN_GROUP = 2
        const val GROUP_RANGE = 6.0
        const val LOW_HP = 12f
        const val HEALED_HP = 16f
        const val TRADE_TICKS = 60
        const val REVIEW_TICKS = 20
        const val TRIGGER_TICKS = 20
        const val CLEAR_RANGE = 10.0
        const val CLEAR_TICKS = 60
        const val RETREAT_DISTANCE = 8
        val RETREAT_OFFSETS = doubleArrayOf(0.0, 45.0, -45.0, 90.0, -90.0)
        const val GROUND_DEPTH = 3.0
        const val HALF_WIDTH = 0.3
    }
}
