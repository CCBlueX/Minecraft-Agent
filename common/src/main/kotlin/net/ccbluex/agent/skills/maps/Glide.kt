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
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
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
     * Glides until the player is on the ground or dead.
     */
    suspend fun fly(agent: Agent, course: KnownMap.Course?) {
        val player = agent.body.player
        val navigator = agent.navigator
        var goal: Vec3? = null
        var heading = player.deltaMovement.horizontal().takeIf { it.lengthSqr() > 1e-4 }?.normalize() ?: player.lookAngle.horizontal().normalize()
        while (player.isAlive && player.isFallFlying) {
            if (ticks % POINT_TICKS == 0) points += KnownMap.Point.of(player.position())
            val landHere = landing == null && changes.lastChat?.lowercase()?.let { LAND.containsMatchIn(it) } == true &&
                changes.isTalking
            if (landHere || landing == null && ticks % GOAL_TICKS == 0) {
                val next = when {
                    landHere -> ground(player, heading)?.also { landing = it }
                    course != null -> ahead(course, player.position())
                    else -> openWay(player, heading)
                }
                if (next != null && next != goal) {
                    goal = next
                    heading = next.subtract(player.position()).horizontal().takeIf { it.lengthSqr() > 1e-4 }?.normalize() ?: heading
                    navigator.moveTo(next, if (next == landing) LANDING_RADIUS else PASS_RADIUS)
                }
            }
            ticks++
            waitTicks(1)
        }
        points += KnownMap.Point.of(player.position())
    }

    /**
     * The course point [AHEAD] blocks past the one nearest [position]; its end once that is closer.
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
     * A point far along the most open way from the player, not turning back on [heading]: down a shaft at first,
     * along the cave after.
     */
    private fun openWay(player: Player, heading: Vec3): Vec3 {
        val eye = player.eyePosition
        val yaw = ViewRotation.fromVector(heading).yRot
        var best = eye.add(heading.scale(SIGHT))
        var bestScore = Double.NEGATIVE_INFINITY
        for (dYaw in -180 until 180 step YAW_STEP) for (xRot in 0..MAX_X_ROT step X_ROT_STEP) {
            val direction = ViewRotation(yaw + dYaw, xRot.toFloat()).viewVector
            val hit = player.level().clip(ClipContext(eye, eye.add(direction.scale(SIGHT)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player))
            val free = if (hit.type == HitResult.Type.MISS) SIGHT else hit.location.distanceTo(eye)
            val score = free - abs(dYaw) * TURN_COST
            if (score > bestScore) {
                bestScore = score
                best = eye.add(direction.scale(free.coerceAtLeast(MIN_FREE) * FAR))
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
                    level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
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
        const val GOAL_TICKS = 40
        const val SIGHT = 96.0
        const val MIN_FREE = 8.0

        /**
         * How far past the open stretch seen the goal lies, so the navigator flies on into what is not loaded yet.
         */
        const val FAR = 4.0
        const val AHEAD = 60.0
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
