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

package net.ccbluex.agent.world.entity

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.item.ItemStack
import java.util.UUID

/**
 * An entity as the agents see it. The adapter wraps the game's entities; two wrappers of one entity are equal.
 *
 * @agent claude-opus-5-5
 */
public interface Entity {

    public val id: Int

    public val uuid: UUID

    public val x: Double

    public val y: Double

    public val z: Double

    public fun position(): Vec3

    /**
     * Where it was at the start of the tick.
     */
    public fun oldPosition(): Vec3

    /**
     * Between [oldPosition] and [position], [partialTick] of the way.
     */
    public fun getPosition(partialTick: Float): Vec3

    public fun blockPosition(): BlockPos

    public val boundingBox: AABB

    public val deltaMovement: Vec3

    public val bbWidth: Float

    public val bbHeight: Float

    public val eyeHeight: Float

    public val eyePosition: Vec3

    public val eyeY: Double

    public val yRot: Float

    public val xRot: Float

    public fun getViewVector(partialTick: Float): Vec3

    public val lookAngle: Vec3

    public val tickCount: Int

    public val isAlive: Boolean

    public val isRemoved: Boolean

    public val isSpectator: Boolean

    public val isPickable: Boolean

    /**
     * How far past its box a ray still picks it.
     */
    public val pickRadius: Float

    public val isInvisible: Boolean

    public val isPassenger: Boolean

    public val isInWater: Boolean

    public val blockY: Int get() = blockPosition().y

    public val passengers: List<Entity>

    public fun onGround(): Boolean

    public fun level(): Level

    public val vehicle: Entity?

    /**
     * The vehicle it steers, when it rides one.
     */
    public val controlledVehicle: Entity?

    public val rootVehicle: Entity

    public fun getPassengerRidingPosition(passenger: Entity): Vec3

    public fun distanceTo(other: Entity): Float

    public fun distanceToSqr(other: Entity): Double

    public fun distanceToSqr(point: Vec3): Double

    public val name: String

    public val scoreboardName: String

    /**
     * The entity type's id, such as `minecraft:villager`.
     */
    public val typeId: String

    /**
     * The name a name tag or a command gave it; null without one.
     */
    public val customName: String?

    public val isCustomNameVisible: Boolean

    public companion object {
        public const val DEFAULT_BB_WIDTH: Float = 0.6f
        public const val DEFAULT_BB_HEIGHT: Float = 1.8f
        public const val SWIMMING_BB_HEIGHT: Float = 0.6f
        public const val DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2: Float = 0.2f
        public const val DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5: Double = 0.500001
        public const val BASE_SAFE_FALL_DISTANCE: Int = 3
        public const val TOTAL_AIR_SUPPLY: Int = 300
    }
}

/**
 * @agent claude-opus-5-5
 */
public interface LivingEntity : Entity {

    public val health: Float

    public val maxHealth: Float

    public val absorptionAmount: Float

    public val hurtTime: Int

    public val isBlocking: Boolean

    public val isUsingItem: Boolean

    public val useItem: ItemStack

    public val mainHandItem: ItemStack

    public val offhandItem: ItemStack

    public fun getItemInHand(hand: net.ccbluex.agent.world.InteractionHand): ItemStack

    public fun getItemBySlot(slot: EquipmentSlot): ItemStack

    public fun hasEffect(effect: MobEffect): Boolean

    public fun getEffect(effect: MobEffect): MobEffectInstance?

    public val activeEffects: Collection<MobEffectInstance>

    public fun getAttributeValue(attribute: Attribute): Double

    public val isFallFlying: Boolean

    public val isDeadOrDying: Boolean

    public val isSleeping: Boolean

    public val jumpBoostPower: Float

    public val lastHurtByMob: LivingEntity?

    public fun hasLineOfSight(other: Entity): Boolean

    public val isSwinging: Boolean

    public fun getSwingAnimation(partialTick: Float): Float

    public companion object {
        public const val MIN_MOVEMENT_DISTANCE: Double = 0.003
        public const val DEFAULT_BASE_GRAVITY: Double = 0.08
        public const val BASE_JUMP_POWER: Float = 0.42f
        public const val BASE_HORIZONTAL_AIR_DRAG: Float = 0.91f
        public const val BASE_VERTICAL_AIR_DRAG: Float = 0.98f
        public const val FLYING_AIR_DRAG: Float = 0.91f
        public const val ELYTRA_HORIZONTAL_AIR_DRAG: Float = 0.99f
        public const val ELYTRA_VERTICAL_AIR_DRAG: Float = 0.98f
    }
}

/**
 * An item lying on the ground.
 *
 * @agent claude-opus-5-5
 */
public interface ItemEntity : Entity {
    public val item: ItemStack
}

/**
 * Arrows, pearls, rods' hooks, snowballs, rockets: whatever was shot or thrown, and by whom.
 *
 * @agent claude-opus-5-5
 */
public interface Projectile : Entity {
    public val owner: Entity?
}

/**
 * @agent claude-opus-5-5
 */
public interface ThrownEnderpearl : Projectile

/**
 * A fishing rod's hook.
 *
 * @agent claude-opus-5-5
 */
public interface FishingHook : Projectile {
    public val hookedIn: Entity?
}

/**
 * @agent claude-opus-5-5
 */
public interface AbstractArrow : Projectile

/**
 * @agent claude-opus-5-5
 */
public interface EndCrystal : Entity

/**
 * @agent claude-opus-5-5
 */
public interface MinecartTNT : Entity

/**
 * A minecart other than a TNT one.
 *
 * @agent claude-opus-5-5
 */
public interface Minecart : Entity

/**
 * @agent claude-opus-5-5
 */
public interface AbstractBoat : Entity

/**
 * A horse, donkey, mule, llama or camel.
 *
 * @agent claude-opus-5-5
 */
public interface AbstractHorse : LivingEntity {
    public fun canJump(): Boolean

    /**
     * The step height the vehicle climbs.
     */
    public fun maxUpStep(): Float
}

/**
 * @agent claude-opus-5-5
 */
public interface Camel : AbstractHorse

/**
 * An armor stand, which is living; a [isMarker] has no hitbox.
 *
 * @agent claude-sonnet-5-5
 */
public interface ArmorStand : LivingEntity {
    public val isMarker: Boolean
}
