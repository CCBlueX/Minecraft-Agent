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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.games.FlightSwitch
import net.ccbluex.agent.skills.games.WayCost
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Flies over long void stretches and up to players standing higher with the host's [FlightSwitch], where walking
 * would need a long bridge. Flight stays off while health is low for what flying costs, right after a flight, for
 * goals too far away and where the straight flight would hit something. It flies to the ground nearest the goal
 * ([landingSpot]), ends on the first ground past the void, and heads back to where it started once it takes too long.
 *
 * From LiquidBounce: [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L756-L795)
 *
 * @agent claude-opus-5-5
 */
internal class Crossing(private val switch: FlightSwitch, private val options: Options) {

    private class Flight(val origin: Vec3, var goal: Vec3, val startedAt: Long, val startY: Double) {
        var airborne = false
        var crossedVoid = false
    }

    private var flight: Flight? = null
    private var endedAt = Long.MIN_VALUE / 2
    private var checkAt = 0L
    private var decision: String? = null

    val isFlying: Boolean
        get() = flight != null

    /**
     * One tick towards [goal]; true while a flight carries the agent there, which walks to it itself.
     */
    fun tick(agent: Agent, terrain: Terrain, goal: Vec3, toOpponent: Boolean, now: Long): Boolean {
        flight?.let { return fly(agent, terrain, it, now) }
        if (!wants(agent, terrain, goal, toOpponent, now)) return false

        val player = agent.body.player
        val landing = landingSpot(terrain, goal) ?: goal
        flight = Flight(player.position(), landing, now, player.y)
        switch.fly(true)
        agent.walkTo(landing, ARRIVAL_RADIUS)
        return true
    }

    /**
     * Flies on to where the flight was going, for when what it went for is gone.
     */
    fun land(agent: Agent, terrain: Terrain, now: Long) {
        flight?.let { fly(agent, terrain, it, now) }
    }

    fun stop(agent: Agent, reason: String, now: Long) {
        if (flight == null) return
        flight = null
        endedAt = now
        switch.fly(false)
        agent.logger.debug("Flight ended at {}: {}", agent.body.player.position(), reason)
    }

    private fun wants(agent: Agent, terrain: Terrain, goal: Vec3, toOpponent: Boolean, now: Long): Boolean {
        val player = agent.body.player
        if (now < checkAt || !player.onGround()) return false
        checkAt = now + CHECK_TICKS

        val position = player.position()
        val void = WayCost.of(terrain, position, goal).longestVoid
        val climb = goal.y - position.y
        val wants = when {
            void > VOID_CROSSING -> "void=%.1f".format(java.util.Locale.ROOT, void)
            toOpponent && climb > HIGHER_GROUND -> "higher=%.1f".format(java.util.Locale.ROOT, climb)
            else -> return false
        }
        val blocker = when {
            player.health < MIN_HEALTH -> "hp"
            now < endedAt + COOLDOWN_TICKS -> "cooldown"
            position.horizontalDistanceTo(goal) > options.flyMaxDistance -> "distance"
            !canFlyTo(terrain, position, goal) -> "path"
            else -> null
        }
        val way = if (blocker == null) "fly" else "bridge"
        val key = "$way $blocker"
        if (key != decision) {
            decision = key
            agent.decide("travel via $way", 0.0, "${blocker ?: "clear"} $wants")
        }
        return blocker == null
    }

    /**
     * Whether the flight hits nothing on the way, rising or sinking to the goal height [HEIGHT_PER_BLOCK] a block.
     */
    private fun canFlyTo(terrain: Terrain, from: Vec3, goal: Vec3): Boolean {
        val distance = from.horizontalDistanceTo(goal)
        val toHeight = goal.y + LANDING_LIFT - (from.y + RISE)
        val box = terrain.box(from)
        val samples = ceil(distance / SAMPLE_SPACING).toInt()
        for (sample in 1..samples) {
            val t = sample.toDouble() / samples
            val heightChange = min(abs(toHeight), HEIGHT_PER_BLOCK * distance * t)
            val rise = RISE + if (toHeight > 0.0) heightChange else -heightChange
            val offset = Vec3((goal.x - from.x) * t, rise, (goal.z - from.z) * t)
            if (!terrain.isClear(box.move(offset))) return false
        }
        return true
    }

    private fun fly(agent: Agent, terrain: Terrain, flight: Flight, now: Long): Boolean {
        val player = agent.body.player
        val position = player.position()
        val overVoid = WayCost.isVoidBelow(terrain, position.x, position.y, position.z)
        val ground = !terrain.isClear(AABB(position.x - 0.3, position.y - LANDING_DEPTH, position.z - 0.3,
            position.x + 0.3, position.y, position.z + 0.3))
        flight.crossedVoid = flight.crossedVoid || overVoid
        flight.airborne = flight.airborne || !player.onGround()
        val near = position.horizontalDistanceTo(flight.goal) <= LANDING_RADIUS
        val elapsed = now - flight.startedAt
        val maxTicks = options.flyMaxSeconds * TICKS_PER_SECOND

        val reason = when {
            flight.airborne && player.onGround() -> "grounded"
            !flight.airborne && elapsed > LIFT_TICKS -> "no lift"
            ground && flight.crossedVoid || near && !overVoid -> "landed"
            player.y < flight.startY - MAX_FALL -> "falling"
            elapsed > 2 * maxTicks -> "timeout"
            else -> null
        }
        if (reason != null) {
            stop(agent, reason, now)
            return false
        }
        if (elapsed > maxTicks && flight.goal != flight.origin) {
            flight.goal = flight.origin
            agent.logger.debug("Flight takes too long, back to {}", flight.origin)
        }
        agent.walkTo(flight.goal, ARRIVAL_RADIUS)
        return true
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
        const val CHECK_TICKS = 10

        /**
         * Void stretches longer than this are flown over, as are climbs to players this much higher.
         */
        const val VOID_CROSSING = 8.0
        const val HIGHER_GROUND = 3.0

        /**
         * Flying costs health on some servers; below this it bridges.
         */
        const val MIN_HEALTH = 6f
        const val COOLDOWN_TICKS = 60
        const val SAMPLE_SPACING = 0.5
        const val RISE = 0.5

        /**
         * Blocks a flight is taken to rise or sink per block it goes, about what fly modes do.
         */
        const val HEIGHT_PER_BLOCK = 0.5
        const val LANDING_LIFT = 0.5
        const val LANDING_DEPTH = 4.0
        const val LANDING_RADIUS = 2.0

        /**
         * Close enough over the landing spot that the player's box is over its ground.
         */
        const val ARRIVAL_RADIUS = 0.4
        const val LIFT_TICKS = 60
        const val MAX_FALL = 4.0
    }
}

/**
 * Where a flight to [goal] sets down: the standable spot nearest [goal] within a few blocks, as a goal such as a
 * player at an island's edge may be over the void a step further on. Null without ground near it.
 *
 * @agent claude-opus-5-5
 */
internal fun landingSpot(terrain: Terrain, goal: Vec3): Vec3? {
    val center = BlockPos.containing(goal)
    var best: BlockPos? = null
    var bestDistance = Double.MAX_VALUE
    for (pos in BlockPos.betweenClosed(
        center.offset(-LANDING_SEARCH, -LANDING_BELOW, -LANDING_SEARCH),
        center.offset(LANDING_SEARCH, LANDING_ABOVE, LANDING_SEARCH),
    )) {
        val distance = Vec3.atBottomCenterOf(pos).distanceToSqr(goal)
        if (distance < bestDistance && terrain.isStandable(pos)) {
            best = pos.immutable()
            bestDistance = distance
        }
    }
    return best?.let(Vec3::atBottomCenterOf)
}

private const val LANDING_SEARCH = 4
private const val LANDING_BELOW = 4
private const val LANDING_ABOVE = 2
