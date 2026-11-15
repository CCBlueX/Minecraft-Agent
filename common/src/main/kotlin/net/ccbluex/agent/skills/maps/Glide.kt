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
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * One glide through open space, as down a cave: steers along the course it knows, else towards the most open way
 * ahead, and lands once the way ends or the map says it is safe to. Every touch of a wall, ceiling or floor is told
 * to [touched].
 *
 * shortcut: steers on its own by rays; the library navigator's cave glide takes over once it has one.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Glide(private val changes: Changes, private val touched: (Vec3, String) -> Unit) {

    /**
     * The points flown, every [POINT_TICKS] ticks.
     */
    val points = ArrayList<KnownMap.Point>()

    var ticks = 0
        private set

    /**
     * Glides until the player is on the ground or dead; [course] is a known one to follow.
     */
    suspend fun fly(agent: Agent, course: KnownMap.Course?) {
        val player = agent.body.player
        var heading = ViewRotation(player.yRot, 20f)
        var touchedAt = -TOUCH_TICKS
        while (player.isAlive && player.isFallFlying) {
            if (ticks % POINT_TICKS == 0) points += KnownMap.Point.of(player.position())
            if (ticks % STEER_TICKS == 0) {
                heading = steer(player, heading, course)
            }
            agent.body.look(heading, LookPurpose.STEER)
            if ((player.horizontalCollision || player.verticalCollision) && ticks - touchedAt >= TOUCH_TICKS) {
                touchedAt = ticks
                touched(player.position(), if (player.horizontalCollision) "wall" else if (player.deltaMovement.y > 0) "ceiling" else "floor")
            }
            ticks++
            waitTicks(1)
        }
        points += KnownMap.Point.of(player.position())
    }

    private fun steer(player: Player, heading: ViewRotation, course: KnownMap.Course?): ViewRotation {
        val eye = player.eyePosition
        val landing = changes.lastChat?.lowercase()?.let { LAND.containsMatchIn(it) } == true
        val target = course?.let { ahead(it, player.position()) }
        val toward = target?.let { ViewRotation.fromVector(it.subtract(eye)) }
        var best = heading
        var bestScore = Double.NEGATIVE_INFINITY
        for (dYaw in -YAW_SPREAD..YAW_SPREAD step YAW_STEP) for (xRot in MIN_X_ROT..MAX_X_ROT step X_ROT_STEP) {
            val candidate = ViewRotation(Mth.wrapDegrees((toward ?: heading).yRot + dYaw), xRot.toFloat())
            val free = freeDistance(player, eye, candidate.viewVector)
            // Open space first, then little turning, a steady dive for speed, and down once landing is allowed
            var score = free - abs(dYaw) * TURN_COST - abs(xRot - CRUISE_X_ROT) * PITCH_COST
            if (toward != null) score -= abs(xRot - toward.xRot) * PITCH_COST
            if (landing) score += xRot * PITCH_COST * 2
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /**
     * How far the player's box flies along [direction] before it hits a block, up to [SIGHT] blocks.
     */
    private fun freeDistance(player: Player, eye: Vec3, direction: Vec3): Double {
        val side = direction.cross(Vec3(0.0, 1.0, 0.0)).normalize().scale(BOX)
        val up = Vec3(0.0, BOX, 0.0)
        return listOf(Vec3.ZERO, side, side.reverse(), up, up.reverse()).minOf { offset ->
            val from = eye.add(offset)
            val hit = player.level().clip(ClipContext(from, from.add(direction.scale(SIGHT)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player))
            if (hit.type == HitResult.Type.MISS) SIGHT else hit.location.distanceTo(from)
        }
    }

    /**
     * The course point [AHEAD] blocks past the one nearest [position].
     */
    private fun ahead(course: KnownMap.Course, position: Vec3): Vec3? {
        val points = course.points.map { it.toVec3() }
        val nearest = points.indices.minByOrNull { points[it].distanceTo(position) } ?: return null
        var travelled = 0.0
        for (i in nearest + 1 until points.size) {
            travelled += points[i].distanceTo(points[i - 1])
            if (travelled >= AHEAD) return points[i]
        }
        return points.lastOrNull()
    }

    private companion object {
        const val POINT_TICKS = 10
        const val STEER_TICKS = 2
        const val TOUCH_TICKS = 10
        const val SIGHT = 64.0
        const val AHEAD = 24.0

        /**
         * Half the player's width plus a margin, for the rays at the sides of its box.
         */
        const val BOX = 0.8
        const val YAW_SPREAD = 60
        const val YAW_STEP = 10
        const val MIN_X_ROT = -20
        const val MAX_X_ROT = 60
        const val X_ROT_STEP = 10
        const val CRUISE_X_ROT = 15
        const val TURN_COST = 0.15
        const val PITCH_COST = 0.1
        val LAND = Regex("""safe to land|landing gear|land now""")
    }
}
