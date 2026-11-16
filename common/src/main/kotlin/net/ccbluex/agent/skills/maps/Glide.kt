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

package net.ccbluex.agent.skills.maps

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.math.Vec3
import kotlin.math.abs

/**
 * One glide through open space, as down a cave, flown by the navigator: along the [course] it knows, else far
 * along the most open way, picked anew as the cave turns; once the map says it is safe to land, or the way ends,
 * down onto the nearest ground ahead.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Glide(private val changes: Changes) {

    /**
     * The points flown, every [POINT_TICKS] ticks.
     */
    val points = ArrayList<KnownMap.Point>()

    var ticks = 0
        private set

    /**
     * Where the glide lands, once it does; touches near it are the touch-down.
     */
    var landing: Vec3? = null
        private set

    /**
     * Glides until the player is on the ground or dead. A goal stays until the player comes near it or the way
     * turns away from it, as every new one is a new route for the navigator.
     */
    suspend fun fly(agent: Agent, course: KnownMap.Course?) {
        val player = agent.body.player
        val navigator = agent.navigator
        var goal: Vec3? = null
        var heading = player.deltaMovement.horizontal().takeIf { it.lengthSqr() > 1e-4 }?.normalize() ?: player.lookAngle.horizontal().normalize()
        while (player.isAlive && player.isFallFlying) {
            if (ticks % POINT_TICKS == 0) points += KnownMap.Point.of(player.position())
            val landHere = landing == null && changes.recentChat(LAND_TICKS).any { LAND.containsMatchIn(it.lowercase()) }
            if (landHere || landing == null && ticks % GOAL_TICKS == 0) {
                val position = player.position()
                val next = when {
                    landHere -> ground(player, heading)?.also { landing = it }
                    course != null -> ahead(course, position)
                    else -> openWay(player, heading)
                }?.let { if (it == landing) it else aloft(player, it) }
                val current = goal
                val due = current == null || next == landing || current.distanceTo(position) < NEAR_GOAL ||
                    next != null && angle(next.subtract(position), current.subtract(position)) > TURNED
                if (next != null && due) {
                    goal = next
                    heading = next.subtract(position).horizontal().takeIf { it.lengthSqr() > 1e-4 }?.normalize() ?: heading
                    navigator.moveTo(next, if (next == landing) LANDING_RADIUS else PASS_RADIUS)
                }
            }
            ticks++
            waitTicks(1)
        }
        points += KnownMap.Point.of(player.position())
    }

    /**
     * The course point [AHEAD] blocks past the one nearest [position]; its end, where it landed, once that is closer.
     */
    private fun ahead(course: KnownMap.Course, position: Vec3): Vec3? {
        val points = course.points.map { it.toVec3() }
        val nearest = points.indices.minByOrNull { points[it].distanceTo(position) } ?: return null
        var travelled = 0.0
        for (i in nearest + 1 until points.size) {
            travelled += points[i].distanceTo(points[i - 1])
            if (travelled >= AHEAD) return points[i]
        }
        return course.end.toVec3().also { landing = it }
    }

    /**
     * [goal] lifted off any floor right below it: the navigator lands on a goal with ground, and the map's course
     * kills a player landing before its end.
     */
    private fun aloft(player: Player, goal: Vec3): Vec3 {
        val level = player.level()
        val below = BlockPos.containing(goal).below()
        return if (level.getBlockState(below).getCollisionShape(level, below).isEmpty) goal else goal.add(0.0, LIFT, 0.0)
    }

    private fun angle(a: Vec3, b: Vec3): Double {
        val lengths = a.length() * b.length()
        return if (lengths < 1e-6) 0.0 else Math.toDegrees(Math.acos((a.dot(b) / lengths).coerceIn(-1.0, 1.0)))
    }

    /**
     * A point far along the most open way from the player, not turning back on [heading]: down a shaft at first,
     * along the cave after. Far past what is seen along a cave, as the navigator flies on into what is not loaded
     * yet; short of the floor down a shaft, so the glide turns where the shaft does.
     */
    private fun openWay(player: Player, heading: Vec3): Vec3 {
        val eye = player.eyePosition
        val yaw = ViewRotation.fromVector(heading).yRot
        var best = eye.add(heading.scale(SIGHT))
        var bestScore = Double.NEGATIVE_INFINITY
        for (dYaw in -180 until 180 step YAW_STEP) for (xRot in 0..MAX_X_ROT step X_ROT_STEP) {
            val direction = ViewRotation(yaw + dYaw, xRot.toFloat()).viewVector
            val hit = player.level().clip(ClipContext(eye, eye.add(direction.scale(SIGHT)), ClipContext.Block.COLLIDER, player, ClipContext.Fluid.ANY))
            val free = if (hit.type == HitResult.Type.MISS) SIGHT else hit.location.distanceTo(eye)
            val score = free - abs(dYaw) * TURN_COST
            if (score > bestScore) {
                bestScore = score
                val reach = if (xRot >= STEEP) (free - FLOOR_GAP).coerceAtLeast(MIN_FREE) else free.coerceAtLeast(MIN_FREE) * FAR
                best = eye.add(direction.scale(reach))
            }
        }
        return best
    }

    /**
     * The nearest standing place ahead along [heading], [MIN_LAND] to [SIGHT] blocks off, at most [DROP] below.
     */
    private fun ground(player: Player, heading: Vec3): Vec3? {
        val level = player.level()
        val from = player.position()
        var distance = MIN_LAND
        while (distance <= SIGHT) {
            val column = from.add(heading.scale(distance))
            for (dy in 0..DROP.toInt()) {
                val feet = BlockPos.containing(column.x, from.y - dy, column.z)
                if (!level.getBlockState(feet.below()).isAir && level.getBlockState(feet).isAir && level.getBlockState(feet.above()).isAir &&
                    level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                ) {
                    return Vec3.atBottomCenterOf(feet)
                }
            }
            distance += 1.0
        }
        return null
    }

    private companion object {
        const val POINT_TICKS = 10
        const val GOAL_TICKS = 10
        const val SIGHT = 96.0
        const val MIN_FREE = 8.0
        const val FAR = 4.0
        const val STEEP = 50
        const val FLOOR_GAP = 6.0
        const val AHEAD = 120.0
        const val NEAR_GOAL = 48.0
        const val TURNED = 35.0
        const val LIFT = 3.0
        const val LAND_TICKS = 100
        const val MIN_LAND = 6.0
        const val DROP = 40.0
        const val PASS_RADIUS = 4.0
        const val LANDING_RADIUS = 1.0
        const val YAW_STEP = 15
        const val MAX_X_ROT = 80
        const val X_ROT_STEP = 10
        const val TURN_COST = 0.2
        val LAND = Regex("""safe to land|landing gear|land now""")
    }
}
