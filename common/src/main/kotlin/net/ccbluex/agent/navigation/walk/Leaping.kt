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

import net.ccbluex.agent.math.getDirectionalInputForYRot
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.pathfinder.walk.Jump
import net.ccbluex.agent.pathfinder.walk.Leap
import net.ccbluex.agent.pathfinder.walk.Step

/**
 * Presses the keys [leap] picks this tick for [jump] of [step], relative to [frameYRot], simulating the player at the
 * speed [stride] measured rather than the one the route was searched with.
 *
 * From LiquidBounce: [Leap.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Leap.kt#L55-L64)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
@Suppress("LongParameterList")
internal fun NavigationContext.leap(
    keys: MovementKeys,
    frameYRot: Float,
    step: Step,
    jump: Jump,
    abilities: WalkAbilities,
    leap: Leap,
    stride: Stride,
): Movement {
    val terrain = Terrain.live(body.level)
    val player = body.player
    val input = leap.tick(player, terrain, body.movementYRot, step, jump, stride.abilities(abilities, player))
    val silentYRot = body.movementYRot.takeIf { it != frameYRot }
    keys.directionalInput = getDirectionalInputForYRot(input.forward, input.strafe, frameYRot, silentYRot)
    keys.jump = input.jump
    keys.sneak = input.shift
    return Movement(leap.target, leap.sprint)
}
