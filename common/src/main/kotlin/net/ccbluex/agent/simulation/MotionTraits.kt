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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.world.entity.Attributes
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_JUMP_POWER
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_VERTICAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.DEFAULT_BASE_GRAVITY
import net.ccbluex.agent.world.entity.Player

/**
 * What sets how fast a player walks and how high it jumps; [walkingSpeed] is without sprinting.
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L537-L567)
 *
 * @agent claude-opus-5-5
 */
public class MotionTraits(public val walkingSpeed: Float, public val jumpStrength: Float, public val jumpBoostPower: Float) {

    /**
     * The upward motion of a jump from flat ground.
     */
    public val jumpPower: Double = (jumpStrength + jumpBoostPower).toDouble()

    /**
     * How high a jump from flat ground gets the feet.
     */
    public val jumpHeight: Double = rise(jumpPower)

    public companion object {
        @JvmField
        public val DEFAULT: MotionTraits =
            MotionTraits(walkingSpeed = 0.1f, jumpStrength = BASE_JUMP_POWER, jumpBoostPower = 0f)

        /**
         * How high the feet get from moving up at [deltaY] until it is used up.
         */
        @JvmStatic
        public fun rise(deltaY: Double): Double {
            var motion = deltaY
            var height = 0.0
            while (motion > 0.0) {
                height += motion
                motion = (motion - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG
            }
            return height
        }

        /**
         * The traits of [player] now, effects included. Call on the game thread.
         */
        @JvmStatic
        public fun capture(player: Player): MotionTraits {
            val speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat()
            return MotionTraits(
                walkingSpeed = if (player.isSprinting) speed / SPRINT_FACTOR else speed,
                jumpStrength = player.getAttributeValue(Attributes.JUMP_STRENGTH).toFloat(),
                jumpBoostPower = player.jumpBoostPower,
            )
        }
    }

}
