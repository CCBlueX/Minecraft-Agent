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

import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Step
import net.minecraft.core.BlockPos

/**
 * How far past the middle of a ladder or vine the climb pushes, into the block it hangs on.
 */
private const val GRIP_REACH = 1.0

/**
 * Pushes against the side the ladder or vine hangs on, which climbs on every version; jumping only from 1.14.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L400-L427)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun NavigationContext.climbUp(keys: MovementKeys, frameYRot: Float, step: Step): Movement {
    val position = body.player.position()
    val terrain = Terrain.live(body.level)
    val feet = BlockPos.containing(position)
    val cell = if (terrain.isClimbable(feet.x, feet.y, feet.z)) feet else step.from
    val side = terrain.climbSide(cell.x, cell.y, cell.z)
    val aim = if (side == null) step.feet else step.feet.add(side.stepX * GRIP_REACH, 0.0, side.stepZ * GRIP_REACH)

    keys.directionalInput = centerInput(position, aim, frameYRot)
    keys.jump = true
    keys.sneak = false
    return Movement(aim)
}
