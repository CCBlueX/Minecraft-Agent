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

package net.ccbluex.agent.pathfinder.ride

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity.BASE_VERTICAL_AIR_DRAG
import net.minecraft.world.entity.LivingEntity.DEFAULT_BASE_GRAVITY
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import kotlin.math.max

/**
 * From LiquidBounce: [Body.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/Body.kt#L31-L31)
 *
 * @agent claude-opus-5-5
 */
public enum class VehicleKind { BOAT, MOUNT }

/**
 * The size and abilities of a ridden vehicle with its rider: heights in blocks, speeds in blocks per tick.
 *
 * From LiquidBounce: [Body.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/Body.kt#L36-L103)
 *
 * @agent claude-opus-5-5
 */
@Suppress("LongParameterList")
public class Vehicle(
    public val kind: VehicleKind,
    public val width: Double,
    /**
     * Room above where the vehicle rests for the vehicle and the eyes of its rider.
     */
    public val height: Double,
    public val stepHeight: Double,
    /**
     * Highest ledge a fully charged jump reaches; 0 when it cannot jump.
     */
    public val jumpHeight: Double,
    public val safeFall: Int,
    public val speed: Double,
    public val iceSpeed: Double = speed,
) {

    public companion object {
        public const val BOAT_WATER_SPEED: Double = 0.35
        public const val BOAT_ICE_SPEED: Double = 1.0

        /**
         * Ticks the jump key is held for a ridden jump of full height.
         */
        public const val JUMP_CHARGE_TICKS: Int = 11

        private const val EYE_MARGIN = 0.05
        private const val JUMP_MARGIN = 0.2
        private const val BOAT_SAFE_FALL = 1

        // Top ground speed per point of the movement speed attribute, as LivingEntity.travel moves on plain ground.
        private const val GROUND_SPEED_FACTOR = 2.158

        public fun of(rider: Entity, vehicle: Entity): Vehicle? {
            val seat = vehicle.getPassengerRidingPosition(rider).y - vehicle.y
            val height = max(vehicle.bbHeight.toDouble(), seat + rider.eyeHeight + EYE_MARGIN)
            return when (vehicle) {
                is AbstractBoat -> Vehicle(
                    kind = VehicleKind.BOAT,
                    width = vehicle.bbWidth.toDouble(),
                    height = height,
                    stepHeight = 0.0,
                    jumpHeight = 0.0,
                    safeFall = BOAT_SAFE_FALL,
                    speed = BOAT_WATER_SPEED,
                    iceSpeed = BOAT_ICE_SPEED,
                )

                is AbstractHorse -> Vehicle(
                    kind = VehicleKind.MOUNT,
                    width = vehicle.bbWidth.toDouble(),
                    height = height,
                    stepHeight = vehicle.maxUpStep().toDouble(),
                    jumpHeight = jumpHeightOf(vehicle),
                    safeFall = vehicle.getAttributeValue(Attributes.SAFE_FALL_DISTANCE).toInt() - 1,
                    speed = vehicle.getAttributeValue(Attributes.MOVEMENT_SPEED) * GROUND_SPEED_FACTOR,
                )

                else -> null
            }
        }

        private fun jumpHeightOf(mount: AbstractHorse): Double {
            if (mount is Camel || !mount.canJump()) {
                return 0.0
            }

            var velocity = mount.getAttributeValue(Attributes.JUMP_STRENGTH) + mount.jumpBoostPower
            var height = 0.0
            while (velocity > 0.0) {
                height += velocity
                velocity = (velocity - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG
            }
            return (height - JUMP_MARGIN).coerceAtLeast(0.0)
        }
    }

}
