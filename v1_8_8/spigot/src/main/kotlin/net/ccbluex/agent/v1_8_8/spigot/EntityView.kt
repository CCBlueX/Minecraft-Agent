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

package net.ccbluex.agent.v1_8_8.spigot

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.entity.Abilities
import net.ccbluex.agent.world.entity.AbstractArrow
import net.ccbluex.agent.world.entity.AbstractBoat
import net.ccbluex.agent.world.entity.ArmorStand
import net.ccbluex.agent.world.entity.Attribute
import net.ccbluex.agent.world.entity.Attributes
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
import net.ccbluex.agent.world.entity.MobEffects
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.entity.Pose
import net.ccbluex.agent.world.entity.Projectile
import net.ccbluex.agent.world.entity.ThrownEnderpearl
import net.ccbluex.agent.world.item.ItemStack
import net.minecraft.server.v1_8_R3.EntityArmorStand
import net.minecraft.server.v1_8_R3.EntityArrow
import net.minecraft.server.v1_8_R3.EntityBoat
import net.minecraft.server.v1_8_R3.EntityEnderCrystal
import net.minecraft.server.v1_8_R3.EntityEnderPearl
import net.minecraft.server.v1_8_R3.EntityFishingHook
import net.minecraft.server.v1_8_R3.EntityHuman
import net.minecraft.server.v1_8_R3.EntityItem
import net.minecraft.server.v1_8_R3.EntityLiving
import net.minecraft.server.v1_8_R3.EntityMinecartAbstract
import net.minecraft.server.v1_8_R3.EntityMinecartTNT
import net.minecraft.server.v1_8_R3.EntityPlayer
import net.minecraft.server.v1_8_R3.EntityProjectile
import net.minecraft.server.v1_8_R3.EntityTypes
import net.minecraft.server.v1_8_R3.GenericAttributes
import net.minecraft.server.v1_8_R3.Material
import net.minecraft.server.v1_8_R3.MobEffectList
import net.minecraft.server.v1_8_R3.WorldSettings
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import net.minecraft.server.v1_8_R3.Entity as McEntity
import net.minecraft.server.v1_8_R3.ItemStack as McItemStack

/**
 * A 1.8 entity as the library sees it, read live from the game's entity; one view per entity.
 *
 * @agent claude-opus-5-5
 */
internal open class EntityView(open val handle: McEntity) : Entity {
    override val id: Int get() = handle.id
    override val uuid: UUID get() = handle.uniqueID
    override val x: Double get() = handle.locX
    override val y: Double get() = handle.locY
    override val z: Double get() = handle.locZ
    override fun position(): Vec3 = Vec3(handle.locX, handle.locY, handle.locZ)
    override fun oldPosition(): Vec3 = Vec3(handle.lastX, handle.lastY, handle.lastZ)
    override fun getPosition(partialTick: Float): Vec3 = Vec3(
        Mth.lerp(partialTick.toDouble(), handle.lastX, handle.locX),
        Mth.lerp(partialTick.toDouble(), handle.lastY, handle.locY),
        Mth.lerp(partialTick.toDouble(), handle.lastZ, handle.locZ),
    )
    override fun blockPosition(): BlockPos = BlockPos.containing(handle.locX, handle.locY, handle.locZ)
    override val boundingBox: AABB get() = handle.boundingBox.let { AABB(it.a, it.b, it.c, it.d, it.e, it.f) }
    override val deltaMovement: Vec3 get() = Vec3(handle.motX, handle.motY, handle.motZ)
    override val bbWidth: Float get() = handle.width
    override val bbHeight: Float get() = handle.length
    override val eyeHeight: Float get() = handle.headHeight
    override val eyePosition: Vec3 get() = Vec3(handle.locX, eyeY, handle.locZ)
    override val eyeY: Double get() = handle.locY + handle.headHeight
    override val yRot: Float get() = handle.yaw
    override val xRot: Float get() = handle.pitch
    override fun getViewVector(partialTick: Float): Vec3 = Vec3.directionFromRotation(xRot, yRot)
    override val lookAngle: Vec3 get() = getViewVector(1f)
    override val tickCount: Int get() = handle.ticksLived
    override val isAlive: Boolean get() = handle.isAlive
    override val isRemoved: Boolean get() = handle.dead
    override val isSpectator: Boolean get() = (handle as? EntityHuman)?.isSpectator == true
    override val isPickable: Boolean get() = handle.ad()
    override val pickRadius: Float get() = handle.ao()
    override val isInvisible: Boolean get() = handle.isInvisible
    override val isPassenger: Boolean get() = handle.vehicle != null
    override val isInWater: Boolean get() = handle.V()
    override val passengers: List<Entity> get() = listOfNotNull(handle.passenger?.let(::view))
    override fun onGround(): Boolean = handle.onGround
    override fun level(): Level = LevelView.of(handle.world)
    override val vehicle: Entity? get() = handle.vehicle?.let(::view)
    override val controlledVehicle: Entity? get() = vehicle
    override val rootVehicle: Entity get() = generateSequence<Entity>(this) { it.vehicle }.last()
    override fun getPassengerRidingPosition(passenger: Entity): Vec3 = position().add(0.0, handle.an(), 0.0)
    override fun distanceTo(other: Entity): Float = Math.sqrt(distanceToSqr(other)).toFloat()
    override fun distanceToSqr(other: Entity): Double = distanceToSqr(other.position())
    override fun distanceToSqr(point: Vec3): Double = point.distanceToSqr(position())
    override val name: String get() = handle.name
    override val scoreboardName: String get() = handle.name

    // 1.8 names its entity types in camel case, modern ids in snake case; players have none
    override val typeId: String
        get() = "minecraft:" + (EntityTypes.b(handle)?.replace(CAMEL_CASE, "_")?.lowercase() ?: "player")
    override val customName: String? get() = handle.customName?.takeIf { it.isNotEmpty() }
    override val isCustomNameVisible: Boolean get() = handle.customNameVisible
    override fun toString(): String = "EntityView($handle)"

    companion object {
        private val CAMEL_CASE = Regex("(?<=[a-z])(?=[A-Z])")

        private val views = Collections.synchronizedMap(WeakHashMap<McEntity, EntityView>())

        fun view(entity: McEntity): EntityView = views.getOrPut(entity) { create(entity) }

        private fun create(entity: McEntity): EntityView = when (entity) {
            is EntityArmorStand -> object : LivingEntityView(entity), ArmorStand {
                override val isMarker: Boolean get() = entity.s()
            }
            is EntityHuman -> PlayerView(entity)
            is EntityLiving -> LivingEntityView(entity)
            is EntityItem -> object : EntityView(entity), ItemEntity {
                override val item: ItemStack get() = StackView.of(entity.itemStack)
            }
            is EntityEnderPearl -> object : EntityView(entity), ThrownEnderpearl {
                override val owner: Entity? get() = entity.shooter?.let(::view)
            }
            is EntityFishingHook -> object : EntityView(entity), FishingHook {
                override val owner: Entity? get() = entity.owner?.let(::view)
                override val hookedIn: Entity? get() = entity.hooked?.let(::view)
            }
            is EntityArrow -> object : EntityView(entity), AbstractArrow {
                override val owner: Entity? get() = entity.shooter?.let(::view)
            }
            is EntityProjectile -> object : EntityView(entity), Projectile {
                override val owner: Entity? get() = entity.shooter?.let(::view)
            }
            is EntityEnderCrystal -> object : EntityView(entity), EndCrystal {}
            is EntityMinecartTNT -> object : EntityView(entity), MinecartTNT {}
            is EntityMinecartAbstract -> object : EntityView(entity), Minecart {}
            is EntityBoat -> object : EntityView(entity), AbstractBoat {}
            else -> EntityView(entity)
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
internal open class LivingEntityView(override val handle: EntityLiving) : EntityView(handle), LivingEntity {
    override val health: Float get() = handle.health
    override val maxHealth: Float get() = handle.maxHealth
    override val absorptionAmount: Float get() = handle.absorptionHearts
    override val hurtTime: Int get() = handle.hurtTicks
    override val isBlocking: Boolean get() = (handle as? EntityHuman)?.isBlocking == true
    override val isUsingItem: Boolean get() = (handle as? EntityHuman)?.bS() == true
    override val useItem: ItemStack get() = (handle as? EntityHuman)?.let { StackView.of(USED.get(it) as McItemStack?) } ?: ItemStack.EMPTY
    override val mainHandItem: ItemStack get() = StackView.of(handle.bA())
    override val offhandItem: ItemStack get() = ItemStack.EMPTY
    override fun getItemInHand(hand: InteractionHand): ItemStack =
        if (hand == InteractionHand.MAIN_HAND) mainHandItem else ItemStack.EMPTY
    override fun getItemBySlot(slot: EquipmentSlot): ItemStack = when (slot) {
        EquipmentSlot.MAINHAND -> mainHandItem
        EquipmentSlot.FEET -> StackView.of(handle.getEquipment(1))
        EquipmentSlot.LEGS -> StackView.of(handle.getEquipment(2))
        EquipmentSlot.CHEST -> StackView.of(handle.getEquipment(3))
        EquipmentSlot.HEAD -> StackView.of(handle.getEquipment(4))
        else -> ItemStack.EMPTY
    }
    override fun hasEffect(effect: MobEffect): Boolean = getEffect(effect) != null
    override fun getEffect(effect: MobEffect): MobEffectInstance? = activeEffects.firstOrNull { it.effect == effect }
    override val activeEffects: Collection<MobEffectInstance>
        get() = handle.effects.values.map { StackView.effect(it.effectId, it.duration, it.amplifier) }
    override fun getAttributeValue(attribute: Attribute): Double = when (attribute) {
        Attributes.MOVEMENT_SPEED -> handle.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).value
        Attributes.ATTACK_DAMAGE -> handle.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE)?.value ?: 1.0
        Attributes.JUMP_STRENGTH -> JUMP_STRENGTH
        Attributes.STEP_HEIGHT -> handle.S.toDouble()
        Attributes.SAFE_FALL_DISTANCE -> SAFE_FALL_DISTANCE
        Attributes.SNEAKING_SPEED -> SNEAKING_SPEED
        Attributes.ENTITY_INTERACTION_RANGE -> ATTACK_REACH
        // No attack cooldown: a full strength hit every time
        Attributes.ATTACK_SPEED -> UNLIMITED_ATTACK_SPEED
        else -> 0.0
    }
    override val isFallFlying: Boolean get() = false
    override val isDeadOrDying: Boolean get() = handle.health <= 0f
    override val isSleeping: Boolean get() = handle.isSleeping
    override val jumpBoostPower: Float
        get() = handle.getEffect(MobEffectList.JUMP)?.let { (it.amplifier + 1) * 0.1f } ?: 0f
    override val lastHurtByMob: LivingEntity? get() = handle.lastDamager?.let { view(it) as? LivingEntity }
    override fun hasLineOfSight(other: Entity): Boolean =
        (other as? EntityView)?.let { handle.hasLineOfSight(it.handle) } ?: false
    override val isSwinging: Boolean get() = handle.ar
    override fun getSwingAnimation(partialTick: Float): Float = if (handle.ar) (handle.`as` + partialTick) / SWING_TICKS else 0f

    private companion object {
        // EntityHuman's item in use, which it keeps private
        val USED: java.lang.reflect.Field = EntityHuman::class.java.getDeclaredField("g").apply { isAccessible = true }
        const val JUMP_STRENGTH = 0.41999998688697815
        const val SAFE_FALL_DISTANCE = 3.0
        const val SNEAKING_SPEED = 0.3
        const val ATTACK_REACH = 3.0
        const val UNLIMITED_ATTACK_SPEED = 1024.0
        const val SWING_TICKS = 6f
    }
}

/**
 * @agent claude-opus-5-5
 */
internal class PlayerView(override val handle: EntityHuman) : LivingEntityView(handle), Player {
    override val inventory: Inventory = object : Inventory {
        override val containerSize: Int get() = Inventory.INVENTORY_SIZE + ARMOR_SLOTS
        override fun getItem(slot: Int): ItemStack = StackView.of(
            when (slot) {
                in 0 until Inventory.INVENTORY_SIZE -> handle.inventory.items[slot]
                in Inventory.INVENTORY_SIZE until Inventory.INVENTORY_SIZE + ARMOR_SLOTS ->
                    handle.inventory.armor[slot - Inventory.INVENTORY_SIZE]
                else -> null
            },
        )
        override val selectedSlot: Int get() = handle.inventory.itemInHandIndex
    }
    override val abilities: Abilities = object : Abilities {
        override val flying: Boolean get() = handle.abilities.isFlying
        override val mayfly: Boolean get() = handle.abilities.canFly
        override val flyingSpeed: Float get() = handle.abilities.a()
    }
    override val foodData: FoodData = object : FoodData {
        override val foodLevel: Int get() = handle.foodData.foodLevel
    }
    // 1.8 has no item cooldowns
    override val cooldowns: ItemCooldowns = object : ItemCooldowns {
        override fun isOnCooldown(item: net.ccbluex.agent.world.item.Item): Boolean = false
    }
    override fun gameMode(): GameType? = (handle as? EntityPlayer)?.playerInteractManager?.gameMode?.let {
        when (it) {
            WorldSettings.EnumGamemode.CREATIVE -> GameType.CREATIVE
            WorldSettings.EnumGamemode.ADVENTURE -> GameType.ADVENTURE
            WorldSettings.EnumGamemode.SPECTATOR -> GameType.SPECTATOR
            else -> GameType.SURVIVAL
        }
    }
    override val isCreative: Boolean get() = handle.abilities.canInstantlyBuild
    override fun blockInteractionRange(): Double = if (isCreative) CREATIVE_REACH else SURVIVAL_REACH
    override fun entityInteractionRange(): Double = ATTACK_REACH
    override val isShiftKeyDown: Boolean get() = handle.isSneaking
    override val isCrouching: Boolean get() = handle.isSneaking
    override val isSprinting: Boolean get() = handle.isSprinting
    override val isSwimming: Boolean get() = false
    override val isUnderWater: Boolean get() = handle.a(Material.WATER)
    override fun isEyeInWater(): Boolean = isUnderWater
    override fun onClimbable(): Boolean = handle.k_()
    override val isSuppressingSlidingDownLadder: Boolean get() = handle.isSneaking
    override val airSupply: Int get() = handle.airTicks
    override val maxAirSupply: Int get() = Entity.TOTAL_AIR_SUPPLY
    override val fallDistance: Double get() = handle.fallDistance.toDouble()
    override val horizontalCollision: Boolean get() = handle.positionChanged
    override val minorHorizontalCollision: Boolean get() = false
    override val verticalCollision: Boolean get() = handle.E
    override val verticalCollisionBelow: Boolean get() = handle.E && handle.onGround
    override val isInPowderSnow: Boolean get() = false
    override val wasInPowderSnow: Boolean get() = false

    // 1.8 takes friction from under the centre, where PlayerMotion's 1.8 step looks too
    override val mainSupportingBlockPos: BlockPos? get() = if (handle.onGround) blockPosBelowThatAffectsMyMovement else null
    override val blockPosBelowThatAffectsMyMovement: BlockPos
        get() = BlockPos(Mth.floor(handle.locX), Mth.floor(handle.boundingBox.b) - 1, Mth.floor(handle.locZ))
    override val knownMovement: Vec3 get() = deltaMovement
    override val xo: Double get() = handle.lastX
    override val yo: Double get() = handle.lastY
    override val zo: Double get() = handle.lastZ
    override val xRotO: Float get() = handle.lastPitch
    override val yRotO: Float get() = handle.lastYaw
    override val pose: Pose get() = Pose.STANDING
    override val fishing: FishingHook? get() = handle.hookedFish?.let { view(it) as? FishingHook }
    override val noJumpDelay: Int get() = JUMP_DELAY.getInt(handle)
    override val latency: Int? get() = (handle as? EntityPlayer)?.ping

    // No attack cooldown under 1.8 rules
    override fun getAttackStrengthScale(adjustTicks: Float): Float = 1f

    private companion object {
        val JUMP_DELAY: java.lang.reflect.Field = EntityLiving::class.java.getDeclaredField("bn").apply { isAccessible = true }
        const val ARMOR_SLOTS = 4
        const val ATTACK_REACH = 3.0
        const val SURVIVAL_REACH = 4.5
        const val CREATIVE_REACH = 5.0
    }
}

/** @agent claude-opus-5-5 */
internal fun McEntity.toAgent(): EntityView = EntityView.view(this)

/** @agent claude-opus-5-5 */
internal fun EntityHuman.toAgent(): PlayerView = EntityView.view(this) as PlayerView
