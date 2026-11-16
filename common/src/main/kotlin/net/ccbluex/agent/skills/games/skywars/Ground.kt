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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.terrain.Terrain

/**
 * The first spot with open air at the feet and a solid block below, looking down the column of [x], [z] from [y] at
 * most [depth] blocks; null over the void or unknown ground.
 *
 * @agent claude-sonnet-5-5
 */
internal fun Terrain.groundUnder(x: Int, y: Int, z: Int, depth: Int): Vec3? {
    var feetY = y
    repeat(depth) {
        if (feetY <= minY) return null
        if (!isPassable(x, feetY - 1, z) && isPassable(x, feetY, z)) {
            return Vec3(x + 0.5, feetY.toDouble(), z + 0.5)
        }
        feetY--
    }
    return null
}

/**
 * The ground of the island whose cage hangs at [spawn]: the first ground under it, which is the cage's own floor
 * until the cage drops.
 *
 * @agent claude-sonnet-5-5
 */
internal fun Terrain.islandGround(spawn: Vec3): Vec3? =
    groundUnder(Mth.floor(spawn.x), Mth.floor(spawn.y), Mth.floor(spawn.z), ISLAND_SEARCH)

private const val ISLAND_SEARCH = 60
