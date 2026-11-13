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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.terrain.isOpen
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

private const val SAMPLE_SPACING = 0.5
private val SIDE_X = intArrayOf(1, -1, 0, 0)
private val SIDE_Z = intArrayOf(0, 0, 1, -1)

/**
 * Whether walking straight from [from] gets within [stopDistance] of [to], past no drop deeper than [safeFall].
 *
 * From LiquidBounce: [StraightWalk.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/StraightWalk.kt#L35-L60), [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/Moves.kt#L106-L126)
 *
 * @agent claude-opus-5-5
 */
public fun isStraightWalkable(
    terrain: Terrain,
    from: Vec3,
    to: Vec3,
    stopDistance: Double,
    safeFall: Int,
): Boolean {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val length = Mth.length(dx, dz)
    val samples = ceil(length / SAMPLE_SPACING).toInt()

    var level = from.y
    if (terrain.isNearDrop(Mth.floor(from.x), level, Mth.floor(from.z), safeFall)) {
        return false
    }

    for (sample in 1..samples) {
        val t = min(sample * SAMPLE_SPACING, length) / length
        val x = from.x + dx * t
        val z = from.z + dz * t
        level = terrain.groundLevel(x, z, level)
        if (level.isNaN() || terrain.isNearDrop(Mth.floor(x), level, Mth.floor(z), safeFall)) {
            return false
        }
        if (to.distanceToSqr(x, level, z) <= stopDistance * stopDistance) {
            return true
        }
    }

    return false
}

/**
 * Whether a side of column [x] [z] drops deeper than [safeFall] blocks below feet at [level], into hazard or void.
 *
 * From LiquidBounce: [StraightWalk.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/StraightWalk.kt#L69-L78)
 *
 * @agent claude-opus-5-5
 */
private fun Terrain.isNearDrop(x: Int, level: Double, z: Int, safeFall: Int): Boolean {
    val y = Mth.floor(level)
    for (i in SIDE_X.indices) {
        val sideX = x + SIDE_X[i]
        val sideZ = z + SIDE_Z[i]
        if (!isKnown(sideX, y, sideZ)) {
            return true
        }
        val open = isOpen(sideX, y, sideZ) && !isWater(sideX, y, sideZ)
        if (open && !isSafeDrop(sideX, y, sideZ, level, safeFall)) {
            return true
        }
    }
    return false
}

/**
 * From LiquidBounce: [StraightWalk.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/StraightWalk.kt#L85-L95)
 *
 * @agent claude-opus-5-5
 */
private fun Terrain.isSafeDrop(x: Int, y: Int, z: Int, level: Double, safeFall: Int): Boolean {
    for (dropY in y downTo y - safeFall - 1) {
        val stand = standHeight(x, dropY, z)
        when {
            isWater(x, dropY, z) -> return true
            !stand.isNaN() -> return level - stand <= safeFall
            !isPassable(x, dropY, z) -> return false
        }
    }
    return false
}

/**
 * Feet height when standing at [x] [z] within a step of [level], or NaN.
 *
 * From LiquidBounce: [StraightWalk.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/StraightWalk.kt#L98-L106)
 *
 * @agent claude-opus-5-5
 */
private fun Terrain.groundLevel(x: Double, z: Double, level: Double): Double {
    val blockX = Mth.floor(x)
    val blockZ = Mth.floor(z)
    val step = Terrain.STEP_HEIGHT

    for (y in Mth.floor(level + step) downTo Mth.floor(level - step)) {
        val height = standHeight(blockX, y, blockZ)
        if (!height.isNaN() && abs(height - level) <= step) {
            return if (isClear(box(x, height, z))) height else Double.NaN
        }
    }

    return Double.NaN
}
