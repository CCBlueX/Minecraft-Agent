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

package net.ccbluex.agent.pathfinder.terrain

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Avatar
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import kotlin.math.ceil

private const val MAX_REACH_POSITIONS = 8

/**
 * Blocks below the box the feet may still stand at, as the eyes are above them.
 */
private const val EYE_DEPTH = 2

/**
 * Where on each axis of a box its sample points lie, inset so a ray to one ends inside the box.
 */
private val SAMPLES = doubleArrayOf(0.05, 0.275, 0.5, 0.725, 0.95)

/**
 * Standable feet positions whose eyes, [eyeHeight] above the feet, see [box] within [reach], closest to [from]
 * first; [block] does not hide it. Without [requiresSupport] feet in the air count too, where the body fits.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L491-L512)
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public fun Terrain.reachPositions(
    box: AABB,
    reach: Double,
    from: Vec3,
    block: BlockPos? = null,
    eyeHeight: Double = Avatar.DEFAULT_EYE_HEIGHT.toDouble(),
    requiresSupport: Boolean = true,
): List<BlockPos> {
    val center = BlockPos.containing(box.center)
    val radius = ceil(reach).toInt()
    val positions = ArrayList<BlockPos>()

    for (dx in -radius..radius) {
        for (dz in -radius..radius) {
            for (dy in -radius - EYE_DEPTH..radius) {
                val position = center.offset(dx, dy, dz)
                val feet = feetAt(position, requiresSupport) ?: continue
                val eyes = feet.add(0.0, eyeHeight, 0.0)
                if (!this.box(feet).intersects(box) && box.distanceToSqr(eyes) <= reach * reach &&
                    sees(eyes, box, reach, block)
                ) {
                    positions += position
                }
            }
        }
    }

    positions.sortBy { feetAt(it, requiresSupport)?.distanceToSqr(from) ?: Double.MAX_VALUE }
    return positions.take(MAX_REACH_POSITIONS)
}

/**
 * Where the feet are at [position]: standing on something, or without [requiresSupport] the bottom of the block when
 * the body fits there.
 *
 * @agent claude-sonnet-5-5
 */
private fun Terrain.feetAt(position: BlockPos, requiresSupport: Boolean): Vec3? {
    feet(position)?.let { return it }
    return if (requiresSupport) null else Vec3.atBottomCenterOf(position).takeIf { isClear(box(it)) }
}

/**
 * Whether [eyes] see a point of [box] within [reach]: with [block], a ray to it that ends in that block first, else
 * one no collision shape stops. Eyes inside the box see it.
 *
 * @agent claude-opus-5-5
 */
internal fun Terrain.sees(eyes: Vec3, box: AABB, reach: Double, block: BlockPos?): Boolean {
    if (box.contains(eyes)) {
        return true
    }

    val shapes = if (block != null) ClipContext.Block.OUTLINE else ClipContext.Block.COLLIDER
    for (fx in SAMPLES) {
        for (fy in SAMPLES) {
            for (fz in SAMPLES) {
                val point = Vec3(
                    Mth.lerp(fx, box.minX, box.maxX),
                    Mth.lerp(fy, box.minY, box.maxY),
                    Mth.lerp(fz, box.minZ, box.maxZ),
                )
                if (eyes.distanceToSqr(point) > reach * reach) {
                    continue
                }

                val hit = level.clip(ClipContext(eyes, point, shapes, ClipContext.Fluid.NONE, CollisionContext.empty()))
                val visible = if (block != null) {
                    hit.type == HitResult.Type.BLOCK && hit.blockPos == block
                } else {
                    hit.type == HitResult.Type.MISS
                }
                if (visible) {
                    return true
                }
            }
        }
    }
    return false
}
