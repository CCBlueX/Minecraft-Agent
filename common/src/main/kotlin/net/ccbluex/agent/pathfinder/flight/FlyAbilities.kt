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

package net.ccbluex.agent.pathfinder.flight

import net.ccbluex.agent.world.entity.LivingEntity.Companion.FLYING_AIR_DRAG
import net.ccbluex.agent.world.entity.Abilities as PlayerAbilities

/**
 * Whether the keys build up speed that fades, as in creative flight, or set it right away, as Fly's Vanilla mode.
 *
 * From LiquidBounce: [Abilities.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/Abilities.kt#L28-L28)
 *
 * @agent claude-opus-5-5
 */
public enum class Acceleration { MOMENTUM, DIRECT }

/**
 * How the player flies, captured on the render thread for one search. Speeds are in blocks per tick.
 *
 * From LiquidBounce: [Abilities.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/flight/Abilities.kt#L32-L70)
 *
 * @agent claude-opus-5-5
 */
public class FlyAbilities(
    public val control: Acceleration,
    public val horizontalSpeed: Double,
    public val verticalSpeed: Double,
    public val isFlying: Boolean,
) {

    public val horizontalCost: Double = 1.0 / horizontalSpeed
    public val verticalCost: Double = 1.0 / verticalSpeed

    public companion object {
        public const val VERTICAL_DRAG: Double = 0.6

        private const val INPUT_SCALE = 0.98
        private const val VERTICAL_THRUST = 3.0

        /**
         * Creative or mayfly flight with [abilities]; null when the player may not fly.
         */
        public fun of(abilities: PlayerAbilities): FlyAbilities? {
            if (!abilities.mayfly && !abilities.flying) {
                return null
            }
            return creative(abilities.flyingSpeed, abilities.flying)
        }

        /**
         * Creative flight at [flyingSpeed], as `Abilities.flyingSpeed` gives it.
         */
        public fun creative(flyingSpeed: Float, isFlying: Boolean): FlyAbilities {
            // Sprinting doubles the flying speed, and the speed settles where drag takes what the keys add.
            val speed = flyingSpeed.toDouble()
            return FlyAbilities(
                Acceleration.MOMENTUM,
                horizontalSpeed = 2.0 * speed * INPUT_SCALE / (1.0 - FLYING_AIR_DRAG),
                verticalSpeed = speed * VERTICAL_THRUST / (1.0 - VERTICAL_DRAG),
                isFlying = isFlying,
            )
        }
    }

}
