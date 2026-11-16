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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.entity.Player

private const val CENTER_TOLERANCE = 0.1

/**
 * The keys towards [feet] from [position], none once that close.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L499-L504)
 *
 * @agent claude-fable-5-2
 */
internal fun centerInput(position: Vec3, feet: Vec3, frameYRot: Float): Input =
    if (position.horizontalDistanceTo(feet) > CENTER_TOLERANCE) {
        getDirectionalInputTowards(feet.subtract(position), frameYRot)
    } else {
        Input.EMPTY
    }

/**
 * On the ground, in water or on a ladder: where the walk is in control.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L121-L122)
 *
 * @agent claude-fable-5-2
 */
internal val Player.isStable: Boolean
    get() = onGround() || isInWater || onClimbable()

/**
 * Whether a door, trapdoor or fence gate is open; anything else that took its place counts as open.
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal val BlockState.isOpenOrGone: Boolean
    get() = getOptionalValue(BlockStateProperties.OPEN) ?: true
