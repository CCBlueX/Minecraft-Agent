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

package net.ccbluex.agent.pathfinder.teleport

import it.unimi.dsi.fastutil.ints.IntArrayList
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.Mechanics
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Feet positions to a destination and the position packets, [steps], that take the server there. Between two points
 * the player rises, crosses at the higher level, then drops.
 *
 * From LiquidBounce: [Route.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/teleport/Route.kt#L34-L210)
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class Route(
    public val points: List<Vec3>,
    public val steps: List<Step>,
    public val mechanics: Mechanics = Mechanics.MODERN,
) {

    /**
     * One position packet, on the part of the route that starts at point [segment].
     */
    public class Step(public val position: Vec3, public val onGround: Boolean, public val segment: Int)

    init {
        require(points.size >= 2) { "A route needs a start and a destination." }
    }

    public val start: Vec3 get() = points.first()

    public val destination: Vec3 get() = points.last()

    /**
     * Corners of the path the player actually takes.
     */
    public val path: List<Vec3> by lazy { corners(points) }

    public val length: Double by lazy { path.zipWithNext(Vec3::distanceTo).sum() }

    /**
     * This route with the way from [position] to its start put in front, or null when that way is blocked.
     */
    public fun startingAt(position: Vec3, terrain: Terrain, stepLength: Double): Route? {
        val linked = ArrayList<Step>(steps.size + 2)
        if (!appendSegment(terrain, position, start, stepLength, 0, linked)) {
            return null
        }

        steps.mapTo(linked) { Step(it.position, it.onGround, it.segment + 1) }
        return Route(listOf(position) + points, linked, mechanics)
    }

    /**
     * How many of [steps] each tick sends, staying inside the server's allowance from where the tick starts.
     */
    public fun batches(packetsPerTick: Int): IntArray {
        val perTick = packetsPerTick.coerceIn(1, mechanics.maxPacketsPerTick)
        val sizes = IntArrayList()
        var tickStart = start
        var inTick = 0

        for ((index, step) in steps.withIndex()) {
            val packet = inTick + 1
            if (inTick > 0 &&
                (packet > perTick || mechanics.isMoveJudgedPerTick && step.position.distanceToSqr(tickStart) >
                    SAFE_DISTANCE * SAFE_DISTANCE * packet.toDouble())
            ) {
                sizes.add(inTick)
                tickStart = steps[index - 1].position
                inTick = 0
            }
            inTick++
        }

        if (inTick > 0) {
            sizes.add(inTick)
        }
        return sizes.toIntArray()
    }

    /**
     * Whether the server takes every step of this route in one tick.
     */
    public val fitsOneTick: Boolean
        get() = batches(mechanics.maxPacketsPerTick).size <= 1

    public companion object {

        // The server adds every descending move to the fall distance, damages a landing after more than three blocks
        // and clears the distance on any rise. The margin covers a fall the player was already in.
        public const val MAX_FALL: Double = 2.5

        /**
         * Rise between two falls of a drop, which clears the fall distance.
         */
        public const val BOUNCE: Double = 0.1

        // A 26.3 server sets the player back when the k-th position packet since its last tick ends more than
        // 10 * sqrt(k) blocks from where that tick started; from the sixth packet on k counts as 1 again. A 1.8 server
        // judges each packet alone: the squared move less the squared motion must stay under 100 (Spigot's
        // moved-too-quickly threshold), so 10 blocks a packet and no limit per tick. See [Mechanics].
        public const val MAX_PACKETS_PER_TICK: Int = 5
        public const val SAFE_DISTANCE: Double = 9.5

        public const val SAME_POSITION_SQ: Double = 1.0E-6
        private const val EPSILON = 1.0E-6

        /**
         * The route through [points] in steps the server's collision sweep lets through; null when one is blocked.
         */
        public fun plan(terrain: Terrain, points: List<Vec3>, stepLength: Double): Route? {
            val steps = ArrayList<Step>()
            for (segment in 0 until points.lastIndex) {
                if (!appendSegment(terrain, points[segment], points[segment + 1], stepLength, segment, steps)) {
                    return null
                }
            }
            return Route(points, steps, terrain.level.mechanics)
        }

        /**
         * One step from [from] to [to], which the server's collision sweep of that move lets through; null when it is
         * blocked or longer than [SAFE_DISTANCE].
         */
        public fun direct(terrain: Terrain, from: Vec3, to: Vec3): Route? {
            if (from.distanceToSqr(to) > SAFE_DISTANCE * SAFE_DISTANCE || !terrain.sweep(from, to)) {
                return null
            }
            return Route(listOf(from, to), listOf(Step(to, terrain.isSupported(to), 0)), terrain.level.mechanics)
        }

        /**
         * Length of the path a route through [points] takes.
         */
        public fun lengthOf(points: List<Vec3>): Double = corners(points).zipWithNext(Vec3::distanceTo).sum()

        private fun corners(points: List<Vec3>) = buildList {
            add(points.first())
            for (index in 0 until points.lastIndex) {
                val from = points[index]
                val to = points[index + 1]
                val top = max(from.y, to.y)

                if (top > from.y) add(from.copy(y = top))
                add(to.copy(y = top))
                if (top > to.y) add(to)
            }
        }

        /**
         * Rises in the column of [from], crosses at the higher level, then drops onto [to]; false when blocked.
         */
        internal fun appendSegment(
            terrain: Terrain,
            from: Vec3,
            to: Vec3,
            stepLength: Double,
            segment: Int,
            out: MutableList<Step>,
        ): Boolean {
            val top = max(from.y, to.y)
            var position = from

            if (top - from.y > EPSILON) {
                position = appendLine(terrain, position, from.copy(y = top), stepLength, segment, out) ?: return false
            }

            position = appendLine(terrain, position, to.copy(y = top), stepLength, segment, out) ?: return false
            return top - to.y <= EPSILON || appendDrop(terrain, position, to, stepLength, segment, out)
        }

        private fun appendLine(
            terrain: Terrain,
            from: Vec3,
            to: Vec3,
            stepLength: Double,
            segment: Int,
            out: MutableList<Step>,
        ): Vec3? {
            val distance = from.distanceTo(to)
            if (distance < EPSILON) {
                return from
            }

            val count = ceil(distance / stepLength).toInt()
            var previous = from
            for (index in 1..count) {
                val next = if (index == count) to else from.lerp(to, index.toDouble() / count)
                if (!terrain.sweep(previous, next)) {
                    return null
                }
                out += Step(next, terrain.isSupported(next), segment)
                previous = next
            }

            return to
        }

        /**
         * Falls of at most [MAX_FALL] blocks with a [BOUNCE] between two of them. Only the landing claims ground.
         */
        private fun appendDrop(
            terrain: Terrain,
            from: Vec3,
            to: Vec3,
            stepLength: Double,
            segment: Int,
            out: MutableList<Step>,
        ): Boolean {
            val fall = min(stepLength, MAX_FALL)
            var position = from
            var fallen = 0.0

            while (position.y - to.y > EPSILON) {
                val remaining = position.y - to.y
                val next = when {
                    fallen + min(fall, remaining) > MAX_FALL + EPSILON -> position.add(0.0, BOUNCE, 0.0)
                    remaining <= fall + EPSILON -> to
                    else -> Vec3(to.x, position.y - fall, to.z)
                }
                if (!terrain.sweep(position, next)) {
                    return false
                }

                fallen = if (next.y > position.y) 0.0 else fallen + position.y - next.y
                out += Step(next, next === to && terrain.isSupported(to), segment)
                position = next
            }

            return true
        }

    }

}
