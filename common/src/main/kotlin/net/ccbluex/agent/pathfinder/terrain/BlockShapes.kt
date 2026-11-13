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

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

internal const val EPSILON = 1.0E-7

/**
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L398-L473)
 *
 * @agent claude-opus-5-5
 */
internal val FULL: VoxelShape = Shapes.block()

/**
 * Every block [box] reaches into, and the layer below for blocks taller than a block, like fences.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L447-L455)
 *
 * @agent claude-opus-5-5
 */
internal inline fun forEachBlock(box: AABB, action: (x: Int, y: Int, z: Int) -> Unit) {
    for (x in Mth.floor(box.minX + EPSILON)..Mth.floor(box.maxX - EPSILON)) {
        for (z in Mth.floor(box.minZ + EPSILON)..Mth.floor(box.maxZ - EPSILON)) {
            for (y in Mth.floor(box.minY + EPSILON) - 1..Mth.floor(box.maxY - EPSILON)) {
                action(x, y, z)
            }
        }
    }
}

/**
 * Whether this shape, placed in block [x] [y] [z], intersects [box].
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L464-L472)
 *
 * @agent claude-opus-5-5
 */
internal fun VoxelShape.intersects(x: Int, y: Int, z: Int, box: AABB): Boolean {
    if (this === FULL) {
        return box.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)
    }

    return toAabbs().any {
        box.intersects(it.minX + x, it.minY + y, it.minZ + z, it.maxX + x, it.maxY + y, it.maxZ + z)
    }
}
