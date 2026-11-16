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

package net.ccbluex.agent.navigation.glide

import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.AABB

/**
 * Tells the listener the blocks the fall-flying body touched in its last move, each once while it keeps touching it.
 *
 * @agent claude-opus-5-5
 */
internal class Contacts(private val context: NavigationContext) {

    private var touching = emptySet<Touch>()

    fun tick() {
        val player = context.body.player
        if (!player.isFallFlying || !player.horizontalCollision && !player.verticalCollision) {
            touching = emptySet()
            return
        }

        val now = HashSet<Touch>()
        for (direction in Direction.entries) {
            val hit = when (direction) {
                Direction.DOWN -> player.verticalCollisionBelow
                Direction.UP -> player.verticalCollision && !player.verticalCollisionBelow
                else -> player.horizontalCollision
            }
            if (hit) {
                touchedBlock(player.boundingBox, direction)?.let { now += Touch(it, direction) }
            }
        }

        for (touch in now) {
            if (touch !in touching) {
                context.listener.touched(touch.pos, touch.direction)
            }
        }
        touching = now
    }

    /**
     * The block right beyond the [direction] face of [box] that it rests against, if any.
     */
    private fun touchedBlock(box: AABB, direction: Direction): BlockPos? {
        val level = context.body.level
        val face = box.expandTowards(direction.stepX * PROBE, direction.stepY * PROBE, direction.stepZ * PROBE)
            .intersect(box.move(direction.stepX * PROBE * 2, direction.stepY * PROBE * 2, direction.stepZ * PROBE * 2))
        for (x in Mth.floor(face.minX)..Mth.floor(face.maxX)) {
            for (y in Mth.floor(face.minY)..Mth.floor(face.maxY)) {
                for (z in Mth.floor(face.minZ)..Mth.floor(face.maxZ)) {
                    val pos = BlockPos(x, y, z)
                    val shape = level.getBlockState(pos).getCollisionShape(level, pos)
                    if (shape.toAabbs().any { it.move(pos).intersects(face) }) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    private data class Touch(val pos: BlockPos, val direction: Direction)

    private companion object {
        const val PROBE = 1.0E-3
    }

}
