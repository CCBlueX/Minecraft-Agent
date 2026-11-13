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
import net.ccbluex.agent.pathfinder.walk.Step
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags

/**
 * Steps under water and coming up for air on the way.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L463-L497)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal class Swimming(private val context: NavigationContext) {

    private val player get() = context.body.player
    private var breathing = false

    /**
     * Sprinting under water swims in the pose that also fits through gaps one block high.
     */
    fun swim(keys: MovementKeys, frameYRot: Float, step: Step): Movement {
        val position = player.position()
        val target = step.feet.add(0.0, SWIM_LEVEL, 0.0)
        val rise = target.y - position.y

        keys.directionalInput = centerInput(position, target, frameYRot)
        keys.jump = rise > SWIM_TOLERANCE
        keys.sneak = rise < -SWIM_TOLERANCE
        return Movement(target, sprint = true, dive = true)
    }

    fun isShortOfAir() = !player.isEyeInFluid(FluidTags.WATER) && player.airSupply < player.maxAirSupply

    /**
     * Whether to come up for air and stay until it is full, which a long swim close under the surface needs.
     */
    fun breathes(): Boolean {
        breathing = when {
            !player.isInWater || player.airSupply >= player.maxAirSupply -> false
            player.airSupply < player.maxAirSupply * LOW_AIR -> isAirAbove()
            else -> breathing
        }
        return breathing
    }

    private fun isAirAbove(): Boolean {
        val head = BlockPos.containing(player.eyePosition)
        val chunk = context.body.level.getChunk(head)
        for (dy in 0..AIR_SEARCH) {
            val pos = head.above(dy)
            val state = chunk.getBlockState(pos)
            if (state.fluidState.isEmpty) {
                return state.getCollisionShape(chunk, pos).isEmpty
            }
        }
        return false
    }

    private companion object {
        /**
         * Above the feet block: the middle of the room a swimming player has in a gap one block high.
         */
        const val SWIM_LEVEL = 0.2
        const val SWIM_TOLERANCE = 0.15
        const val LOW_AIR = 0.3
        const val AIR_SEARCH = 4
    }
}
