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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.skills.Agent
import kotlin.math.abs

/**
 * Walks and bridges an agent to a point through [Agent.walkTo], and tells when a way leads nowhere: no block closer
 * for [STALL_TICKS] (towards a goal that keeps moving, such as a player: not a block moved), or the navigator
 * stranded for [STRANDED_TICKS].
 *
 * From LiquidBounce: [GameBotBedWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBedWars.kt#L181-L186)
 *
 * @agent claude-opus-5-5
 */
internal class Travel {

    enum class Status { ARRIVED, MOVING, BLOCKED }

    private var goal: Vec3? = null
    private var closest = Double.MAX_VALUE
    private var closerAt = 0L

    /**
     * Where the agent stood when it last got closer.
     */
    private var standing: Vec3? = null
    private var strandedTicks = 0

    /**
     * The navigator's stranded ticks from before this goal, null until the next walk reads them.
     */
    private var strandedBefore: Int? = null

    /**
     * Goes to [to], building a bridge where the navigator may; BLOCKED once it gets no closer.
     */
    fun bridgeTo(agent: Agent, to: Vec3, within: Double, tick: Long): Status {
        val status = walk(agent, to, within)
        if (isStalled(agent.body.player.position(), to, tick) && status != Status.ARRIVED) {
            forget()
            return Status.BLOCKED
        }
        return status
    }

    /**
     * Whether the way from [position] to [to] stopped getting anywhere. A goal moved by more than 2 blocks cannot be
     * measured by its distance, so the agent has to move a block itself instead: a hunted player running about
     * must not keep the stall from ever counting while the agent stands at the end of its bridge.
     *
     * @agent claude-opus-5-5
     */
    fun isStalled(position: Vec3, to: Vec3, tick: Long): Boolean {
        val distance = position.distanceTo(to)
        val last = goal
        val moved = last != null && last.distanceTo(to) > 2
        val closer = when {
            last == null -> true
            moved -> standing.let { it == null || position.distanceTo(it) >= 1 }
            else -> distance < closest - 1
        }
        if (moved || closer) {
            goal = to
            closest = distance
        }
        if (closer) {
            closerAt = tick
            standing = position
        }
        return tick - closerAt > STALL_TICKS || strandedTicks >= STRANDED_TICKS
    }

    /**
     * Walks to [to]; BLOCKED once the navigator found no way for a while.
     */
    fun walk(agent: Agent, to: Vec3, within: Double): Status {
        val navigatorTicks = agent.navigator.strandedTicks
        val before = strandedBefore
        val counted = when {
            before == null -> navigatorTicks
            navigatorTicks < before -> 0
            else -> before
        }
        strandedBefore = counted
        strandedTicks = navigatorTicks - counted
        if (isWithin(agent.body.player.position(), to, within)) return Status.ARRIVED
        agent.walkTo(to, within)
        return if (strandedTicks >= STRANDED_TICKS) Status.BLOCKED else Status.MOVING
    }

    fun forget() {
        goal = null
        closest = Double.MAX_VALUE
        strandedTicks = 0
        strandedBefore = null
    }

    companion object {
        const val STALL_TICKS = 200
        const val STRANDED_TICKS = 60

        /**
         * Whether [point] is in reach across the ground, a slab or a step up or down allowed for.
         */
        fun isWithin(from: Vec3, point: Vec3, reach: Double): Boolean =
            from.horizontalDistanceTo(point) <= reach && abs(from.y - point.y) < 2.5
    }
}
