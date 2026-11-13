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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.fma
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.isStraightWalkable
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.min

private const val HEADWAY = 2.0
private const val MIN_HEADWAY = 0.5
private const val HEADWAY_TOLERANCE = 0.75

/**
 * Whether the player stands on ground that goes on plainly for a bit towards [goal], so it can walk on while the first
 * path is searched.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L445-L460)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.hasHeadway(goal: Vec3): Boolean {
    val player = body.player
    if (!player.onGround()) {
        return false
    }

    val position = player.position()
    val delta = Vec3(goal.x - position.x, 0.0, goal.z - position.z)
    val length = delta.length()
    if (length < MIN_HEADWAY) {
        return false
    }

    val ahead = position.fma(min(HEADWAY, length) / length, delta)
    val terrain = Terrain.live(body.level)
    return isStraightWalkable(terrain, position, ahead, HEADWAY_TOLERANCE, safeFall())
}

/**
 * The point of the block the player stands in closest to [goal], if that block is close enough to it. Searches
 * measure from block centers, so they end in such a block before the player itself is close enough.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L466-L479)
 *
 * @agent claude-fable-5-2
 */
internal fun NavigationContext.finalApproach(goal: Vec3, radius: Double): Vec3? {
    val player = body.player
    val position = player.position()
    val feet = BlockPos.containing(position)
    if (!NearDestination(goal, radius).isGoal(feet)) {
        return null
    }

    val margin = player.bbWidth / 2.0
    return Vec3(
        goal.x.coerceIn(feet.x + margin, feet.x + 1.0 - margin),
        position.y,
        goal.z.coerceIn(feet.z + margin, feet.z + 1.0 - margin),
    )
}
