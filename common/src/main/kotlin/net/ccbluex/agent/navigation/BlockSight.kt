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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs

/**
 * What a look from [eye] with [rotation] hits first within [reach], as the game's pick does: block outlines, no
 * fluids.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@InternalAgentApi
@JvmOverloads
public fun Player.pick(rotation: ViewRotation, reach: Double, eye: Vec3 = eyePosition): BlockHitResult {
    val end = eye.add(rotation.viewVector.scale(reach))
    return level().clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, this))
}

/**
 * The click on [face] of [pos] that the rotation the server has makes: the pick, if it hits that face of that block
 * first; null when another block is in the way, the look misses it or it is out of reach.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal fun Body.pick(pos: BlockPos, face: Direction): BlockHitResult? {
    // The server's reach for using and digging, a block over the client's pick
    val hit = player.pick(sentRotation, player.blockInteractionRange() + 1.0)
    return hit.takeIf { it.type == HitResult.Type.BLOCK && it.blockPos == pos && it.direction == face }
}

/**
 * Whether no solid block stands between the eyes and [entity], where the rotation the server has meets its hitbox or,
 * when it looks past, at its point nearest to the eyes.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal fun Body.sees(entity: Entity): Boolean {
    val eye = player.eyePosition
    val box = entity.boundingBox.inflate(entity.pickRadius.toDouble())
    val far = eye.add(sentRotation.viewVector.scale(eye.distanceTo(box.center) + (box.xsize + box.ysize + box.zsize) / 3.0))
    val point = box.clip(eye, far)
        ?: Vec3(eye.x.coerceIn(box.minX, box.maxX), eye.y.coerceIn(box.minY, box.maxY), eye.z.coerceIn(box.minZ, box.maxZ))
    val hit = level.clip(ClipContext(eye, point, ClipContext.Block.COLLIDER, player))
    return hit.type == HitResult.Type.MISS
}

/**
 * Whether a look from [eye] at [point] hits the block at [pos] first, on [face] when given, as the game's pick does:
 * through no other block's outline.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun BlockGetter.isSeen(eye: Vec3, point: Vec3, pos: BlockPos, face: Direction?, entity: Entity?): Boolean {
    val past = eye.add(point.subtract(eye).scale(1.0 + SEEN_EPSILON))
    val hit = clip(ClipContext(eye, past, ClipContext.Block.OUTLINE, entity))
    return hit.type == HitResult.Type.BLOCK && hit.blockPos == pos && (face == null || hit.direction == face)
}

/**
 * The point of the block at [pos] within [reach] of [eye] that a look hits first, on [face] when given, turning least
 * from [from]; null when every point of it is hidden behind other blocks or out of reach. The points lie on a grid
 * through the block's outline, as a player may look at any part of it. A body turns in whole [step]s
 * ([Body.viewStep]) and so lands up to half a step beside a point: a point counts only while looks that far off it
 * still hit the block first, as one seen past an edge by a hair is never hit.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun BlockGetter.seenPoint(
    eye: Vec3,
    pos: BlockPos,
    reach: Double,
    face: Direction?,
    from: ViewRotation,
    entity: Entity?,
    step: Float,
): Vec3? {
    val shape = getBlockState(pos).getShape(this, pos)
    val boxes = if (shape.isEmpty) listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)) else shape.toAabbs()
    val seen = ArrayList<Pair<Vec3, Float>>()
    for (box in boxes) {
        for (i in 0 until SEEN_GRID) for (j in 0 until SEEN_GRID) for (k in 0 until SEEN_GRID) {
            val point = Vec3(
                pos.x + Mth.lerp((i + 0.5) / SEEN_GRID, box.minX, box.maxX),
                pos.y + Mth.lerp((j + 0.5) / SEEN_GRID, box.minY, box.maxY),
                pos.z + Mth.lerp((k + 0.5) / SEEN_GRID, box.minZ, box.maxZ),
            )
            if (eye.distanceToSqr(point) > reach * reach || !isSeen(eye, point, pos, face, entity)) continue
            val rotation = ViewRotation.fromVector(point.subtract(eye))
            seen += point to abs(Mth.wrapDegrees(rotation.yRot - from.yRot)) + abs(rotation.xRot - from.xRot)
        }
    }
    seen.sortBy { it.second }
    return seen.firstOrNull { (point, _) -> step <= 0f || isSeenAround(eye, point, pos, face, entity, step / 2f) }
        ?.first
}

/**
 * Whether looks [off] degrees beside the one from [eye] at [point], up, down and to either side, all hit the block at
 * [pos] first, on [face] when given.
 *
 * @agent claude-opus-5-5
 */
private fun BlockGetter.isSeenAround(
    eye: Vec3,
    point: Vec3,
    pos: BlockPos,
    face: Direction?,
    entity: Entity?,
    off: Float,
): Boolean {
    val rotation = ViewRotation.fromVector(point.subtract(eye))
    // Past the point by a block, as a look off to one side may meet the block further back
    val length = eye.distanceTo(point) + 1.0
    return arrayOf(off to 0f, -off to 0f, 0f to off, 0f to -off).all { (yRot, xRot) ->
        val look = ViewRotation(rotation.yRot + yRot, rotation.xRot + xRot).viewVector
        isSeen(eye, eye.add(look.scale(length)), pos, face, entity)
    }
}

/**
 * Points across each side of a block's outline box that [seenPoint] tries.
 */
private const val SEEN_GRID = 5
private const val SEEN_EPSILON = 1.0E-3
