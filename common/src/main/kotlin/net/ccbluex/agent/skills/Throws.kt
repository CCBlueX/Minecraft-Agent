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

package net.ccbluex.agent.skills

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs

/**
 * Where to look to throw ender pearls so they land on a spot.
 *
 * @agent claude-opus-5-5
 */
internal object Throws {

    private const val PEARL_SPEED = 1.5
    private const val PEARL_DRAG = 0.99
    private const val PEARL_GRAVITY = 0.03
    private const val SPAWN_BELOW_EYES = 0.1
    private const val MAX_FLIGHT_TICKS = 200
    private const val MIN_X_ROT = -80
    private const val MAX_X_ROT = 80

    /**
     * Turns either side of the way searched for a throw onto any ground, in degrees, and the steps of the search.
     */
    private const val SPREAD = 30
    private const val SPREAD_STEP = 10
    private const val GROUND_X_ROT_STEP = 2

    /**
     * How much bigger than their boxes entities are to a pearl.
     */
    private const val ENTITY_MARGIN = 0.3

    /**
     * How far from the spot aimed at a landing still counts, across and in height.
     */
    private const val LANDING_SLACK = 1.0
    private const val LANDING_HEIGHT = 0.6

    /**
     * Blocks below the point the thrower comes out at that are searched for ground to land on.
     */
    private const val GROUND_DEPTH = 3

    /**
     * Where a pearl puts its thrower, and after how many ticks of flight.
     */
    data class Landing(val at: Vec3, val ticks: Int)

    /**
     * The rotation that brings [player] onto the ground at [to] (feet height) with an ender pearl that none of
     * [entities] is in the way of; of those that do, the one with the shortest flight. Null when no throw lands within
     * [LANDING_SLACK] of it. Aimed from where the eyes are next tick, once a turn to it has gone through.
     */
    fun pearlRotation(world: BlockGetter, player: Player, to: Vec3, entities: List<Entity>): ViewRotation? =
        pearlRotation(world, player.eyePosition.add(player.deltaMovement), motion(player), to, boxes(player, entities))

    fun pearlRotation(
        world: BlockGetter,
        eye: Vec3,
        motion: Vec3,
        to: Vec3,
        obstacles: List<AABB> = emptyList(),
    ): ViewRotation? {
        val yRot = ViewRotation.fromVector(to.subtract(eye)).yRot
        var best: ViewRotation? = null
        var bestTicks = Int.MAX_VALUE
        for (xRot in MIN_X_ROT..MAX_X_ROT) {
            val rotation = ViewRotation(yRot, xRot.toFloat())
            val landing = pearlLanding(world, eye, motion, rotation, to.y, obstacles) ?: continue
            if (landing.ticks < bestTicks && landsAt(landing, to)) {
                best = rotation
                bestTicks = landing.ticks
            }
        }
        return best
    }

    /**
     * Whether a pearl thrown along [rotation] now brings [player] onto the ground at [to].
     */
    fun pearlLandsAt(
        world: BlockGetter,
        player: Player,
        rotation: ViewRotation,
        to: Vec3,
        entities: List<Entity>,
    ): Boolean {
        val obstacles = boxes(player, entities)
        val landing = pearlLanding(world, player.eyePosition, motion(player), rotation, to.y, obstacles) ?: return false
        return landsAt(landing, to)
    }

    private fun landsAt(landing: Landing, to: Vec3): Boolean = abs(landing.at.y - to.y) <= LANDING_HEIGHT &&
        Math.hypot(landing.at.x - to.x, landing.at.z - to.z) <= LANDING_SLACK

    /**
     * The rotation within [SPREAD] degrees of the way to [towards] whose pearl brings [player] onto any ground, the
     * quickest one: for a fall, where coming down on something matters more than where.
     */
    fun pearlToGround(world: BlockGetter, player: Player, towards: Vec3, entities: List<Entity>): ViewRotation? {
        val eye = player.eyePosition.add(player.deltaMovement)
        val motion = motion(player)
        val obstacles = boxes(player, entities)
        val lowest = minOf(player.y, towards.y)
        val yRot = ViewRotation.fromVector(towards.subtract(eye)).yRot
        var best: ViewRotation? = null
        var bestTicks = Int.MAX_VALUE
        for (turn in -SPREAD..SPREAD step SPREAD_STEP) {
            for (xRot in MIN_X_ROT..MAX_X_ROT step GROUND_X_ROT_STEP) {
                val rotation = ViewRotation(yRot + turn, xRot.toFloat())
                val landing = pearlLanding(world, eye, motion, rotation, lowest, obstacles) ?: continue
                if (landing.ticks < bestTicks) {
                    best = rotation
                    bestTicks = landing.ticks
                }
            }
        }
        return best
    }

    /**
     * Whether a pearl thrown along [rotation] now brings [player] onto any ground.
     */
    fun pearlLands(world: BlockGetter, player: Player, rotation: ViewRotation, entities: List<Entity>): Boolean =
        pearlLanding(world, player.eyePosition, motion(player), rotation, player.y, boxes(player, entities)) != null

    /**
     * Follows the pearl as the game moves it, tick by tick into the first block or one of the [obstacles] it hits. The
     * thrower comes out where the pearl was the tick before, so a block only counts by its top face, and either way
     * with ground under that point; the flight ends early once the pearl drops past [lowest] for good.
     */
    fun pearlLanding(
        world: BlockGetter,
        eye: Vec3,
        motion: Vec3,
        rotation: ViewRotation,
        lowest: Double,
        obstacles: List<AABB> = emptyList(),
    ): Landing? {
        var position = Vec3(eye.x, eye.y - SPAWN_BELOW_EYES, eye.z)
        var velocity = rotation.viewVector.scale(PEARL_SPEED).add(motion)
        for (tick in 1..MAX_FLIGHT_TICKS) {
            velocity = Vec3(velocity.x, velocity.y - PEARL_GRAVITY, velocity.z).scale(PEARL_DRAG)
            val next = position.add(velocity)
            val context = ClipContext(position, next, ClipContext.Block.COLLIDER, null)
            val hit = world.clip(context)
            val end = if (hit.type == HitResult.Type.MISS) next else hit.location
            val from = position
            val struck = obstacles.any { it.clip(from, end) != null }
            if (struck || hit.type != HitResult.Type.MISS) {
                if (!struck && hit.direction != Direction.UP) return null
                return groundUnder(world, position)?.let { Landing(it, tick) }
            }
            position = next
            if (velocity.y < 0 && position.y < lowest - GROUND_DEPTH || position.y < world.minY) return null
        }
        return null
    }

    /**
     * The movement the server adds to a throw: the thrower's, without the fall while on the ground.
     */
    private fun motion(player: Player): Vec3 {
        val known = player.knownMovement
        return Vec3(known.x, if (player.onGround()) 0.0 else known.y, known.z)
    }

    private fun boxes(player: Player, entities: List<Entity>): List<AABB> =
        entities.filter { it !== player && it.isAlive }.map { it.boundingBox.inflate(ENTITY_MARGIN) }

    private fun groundUnder(world: BlockGetter, at: Vec3): Vec3? {
        val start = BlockPos.containing(at)
        for (depth in 0..GROUND_DEPTH) {
            val pos = start.below(depth)
            val shape = world.getBlockState(pos).getCollisionShape(world, pos)
            if (shape.isEmpty) continue
            val top = pos.y + shape.max(Direction.Axis.Y)
            return if (top <= at.y + 1e-6) Vec3(at.x, top, at.z) else null
        }
        return null
    }
}
