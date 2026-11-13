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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.isStraightWalkable
import net.minecraft.world.phys.Vec3
import kotlin.math.min

private const val SHORTCUT_STOP = 0.25

/**
 * The farthest of the points after step [index] a straight walk from [position] gets to, while every step up to it
 * is a plain walk.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L583-L604)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun Route.shortcut(index: Int, position: Vec3, terrain: Terrain): Vec3? {
    var end = index
    while (end + 1 < min(steps.size, index + SHORTCUT_STEPS) && steps[end + 1].isPlain) {
        end++
    }

    for (i in end downTo index + 1) {
        val point = steps[i].feet
        if (isStraightWalkable(terrain, position, point, SHORTCUT_STOP, abilities.safeFall)) {
            return point
        }
    }
    return null
}
