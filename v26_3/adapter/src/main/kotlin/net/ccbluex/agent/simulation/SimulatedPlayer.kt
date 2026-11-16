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

import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.UseEffects
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * A vanilla player entity that is never added to a level and moves through the real one with a fixed [input].
 *
 * Movement is [net.minecraft.world.entity.LivingEntity.aiStep] itself. Only what the client's LocalPlayer adds on top
 * of it is ported here, since a LocalPlayer cannot tick without sending packets, and a ServerPlayer leaves movement
 * to its client. Entities are not pushed and do not push back. Blocks the simulation touches may still react to it;
 * hosts keep the level untouched while [isTicking] (block changes, particles).
 *
 * From LiquidBounce: [SimulatedPlayer.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/SimulatedPlayer.kt#L58-L319)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
public class SimulatedPlayer private constructor(
    source: Player,
    override var input: SimulatedInput,
    deltaMovement: Vec3,
    /**
     * The source already ran its tick up to [aiStep], so the first tick picks up from there.
     */
    private var continuesTick: Boolean,
    private val hooks: SimulationHooks,
) : Player(source.level(), source.gameProfile), PlayerSimulation.Steered {

    private val sourceGameMode: GameType? = source.gameMode()

    private var crouching = source.isCrouching

    /**
     * The sneak key of the tick before, which decides crouching.
     */
    private var wasShiftKeyDown = source.isShiftKeyDown
    private var probesLedge = false

    private val itemUseEffects = source.takeIf { it.isUsingItem && !it.isPassenger }
        ?.useItem?.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT)

    /**
     * Sideways (x) and forward (y) speed while using an item.
     */
    private val itemUseMultiplier = itemUseEffects?.speedMultiplier()?.let(hooks::itemUseMultiplier)

    private val isSlowDueToUsingItem = itemUseEffects?.canSprint() == false && hooks.slowedByItemUse()

    private val safeWalk = hooks.isStayingOnGroundSurface()

    private val sneakingSpeed by lazy {
        hooks.sneakingSpeed(getAttributeValue(Attributes.SNEAKING_SPEED)).toFloat()
    }

    /**
     * Sneaking or safe walk would have held the player back at a ledge in the last tick.
     */
    override var hasBackedOffEdge: Boolean = false
        private set

    override val pos: net.ccbluex.agent.math.Vec3
        get() = position().toAgent()

    override val player: net.ccbluex.agent.world.entity.Player
        get() = toAgent()

    init {
        attributes.assignAllValues(source.attributes)
        for (effect in source.activeEffects) {
            addEffect(MobEffectInstance(effect))
        }
        for (slot in EquipmentSlot.VALUES) {
            equipment.set(slot, source.getItemBySlot(slot))
        }
        abilities.flying = source.abilities.flying
        abilities.mayfly = source.abilities.mayfly
        abilities.flyingSpeed = source.abilities.flyingSpeed
        foodData.foodLevel = source.foodData.foodLevel

        pose = source.pose
        setPos(source.position())
        setOldPosAndRot(source.oldPosition(), source.yRotO, source.xRotO)
        yRot = source.yRot
        xRot = source.xRot
        this.deltaMovement = deltaMovement

        setOnGround(source.onGround())
        mainSupportingBlockPos = source.mainSupportingBlockPos
        horizontalCollision = source.horizontalCollision
        verticalCollision = source.verticalCollision
        verticalCollisionBelow = source.verticalCollisionBelow
        minorHorizontalCollision = source.minorHorizontalCollision
        fallDistance = source.fallDistance
        stuckSpeedMultiplier = EntityFields.stuckSpeedMultiplier(source)
        isInPowderSnow = source.isInPowderSnow
        wasInPowderSnow = source.wasInPowderSnow
        EntityFields.setNoJumpDelay(this, EntityFields.noJumpDelay(source))

        firstTick = false
        EntityFields.fluidInteraction(this).update(this, true)
        wasTouchingWater = source.isInWater
        wasUnderwater = source.isUnderWater

        isSwimming = source.isSwimming
        isSprinting = source.isSprinting
        setSharedFlag(FLAG_FALL_FLYING, source.isFallFlying)
    }

    override fun gameMode(): GameType? = sourceGameMode

    override fun tick() {
        hasBackedOffEdge = false

        ticking.set(true)
        try {
            if (continuesTick) {
                aiStep()
                updatePlayerPose()
            } else {
                commonTick()
                super.tick()
            }
        } finally {
            ticking.set(false)
        }

        removeExpiredEffects()
    }

    /**
     * The server removes an expired effect, which a simulation never hears of.
     */
    private fun removeExpiredEffects() {
        activeEffects
            .filter { effect -> !effect.isInfiniteDuration && effect.duration <= 0 }
            .forEach { effect -> removeEffect(effect.effect) }
    }

    private val isMovingSlowly
        get() = isCrouching || isVisuallyCrawling

    /**
     * LocalPlayer.aiStep, without what only a player on screen does (portals, vision, riding, toggling flight).
     */
    override fun aiStep() {
        if (continuesTick) {
            continuesTick = false
        } else {
            updateCrouching()
        }

        val keys = input.keys
        wasShiftKeyDown = keys.shift
        updateSprinting(keys.sprint)

        if (isInWater && keys.shift && isAffectedByFluids) {
            goDownInWater()
        }

        val flyingUp = (if (keys.jump) 1 else 0) - (if (keys.shift) 1 else 0)
        if (abilities.flying && flyingUp != 0) {
            addDeltaMovement(Vec3(0.0, (flyingUp * abilities.flyingSpeed * 3.0f).toDouble(), 0.0))
        }

        super.aiStep()

        if (onGround() && abilities.flying && !isSpectator) {
            abilities.flying = false
        }
    }

    private fun updateCrouching() {
        val fitsStanding = isSleeping || canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING)
        val mayCrouch = !abilities.flying && !isSwimming && !isPassenger
        crouching = mayCrouch && canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING) &&
            (wasShiftKeyDown || !fitsStanding)
    }

    private fun updateSprinting(sprintKey: Boolean) {
        if (sprintKey && canStartSprinting()) {
            isSprinting = true
        }
        if (isSprinting && if (isSwimming) shouldStopSwimSprinting() else shouldStopRunSprinting()) {
            isSprinting = false
        }
    }

    /**
     * LocalPlayer.applyInput and LocalPlayer.modifyInput: the keys slowed by item use and sneaking, then stretched
     * so diagonal input reaches the corners of the unit square.
     */
    override fun applyInput() {
        var move = input.moveVector.toMinecraft()
        if (move.lengthSquared() != 0.0f) {
            move = move.scale(INPUT_FRICTION)
            itemUseMultiplier?.let { move = Vec2(move.x * it.x, move.y * it.y) }
            if (isMovingSlowly) {
                move = move.scale(sneakingSpeed)
            }
            move = stretchToSquare(move)
        }

        xxa = move.x
        zza = move.y
        jumping = input.keys.jump
    }

    private fun isSprintingPossible(allowedInShallowWater: Boolean) =
        !isMobilityRestricted && hasEnoughFoodToDoExhaustiveManoeuvres() &&
            (allowedInShallowWater || !isInShallowWater)

    private fun canStartSprinting(): Boolean {
        val canSprint = isSprintingPossible(abilities.flying) && !isSlowDueToUsingItem
        val free = isUnderWater || !isFallFlying && !isMovingSlowly
        return !isSprinting && input.hasForwardImpulse() && canSprint && free
    }

    private fun shouldStopRunSprinting() =
        !isSprintingPossible(abilities.flying) || !input.hasForwardImpulse() ||
            horizontalCollision && !minorHorizontalCollision

    private fun shouldStopSwimSprinting() =
        !isSprintingPossible(true) || !isInWater ||
            !input.hasForwardImpulse() && !onGround() && !input.keys.shift

    /**
     * LocalPlayer.isHorizontalCollisionMinor: the wall is grazed when the player moved within a few degrees of where
     * its input pushed.
     */
    override fun isHorizontalCollisionMinor(movement: Vec3): Boolean {
        val yRot = (yRot * Mth.DEG_TO_RAD).toDouble()
        val sin = Mth.sin(yRot).toDouble()
        val cos = Mth.cos(yRot).toDouble()
        val wantedX = xxa * cos - zza * sin
        val wantedZ = zza * cos + xxa * sin
        val wantedLengthSqr = wantedX * wantedX + wantedZ * wantedZ
        val movedLengthSqr = movement.x * movement.x + movement.z * movement.z
        if (wantedLengthSqr < MINOR_COLLISION_MIN_LENGTH_SQR || movedLengthSqr < MINOR_COLLISION_MIN_LENGTH_SQR) {
            return false
        }

        val dot = wantedX * movement.x + wantedZ * movement.z
        return acos(dot / sqrt(wantedLengthSqr * movedLengthSqr)) < MINOR_COLLISION_ANGLE
    }

    override fun move(moverType: MoverType, delta: Vec3) {
        super.move(moverType, hooks.move(moverType.toAgent(), delta.toAgent()).toMinecraft())
    }

    override fun maybeBackOffFromEdge(delta: Vec3, moverType: MoverType): Vec3 {
        probesLedge = true
        val backedOff = try {
            super.maybeBackOffFromEdge(delta, moverType)
        } finally {
            probesLedge = false
        }

        hasBackedOffEdge = backedOff != delta
        return if (staysOnGroundSurface()) backedOff else delta
    }

    private fun staysOnGroundSurface() =
        !input.ignoresEdgeBackOff && (input.keys.shift || input.forceStayOnGroundSurface || safeWalk)

    override fun isStayingOnGroundSurface(): Boolean = probesLedge || staysOnGroundSurface()

    override fun isShiftKeyDown(): Boolean = input.keys.shift

    override fun isCrouching(): Boolean = crouching

    override fun isUnderWater(): Boolean = wasUnderwater

    override fun isSuppressingSlidingDownLadder(): Boolean =
        !abilities.flying && super.isSuppressingSlidingDownLadder()

    override fun isLocalClientAuthoritative(): Boolean = true

    // On a server level Entity.isLocalInstanceAuthoritative asks this instead, which a player otherwise answers
    // with true and so would leave the simulation's own movement to a client.
    override fun isClientAuthoritative(): Boolean = false

    override fun isEffectiveAi(): Boolean = true

    override fun pushEntities() {
        // Would push the real entities around
    }

    public companion object {
        private const val FLAG_FALL_FLYING = 7
        private const val INPUT_FRICTION = 0.98f
        private const val MINOR_COLLISION_MIN_LENGTH_SQR = 1.0E-5f
        private const val MINOR_COLLISION_ANGLE = 0.13962634f

        private val ticking = ThreadLocal.withInitial { false }

        /**
         * A simulation ticks on this thread. Blocks reacting to it must not change the level or show particles.
         */
        @JvmStatic
        public val isTicking: Boolean
            get() = ticking.get()

        /**
         * A simulation of [source] holding [input] from now on. [continuesTick] tells that [source] is inside its own
         * tick, past the point where LocalPlayer.aiStep reads the keys and before it moves, so the first [tick] only
         * finishes that tick. [hooks] are the host's changes to the movement of [source].
         */
        @JvmStatic
        @JvmOverloads
        public fun fromPlayer(
            source: Player,
            input: SimulatedInput,
            continuesTick: Boolean,
            hooks: SimulationHooks = SimulationHooks.VANILLA,
        ): SimulatedPlayer = SimulatedPlayer(source, input, source.deltaMovement, continuesTick, hooks)

        /**
         * A simulation of a player whose motion this side does not know, such as another player on a client: the
         * motion is taken from how far it moved in its last tick.
         */
        @JvmStatic
        @JvmOverloads
        public fun fromOtherPlayer(
            player: Player,
            input: SimulatedInput,
            hooks: SimulationHooks = SimulationHooks.VANILLA,
        ): SimulatedPlayer {
            val lastTick = Vec3(player.x - player.xo, player.y - player.yo, player.z - player.zo)
            return SimulatedPlayer(player, input, lastTick, continuesTick = false, hooks)
        }

        /**
         * LocalPlayer.modifyInputSpeedForSquareMovement: scales [input] so a full diagonal reaches the corner of the
         * unit square instead of the circle, at most to length one.
         */
        private fun stretchToSquare(input: Vec2): Vec2 {
            val length = input.length()
            if (length <= 0f) {
                return input
            }

            val direction = input.scale(1f / length)
            val alongX = abs(direction.x)
            val alongY = abs(direction.y)
            val slope = if (alongY > alongX) alongX / alongY else alongY / alongX
            val toSquare = Mth.sqrt(1f + slope * slope)
            return direction.scale(minOf(length * toSquare, 1f))
        }
    }
}
