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

package net.ccbluex.agent.skills.games

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What the straight way from one point to another looks like to a planner: its length, the climb, the columns with
 * nothing to land on and the floor cells a bridge would need. Unloaded blocks count as solid, so only what was seen
 * counts as void.
 *
 * From LiquidBounce: [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L461-L487), [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L971-L973), [Plan.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Plan.kt#L173-L193)
 *
 * @agent claude-opus-5-5
 */
internal class WayCost(
    val distance: Double,
    val climb: Double,
    /**
     * Columns of the way, one a block, with nothing within [VOID_DEPTH] below.
     */
    val voidColumns: Int,
    /**
     * The longest stretch of the way, in blocks, over such columns.
     */
    val longestVoid: Double,
    /**
     * Floor cells at the starting height without a block, which a level bridge has to place.
     */
    val bridgeBlocks: Int,
) {

    /**
     * The way's cost in score points: per block, per block up and per void column.
     */
    val value: Double
        get() = distance * DISTANCE_COST + climb * CLIMB_COST + voidColumns * VOID_COST

    companion object {
        const val DISTANCE_COST = 0.25
        const val CLIMB_COST = 0.5
        const val VOID_COST = 1.0
        const val VOID_DEPTH = 40.0

        private const val MAX_SAMPLES = 160
        private const val VOID_SPACING = 0.5
        private const val HALF_WIDTH = 0.3

        fun of(terrain: Terrain, from: Vec3, to: Vec3): WayCost {
            val distance = from.horizontalDistanceTo(to)
            return WayCost(
                distance = from.distanceTo(to),
                climb = (to.y - from.y).coerceAtLeast(0.0),
                voidColumns = voidColumns(terrain, from, to, distance),
                longestVoid = longestVoid(terrain, from, to, distance),
                bridgeBlocks = bridgeBlocks(terrain, from, to, distance),
            )
        }

        /**
         * Nothing to land on within [VOID_DEPTH] below [top] at [x] [z], for a body [HALF_WIDTH] wide.
         */
        fun isVoidBelow(terrain: Terrain, x: Double, top: Double, z: Double): Boolean =
            terrain.isClear(AABB(x - HALF_WIDTH, top - VOID_DEPTH, z - HALF_WIDTH, x + HALF_WIDTH, top, z + HALF_WIDTH))

        private fun voidColumns(terrain: Terrain, from: Vec3, to: Vec3, distance: Double): Int {
            val samples = min(floor(distance).toInt(), MAX_SAMPLES)
            var count = 0
            for (sample in 1..samples) {
                val t = sample / distance
                if (isVoidBelow(terrain, lerp(from.x, to.x, t), lerp(from.y, to.y, t) + 1.0, lerp(from.z, to.z, t))) {
                    count++
                }
            }
            return count
        }

        private fun longestVoid(terrain: Terrain, from: Vec3, to: Vec3, distance: Double): Double {
            val samples = min(ceil(distance / VOID_SPACING).toInt(), MAX_SAMPLES * 2)
            val step = distance / samples.coerceAtLeast(1)
            var run = 0.0
            var longest = 0.0
            for (sample in 1..samples) {
                val t = sample.toDouble() / samples
                if (isVoidBelow(terrain, lerp(from.x, to.x, t), lerp(from.y, to.y, t) + 1.0, lerp(from.z, to.z, t))) {
                    run += step
                    longest = max(longest, run)
                } else {
                    run = 0.0
                }
            }
            return longest
        }

        private fun bridgeBlocks(terrain: Terrain, from: Vec3, to: Vec3, distance: Double): Int {
            val floorY = Mth.floor(from.y) - 1
            val samples = min(ceil(distance).toInt(), MAX_SAMPLES)
            var lastX = Mth.floor(from.x)
            var lastZ = Mth.floor(from.z)
            var count = 0
            for (sample in 1..samples) {
                val t = sample.toDouble() / samples
                val x = Mth.floor(lerp(from.x, to.x, t))
                val z = Mth.floor(lerp(from.z, to.z, t))
                if (x == lastX && z == lastZ) {
                    continue
                }
                lastX = x
                lastZ = z
                if (terrain.isPassable(x, floorY, z)) {
                    count++
                }
            }
            return count
        }

        private fun lerp(from: Double, to: Double, t: Double) = from + (to - from) * t
    }
}
