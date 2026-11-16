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
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player

/**
 * What a look from the eyes with [rotation] hits first within [reach], as the game's pick does: block outlines, no
 * fluids.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@InternalAgentApi
public fun Player.pick(rotation: ViewRotation, reach: Double): BlockHitResult {
    val eye = eyePosition
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
