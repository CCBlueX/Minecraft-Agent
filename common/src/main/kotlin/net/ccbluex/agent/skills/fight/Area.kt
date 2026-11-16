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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3

/**
 * Where a fighter may fight and build: it only picks opponents inside and only places blocks, crystals, anchors and
 * buckets inside. See [Rules.area].
 *
 * @agent claude-opus-5-5
 */
public fun interface Area {

    /**
     * Whether [position] lies inside.
     */
    public fun contains(position: Vec3): Boolean

    /**
     * @agent claude-opus-5-5
     */
    public companion object {

        /**
         * The blocks between two corners, both included, in either order; a position counts by the block it is in.
         */
        @JvmStatic
        public fun box(a: BlockPos, b: BlockPos): Area {
            val x = minOf(a.x, b.x)..maxOf(a.x, b.x)
            val y = minOf(a.y, b.y)..maxOf(a.y, b.y)
            val z = minOf(a.z, b.z)..maxOf(a.z, b.z)
            return Area { position ->
                Mth.floor(position.x) in x && Mth.floor(position.y) in y && Mth.floor(position.z) in z
            }
        }

        /**
         * Every position within [radius] blocks of [centre].
         */
        @JvmStatic
        public fun sphere(centre: Vec3, radius: Double): Area =
            Area { position -> position.distanceToSqr(centre) <= radius * radius }
    }
}
