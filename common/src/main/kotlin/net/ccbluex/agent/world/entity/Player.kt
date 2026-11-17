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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack

/**
 * A player, a bot body or a copy that simulates one.
 *
 * @agent claude-opus-5-5
 */
public interface Player : LivingEntity {

    public val inventory: Inventory

    public val abilities: Abilities

    public val foodData: FoodData

    public val cooldowns: ItemCooldowns

    public fun gameMode(): GameType?

    public val isCreative: Boolean

    public fun blockInteractionRange(): Double

    public fun entityInteractionRange(): Double

    public val isShiftKeyDown: Boolean

    public val isCrouching: Boolean

    public val isSprinting: Boolean

    public val isSwimming: Boolean

    public val isUnderWater: Boolean

    public fun isEyeInWater(): Boolean

    public fun onClimbable(): Boolean

    public val isSuppressingSlidingDownLadder: Boolean

    public val airSupply: Int

    public val maxAirSupply: Int

    public val fallDistance: Double

    public val horizontalCollision: Boolean

    public val minorHorizontalCollision: Boolean

    public val verticalCollision: Boolean

    public val verticalCollisionBelow: Boolean

    public val isInPowderSnow: Boolean

    public val wasInPowderSnow: Boolean

    /**
     * The block the player stands on, by the game's own reckoning; null in the air.
     */
    public val mainSupportingBlockPos: BlockPos?

    public val blockPosBelowThatAffectsMyMovement: BlockPos

    /**
     * The movement of the last tick as the game knows it; on a server what the client last sent.
     */
    public val knownMovement: Vec3

    public val xo: Double

    public val yo: Double

    public val zo: Double

    public val xRotO: Float

    public val yRotO: Float

    public val pose: Pose

    /**
     * The hook of the player's fishing rod while it is out.
     */
    public val fishing: FishingHook?

    /**
     * Ticks until holding jump jumps again.
     */
    public val noJumpDelay: Int

    /**
     * How charged the attack is, 0 to 1, [adjustTicks] ticks ahead; always 1 under 1.8 rules.
     */
    public fun getAttackStrengthScale(adjustTicks: Float): Float

    /**
     * The round trip in milliseconds the server measured to the player's client; null where the game does not know it,
     * as for players a client sees.
     */
    public val latency: Int?

    public companion object {
        public const val DEFAULT_EYE_HEIGHT: Float = 1.62f
    }
}

/**
 * @agent claude-opus-5-5
 */
public enum class GameType { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }

/**
 * @agent claude-opus-5-5
 */
public enum class Pose { STANDING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK, CROUCHING, DYING, OTHER }

/**
 * What moves an entity.
 *
 * @agent claude-opus-5-5
 */
public enum class MoverType { SELF, PLAYER, PISTON, SHULKER_BOX, SHULKER }

/**
 * The 41 slots of a player: hotbar 0 to 8, the main inventory 9 to 35, armour 36 to 39 (feet first), off hand 40.
 *
 * @agent claude-opus-5-5
 */
public interface Inventory {

    public val containerSize: Int

    public fun getItem(slot: Int): ItemStack

    /**
     * The hotbar slot to put an item into: the first empty one from the selected slot on, else the selected one.
     */
    public val suitableHotbarSlot: Int
        get() = (0 until SELECTION_SIZE).map { (selectedSlot + it) % SELECTION_SIZE }
            .firstOrNull { getItem(it).isEmpty } ?: selectedSlot

    /**
     * How many of [item] the main inventory and the offhand hold.
     */
    public fun countItem(item: Item): Int =
        (0 until INVENTORY_SIZE + 1).map { if (it == INVENTORY_SIZE) SLOT_OFFHAND else it }
            .sumOf { getItem(it).let { stack -> if (stack.`is`(item)) stack.count else 0 } }

    public val selectedSlot: Int

    public companion object {
        public const val SELECTION_SIZE: Int = 9
        public const val INVENTORY_SIZE: Int = 36
        public const val SLOT_OFFHAND: Int = 40

        @JvmStatic
        public fun getSelectionSize(): Int = SELECTION_SIZE

        @JvmStatic
        public fun isHotbarSlot(slot: Int): Boolean = slot in 0 until SELECTION_SIZE
    }
}

/**
 * @agent claude-opus-5-5
 */
public interface Abilities {
    public val flying: Boolean
    public val mayfly: Boolean
    public val flyingSpeed: Float
}

/**
 * @agent claude-opus-5-5
 */
public interface FoodData {
    public val foodLevel: Int
}

/**
 * @agent claude-opus-5-5
 */
public interface ItemCooldowns {
    public fun isOnCooldown(item: Item): Boolean
}

/**
 * @agent claude-opus-5-5
 */
public enum class EquipmentSlot(public val type: Type, private val index: Int) {
    MAINHAND(Type.HAND, 0),
    OFFHAND(Type.HAND, 1),
    FEET(Type.HUMANOID_ARMOR, 0),
    LEGS(Type.HUMANOID_ARMOR, 1),
    CHEST(Type.HUMANOID_ARMOR, 2),
    HEAD(Type.HUMANOID_ARMOR, 3),
    BODY(Type.ANIMAL_ARMOR, 0),
    SADDLE(Type.SADDLE, 0),
    ;

    public val isArmor: Boolean get() = type == Type.HUMANOID_ARMOR || type == Type.ANIMAL_ARMOR

    /**
     * The inventory slot of this armour slot, from [base] (36 for a player's inventory).
     */
    public fun getIndex(base: Int): Int = base + index

    /**
     * @agent claude-opus-5-5
     */
    public enum class Type { HAND, HUMANOID_ARMOR, ANIMAL_ARMOR, SADDLE }

    public companion object {
        @JvmField
        public val VALUES: List<EquipmentSlot> = entries
    }
}

/**
 * A status effect by its id.
 *
 * @agent claude-opus-5-5
 */
public class MobEffect private constructor(public val id: String) {
    override fun toString(): String = id

    public companion object {
        private val effects = java.util.concurrent.ConcurrentHashMap<String, MobEffect>()

        @JvmStatic
        public fun of(id: String): MobEffect =
            effects.computeIfAbsent(net.ccbluex.agent.world.namespaced(id)) { MobEffect(it) }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class MobEffectInstance(
    public val effect: MobEffect,
    public val duration: Int,
    public val amplifier: Int = 0,
) {
    public val isInfiniteDuration: Boolean get() = duration == INFINITE_DURATION

    override fun toString(): String = "$effect x${amplifier + 1}, duration: $duration"

    public companion object {
        public const val INFINITE_DURATION: Int = -1
    }
}

/**
 * @agent claude-opus-5-5
 */
public object MobEffects {
    @JvmField public val SPEED: MobEffect = MobEffect.of("speed")
    @JvmField public val STRENGTH: MobEffect = MobEffect.of("strength")
    @JvmField public val INSTANT_HEALTH: MobEffect = MobEffect.of("instant_health")
    @JvmField public val REGENERATION: MobEffect = MobEffect.of("regeneration")
    @JvmField public val FIRE_RESISTANCE: MobEffect = MobEffect.of("fire_resistance")
    @JvmField public val JUMP_BOOST: MobEffect = MobEffect.of("jump_boost")
    @JvmField public val SLOWNESS: MobEffect = MobEffect.of("slowness")
    @JvmField public val ABSORPTION: MobEffect = MobEffect.of("absorption")
    @JvmField public val RESISTANCE: MobEffect = MobEffect.of("resistance")
}

/**
 * An attribute by its id, such as `minecraft:attack_damage`.
 *
 * @agent claude-opus-5-5
 */
public class Attribute private constructor(public val id: String) {
    override fun toString(): String = id

    public companion object {
        private val attributes = java.util.concurrent.ConcurrentHashMap<String, Attribute>()

        @JvmStatic
        public fun of(id: String): Attribute =
            attributes.computeIfAbsent(net.ccbluex.agent.world.namespaced(id)) { Attribute(it) }
    }
}

/**
 * @agent claude-opus-5-5
 */
public object Attributes {
    @JvmField public val ATTACK_DAMAGE: Attribute = Attribute.of("attack_damage")
    @JvmField public val ATTACK_SPEED: Attribute = Attribute.of("attack_speed")
    @JvmField public val JUMP_STRENGTH: Attribute = Attribute.of("jump_strength")
    @JvmField public val MOVEMENT_SPEED: Attribute = Attribute.of("movement_speed")
    @JvmField public val SAFE_FALL_DISTANCE: Attribute = Attribute.of("safe_fall_distance")
    @JvmField public val SNEAKING_SPEED: Attribute = Attribute.of("sneaking_speed")
    @JvmField public val STEP_HEIGHT: Attribute = Attribute.of("step_height")
    @JvmField public val ENTITY_INTERACTION_RANGE: Attribute = Attribute.of("entity_interaction_range")
}
