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

package net.ccbluex.agent.pathfinder.level

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * From LiquidBounce: [BlockRegion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/world/BlockRegion.kt#L27-L79)
 *
 * @agent claude-opus-5-5
 */
public data class BlockRegion(
    public val minX: Int,
    public val minY: Int,
    public val minZ: Int,
    public val maxX: Int,
    public val maxY: Int,
    public val maxZ: Int,
) {

    public operator fun contains(pos: BlockPos): Boolean =
        pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ

    public companion object {

        /**
         * Every block a route of at most [maxLength] from [start] to one of [ends] can pass, at most
         * [verticalMargin] blocks above or below them.
         */
        public fun around(start: Vec3, ends: Collection<Vec3>, maxLength: Double, verticalMargin: Int): BlockRegion {
            var minX = start.x
            var minY = start.y
            var minZ = start.z
            var maxX = start.x
            var maxY = start.y
            var maxZ = start.z
            var closest = Double.MAX_VALUE

            for (end in ends) {
                minX = min(minX, end.x)
                minY = min(minY, end.y)
                minZ = min(minZ, end.z)
                maxX = max(maxX, end.x)
                maxY = max(maxY, end.y)
                maxZ = max(maxZ, end.z)
                closest = min(closest, start.distanceTo(end))
            }

            val detour = if (closest == Double.MAX_VALUE) maxLength else max(0.0, (maxLength - closest) / 2.0)
            val horizontal = detour + EDGE
            val vertical = min(detour, verticalMargin.toDouble()) + EDGE

            return BlockRegion(
                (minX - horizontal).toInt() - 1,
                (minY - vertical).toInt() - 1,
                (minZ - horizontal).toInt() - 1,
                (maxX + horizontal).toInt() + 1,
                (maxY + vertical).toInt() + 1,
                (maxZ + horizontal).toInt() + 1,
            )
        }

        private const val EDGE = 3.0
    }

}
