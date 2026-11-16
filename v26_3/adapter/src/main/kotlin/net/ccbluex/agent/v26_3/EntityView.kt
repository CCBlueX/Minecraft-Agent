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

package net.ccbluex.agent.v26_3

import com.google.common.collect.MapMaker
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.simulation.EntityFields
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.entity.Abilities
import net.ccbluex.agent.world.entity.AbstractArrow
import net.ccbluex.agent.world.entity.AbstractBoat
import net.ccbluex.agent.world.entity.AbstractHorse
import net.ccbluex.agent.world.entity.ArmorStand
import net.ccbluex.agent.world.entity.Attribute
import net.ccbluex.agent.world.entity.Camel
import net.ccbluex.agent.world.entity.EndCrystal
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.FishingHook
import net.ccbluex.agent.world.entity.FoodData
import net.ccbluex.agent.world.entity.GameType
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.entity.ItemCooldowns
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.entity.Minecart
import net.ccbluex.agent.world.entity.MinecartTNT
import net.ccbluex.agent.world.entity.MobEffect
import net.ccbluex.agent.world.entity.MobEffectInstance
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.entity.Pose
import net.ccbluex.agent.world.entity.Projectile
import net.ccbluex.agent.world.entity.ThrownEnderpearl
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import java.util.UUID
import java.util.concurrent.ConcurrentMap
import net.minecraft.world.effect.MobEffectInstance as McMobEffectInstance
import net.minecraft.world.entity.Entity as McEntity
import net.minecraft.world.entity.LivingEntity as McLivingEntity
import net.minecraft.world.entity.animal.camel.Camel as McCamel
import net.minecraft.world.entity.animal.equine.AbstractHorse as McAbstractHorse
import net.minecraft.world.entity.boss.enderdragon.EndCrystal as McEndCrystal
import net.minecraft.world.entity.decoration.ArmorStand as McArmorStand
import net.minecraft.world.entity.item.ItemEntity as McItemEntity
import net.minecraft.world.entity.player.Player as McPlayer
import net.minecraft.world.entity.projectile.FishingHook as McFishingHook
import net.minecraft.world.entity.projectile.Projectile as McProjectile
import net.minecraft.world.entity.projectile.arrow.AbstractArrow as McAbstractArrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl as McThrownEnderpearl
import net.minecraft.world.entity.vehicle.boat.AbstractBoat as McAbstractBoat
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart as McAbstractMinecart
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT as McMinecartTNT

/**
 * An entity of the game as the agents see it; it shows the entity as it is now.
 *
 * @agent claude-opus-5-5
 */
internal open class EntityView(open val handle: McEntity) : Entity {
    override val id: Int get() = handle.id
    override val uuid: UUID get() = handle.uuid
    override val x: Double get() = handle.x
    override val y: Double get() = handle.y
    override val z: Double get() = handle.z

    override fun position(): Vec3 = handle.position().toAgent()

    override fun oldPosition(): Vec3 = handle.oldPosition().toAgent()

    override fun getPosition(partialTick: Float): Vec3 = handle.getPosition(partialTick).toAgent()

    override fun blockPosition(): BlockPos = handle.blockPosition().toAgent()

    override val boundingBox: AABB get() = handle.boundingBox.toAgent()
    override val deltaMovement: Vec3 get() = handle.deltaMovement.toAgent()
    override val bbWidth: Float get() = handle.bbWidth
    override val bbHeight: Float get() = handle.bbHeight
    override val eyeHeight: Float get() = handle.eyeHeight
    override val eyePosition: Vec3 get() = handle.eyePosition.toAgent()
    override val eyeY: Double get() = handle.eyeY
    override val yRot: Float get() = handle.yRot
    override val xRot: Float get() = handle.xRot

    override fun getViewVector(partialTick: Float): Vec3 = handle.getViewVector(partialTick).toAgent()

    override val lookAngle: Vec3 get() = handle.lookAngle.toAgent()
    override val tickCount: Int get() = handle.tickCount
    override val isAlive: Boolean get() = handle.isAlive
    override val isRemoved: Boolean get() = handle.isRemoved
    override val isSpectator: Boolean get() = handle.isSpectator
    override val isPickable: Boolean get() = handle.isPickable
    override val pickRadius: Float get() = handle.pickRadius
    override val isInvisible: Boolean get() = handle.isInvisible
    override val isPassenger: Boolean get() = handle.isPassenger
    override val isInWater: Boolean get() = handle.isInWater

    override fun onGround(): Boolean = handle.onGround()

    override fun level(): Level = handle.level().toAgent()

    override val vehicle: Entity? get() = handle.vehicle?.toAgent()
    override val controlledVehicle: Entity? get() = handle.controlledVehicle?.toAgent()
    override val rootVehicle: Entity get() = handle.rootVehicle.toAgent()

    override fun getPassengerRidingPosition(passenger: Entity): Vec3 =
        handle.getPassengerRidingPosition(passenger.toMinecraft()).toAgent()

    override fun distanceTo(other: Entity): Float = handle.distanceTo(other.toMinecraft())

    override fun distanceToSqr(other: Entity): Double = handle.distanceToSqr(other.toMinecraft())

    override fun distanceToSqr(point: Vec3): Double = handle.distanceToSqr(point.x, point.y, point.z)

    override val name: String get() = handle.name.string
    override val scoreboardName: String get() = handle.scoreboardName
    override val typeId: String get() = BuiltInRegistries.ENTITY_TYPE.getKey(handle.type).toString()
    override val customName: String? get() = handle.customName?.string
    override val isCustomNameVisible: Boolean get() = handle.isCustomNameVisible

    override fun toString(): String = handle.toString()
}

/**
 * @agent claude-opus-5-5
 */
internal open class LivingEntityView(override val handle: McLivingEntity) : EntityView(handle), LivingEntity {
    override val health: Float get() = handle.health
    override val maxHealth: Float get() = handle.maxHealth
    override val absorptionAmount: Float get() = handle.absorptionAmount
    override val hurtTime: Int get() = handle.hurtTime
    override val isBlocking: Boolean get() = handle.isBlocking
    override val isUsingItem: Boolean get() = handle.isUsingItem
    override val useItem: ItemStack get() = handle.useItem.toAgent()
    override val mainHandItem: ItemStack get() = handle.mainHandItem.toAgent()
    override val offhandItem: ItemStack get() = handle.offhandItem.toAgent()

    override fun getItemInHand(hand: InteractionHand): ItemStack = handle.getItemInHand(hand.toMinecraft()).toAgent()

    override fun getItemBySlot(slot: EquipmentSlot): ItemStack = handle.getItemBySlot(slot.toMinecraft()).toAgent()

    override fun hasEffect(effect: MobEffect): Boolean = handle.hasEffect(effect.toMinecraft())

    override fun getEffect(effect: MobEffect): MobEffectInstance? = handle.getEffect(effect.toMinecraft())?.toAgent()

    override val activeEffects: Collection<MobEffectInstance> get() = handle.activeEffects.map { it.toAgent() }

    override fun getAttributeValue(attribute: Attribute): Double = handle.getAttributeValue(attribute.toMinecraft())

    override val isFallFlying: Boolean get() = handle.isFallFlying
    override val isDeadOrDying: Boolean get() = handle.isDeadOrDying
    override val jumpBoostPower: Float get() = handle.jumpBoostPower
    override val lastHurtByMob: LivingEntity? get() = handle.lastHurtByMob?.toAgent()

    override fun hasLineOfSight(other: Entity): Boolean = handle.hasLineOfSight(other.toMinecraft())

    override val isSwinging: Boolean get() = handle.isSwinging

    override fun getSwingAnimation(partialTick: Float): Float = handle.getSwingAnimation(partialTick)
}

/**
 * @agent claude-opus-5-5
 */
internal open class PlayerView(override val handle: McPlayer) : LivingEntityView(handle), Player {
    override val inventory: Inventory = object : Inventory {
        override val containerSize: Int get() = handle.inventory.containerSize

        override fun getItem(slot: Int): ItemStack = handle.inventory.getItem(slot).toAgent()

        override val selectedSlot: Int get() = handle.inventory.selectedSlot
    }
    override val abilities: Abilities = object : Abilities {
        override val flying: Boolean get() = handle.abilities.flying
        override val mayfly: Boolean get() = handle.abilities.mayfly
        override val flyingSpeed: Float get() = handle.abilities.flyingSpeed
    }
    override val foodData: FoodData = object : FoodData {
        override val foodLevel: Int get() = handle.foodData.foodLevel
    }
    override val cooldowns: ItemCooldowns = object : ItemCooldowns {
        override fun isOnCooldown(item: Item): Boolean =
            handle.cooldowns.isOnCooldown(item.toMinecraft().defaultInstance)
    }

    override fun gameMode(): GameType? = handle.gameMode()?.toAgent()

    override val isCreative: Boolean get() = handle.isCreative

    override fun blockInteractionRange(): Double = handle.blockInteractionRange()

    override fun entityInteractionRange(): Double = handle.entityInteractionRange()

    override val isShiftKeyDown: Boolean get() = handle.isShiftKeyDown
    override val isCrouching: Boolean get() = handle.isCrouching
    override val isSprinting: Boolean get() = handle.isSprinting
    override val isSwimming: Boolean get() = handle.isSwimming
    override val isUnderWater: Boolean get() = handle.isUnderWater

    override fun isEyeInWater(): Boolean = handle.isEyeInFluid(FluidTags.WATER)

    override fun onClimbable(): Boolean = handle.onClimbable()

    override val isSuppressingSlidingDownLadder: Boolean get() = handle.isSuppressingSlidingDownLadder
    override val airSupply: Int get() = handle.airSupply
    override val maxAirSupply: Int get() = handle.maxAirSupply
    override val fallDistance: Double get() = handle.fallDistance
    override val horizontalCollision: Boolean get() = handle.horizontalCollision
    override val minorHorizontalCollision: Boolean get() = handle.minorHorizontalCollision
    override val verticalCollision: Boolean get() = handle.verticalCollision
    override val verticalCollisionBelow: Boolean get() = handle.verticalCollisionBelow
    override val isInPowderSnow: Boolean get() = handle.isInPowderSnow
    override val wasInPowderSnow: Boolean get() = handle.wasInPowderSnow
    override val mainSupportingBlockPos: BlockPos? get() = handle.mainSupportingBlockPos.orElse(null)?.toAgent()
    override val blockPosBelowThatAffectsMyMovement: BlockPos
        get() = handle.blockPosBelowThatAffectsMyMovement.toAgent()
    override val knownMovement: Vec3 get() = handle.knownMovement.toAgent()
    override val xo: Double get() = handle.xo
    override val yo: Double get() = handle.yo
    override val zo: Double get() = handle.zo
    override val xRotO: Float get() = handle.xRotO
    override val yRotO: Float get() = handle.yRotO
    override val pose: Pose get() = handle.pose.toAgent()
    override val fishing: FishingHook? get() = handle.fishing?.toAgent() as FishingHook?
    override val noJumpDelay: Int get() = EntityFields.noJumpDelay(handle)
    override val latency: Int? get() = (handle as? ServerPlayer)?.connection?.latency()
}

private class ItemEntityView(override val handle: McItemEntity) : EntityView(handle), ItemEntity {
    override val item: ItemStack get() = handle.item.toAgent()
}

private open class ProjectileView(override val handle: McProjectile) : EntityView(handle), Projectile {
    override val owner: Entity? get() = handle.owner?.toAgent()
}

private class ThrownEnderpearlView(handle: McThrownEnderpearl) : ProjectileView(handle), ThrownEnderpearl

private class AbstractArrowView(handle: McAbstractArrow) : ProjectileView(handle), AbstractArrow

private class FishingHookView(override val handle: McFishingHook) : ProjectileView(handle), FishingHook {
    override val hookedIn: Entity? get() = handle.hookedIn?.toAgent()
}

private class EndCrystalView(handle: McEndCrystal) : EntityView(handle), EndCrystal

private class MinecartTNTView(handle: McMinecartTNT) : EntityView(handle), MinecartTNT

private class MinecartView(handle: McAbstractMinecart) : EntityView(handle), Minecart

private class AbstractBoatView(handle: McAbstractBoat) : EntityView(handle), AbstractBoat

private open class AbstractHorseView(override val handle: McAbstractHorse) : LivingEntityView(handle), AbstractHorse {
    override fun canJump(): Boolean = handle.canJump()

    override fun maxUpStep(): Float = handle.maxUpStep()
}

private class CamelView(handle: McCamel) : AbstractHorseView(handle), Camel

private class ArmorStandView(override val handle: McArmorStand) : LivingEntityView(handle), ArmorStand {
    override val isMarker: Boolean get() = handle.isMarker
}

/**
 * The views made so far, one per entity of the game; weak keys compared by identity.
 */
private val views: ConcurrentMap<McEntity, Entity> = MapMaker().weakKeys().makeMap()

/**
 * The library's view of [this]; the same instance every time, so entities compare with `===` as the game's do.
 *
 * @agent claude-opus-5-5
 */
public fun McEntity.toAgent(): Entity = views.computeIfAbsent(this, ::view)

/**
 * @agent claude-opus-5-5
 */
public fun McLivingEntity.toAgent(): LivingEntity = (this as McEntity).toAgent() as LivingEntity

/**
 * @agent claude-opus-5-5
 */
public fun McPlayer.toAgent(): Player = (this as McEntity).toAgent() as Player

/**
 * The game's entity behind [this].
 *
 * @agent claude-opus-5-5
 */
public fun Entity.toMinecraft(): McEntity = (this as EntityView).handle

/**
 * @agent claude-opus-5-5
 */
public fun LivingEntity.toMinecraft(): McLivingEntity = (this as LivingEntityView).handle

/**
 * @agent claude-opus-5-5
 */
public fun Player.toMinecraft(): McPlayer = (this as PlayerView).handle

private fun view(entity: McEntity): Entity = when (entity) {
    is McPlayer -> PlayerView(entity)
    is McArmorStand -> ArmorStandView(entity)
    is McCamel -> CamelView(entity)
    is McAbstractHorse -> AbstractHorseView(entity)
    is McLivingEntity -> LivingEntityView(entity)
    is McItemEntity -> ItemEntityView(entity)
    is McThrownEnderpearl -> ThrownEnderpearlView(entity)
    is McFishingHook -> FishingHookView(entity)
    is McAbstractArrow -> AbstractArrowView(entity)
    is McProjectile -> ProjectileView(entity)
    is McEndCrystal -> EndCrystalView(entity)
    is McMinecartTNT -> MinecartTNTView(entity)
    is McAbstractMinecart -> MinecartView(entity)
    is McAbstractBoat -> AbstractBoatView(entity)
    else -> EntityView(entity)
}

private fun McMobEffectInstance.toAgent() =
    MobEffectInstance(MobEffect.of(effect.id), duration, amplifier)
