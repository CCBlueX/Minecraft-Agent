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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.world.block.VoxelShape

/**
 * Height the feet of a body [bodyHeight] tall rest at when standing in block [x] [y] [z], uncached; NaN when
 * nothing carries it there or it does not fit.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L343-L356)
 *
 * @agent claude-opus-5-5
 */
internal fun Terrain.computeStandHeight(x: Int, y: Int, z: Int, bodyHeight: Double): Double {
    if (y > maxY || !isKnown(x, y, z) || !isKnown(x, y - 1, z)) {
        return Double.NaN
    }

    val below = shape(x, y - 1, z)
    val at = shape(x, y, z)
    if (at === FULL || below.isEmpty && at.isEmpty || isHazard(x, y - 1, z) || isHazard(x, y, z)) {
        return Double.NaN
    }

    val top = if (below === FULL && at.isEmpty) y.toDouble() else partialTop(below, at, y, width / 2.0)
    return if (top.isNaN() || !isClear(box(x + 0.5, top, z + 0.5, bodyHeight))) Double.NaN else top
}

/**
 * Whether the block at the feet [x] [y] [z] carries them on its own floor inside walls higher than a step, as a
 * composter or a cauldron does: the way out takes a jump, and a walk that ends in one stays there.
 *
 * @agent claude-opus-5-5
 */
internal fun Terrain.isBowl(x: Int, y: Int, z: Int): Boolean {
    val at = shape(x, y, z)
    if (at.isEmpty || at === FULL) {
        return false
    }
    val top = standHeight(x, y, z)
    return top > y && at.toAabbs().any { y + it.maxY > top + Terrain.STEP_HEIGHT }
}

/**
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L358-L382)
 *
 * @agent claude-opus-5-5
 */
private fun partialTop(below: VoxelShape, at: VoxelShape, y: Int, halfWidth: Double): Double {
    var top = Double.NaN
    for (box in below.toAabbs()) {
        val boxTop = y - 1 + box.maxY
        if (box.overlapsFootprint(halfWidth) && boxTop >= y && boxTop < y + 1 && !(boxTop <= top)) {
            top = boxTop
        }
    }

    for (box in at.toAabbs()) {
        if (!box.overlapsFootprint(halfWidth)) {
            continue
        }

        val boxTop = y + box.maxY
        if (boxTop >= y + 1) {
            return Double.NaN
        }
        if (!(boxTop <= top)) {
            top = boxTop
        }
    }

    return top
}

/**
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L384-L385)
 *
 * @agent claude-opus-5-5
 */
private fun AABB.overlapsFootprint(halfWidth: Double) =
    minX < 0.5 + halfWidth && maxX > 0.5 - halfWidth && minZ < 0.5 + halfWidth && maxZ > 0.5 - halfWidth
