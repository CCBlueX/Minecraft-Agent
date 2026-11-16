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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.fastCos
import net.ccbluex.agent.math.fastSin
import net.ccbluex.agent.math.toRadians
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.entity.Entity.Companion.DEFAULT_BB_HEIGHT
import net.ccbluex.agent.world.entity.Entity.Companion.DEFAULT_BB_WIDTH
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_HORIZONTAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_VERTICAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.DEFAULT_BASE_GRAVITY
import net.ccbluex.agent.world.entity.LivingEntity.Companion.MIN_MOVEMENT_DISTANCE
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs
import kotlin.math.sqrt

internal const val SPRINT_FACTOR = 1.3f

/**
 * A player walking, sprinting, sneaking and jumping on land, tick for tick as LivingEntity.aiStep moves it.
 * Fluids, climbing and slow falling are left out. Collisions come from [terrain], so unlike [SimulatedPlayer] it
 * runs off the game thread.
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L87-L392)
 *
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
public class PlayerMotion internal constructor(
    internal val terrain: BlockGrid,
    private val traits: MotionTraits,
    internal val colliders: BlockColliders,
) {

    public constructor(terrain: BlockGrid, traits: MotionTraits = MotionTraits.DEFAULT) :
        this(terrain, traits, BlockColliders(terrain))

    public var x: Double = 0.0
        private set
    public var y: Double = 0.0
        private set
    public var z: Double = 0.0
        private set
    public var deltaX: Double = 0.0
        private set
    public var deltaY: Double = 0.0
        private set
    public var deltaZ: Double = 0.0
        private set
    public var onGround: Boolean = false
        private set
    public var horizontalCollision: Boolean = false
        private set
    public var isSprinting: Boolean = false
        private set

    /**
     * Fall distance of the last landing as fall damage counts it, from the highest point since leaving the ground.
     */
    public var landingFall: Double = 0.0
        private set

    private var peakY = 0.0
    private var jumpDelay = 0
    private val support = Support()
    internal val supportX: Int get() = support.x
    internal val supportY: Int get() = support.y
    internal val supportZ: Int get() = support.z
    internal val hasSupport: Boolean get() = support.exists

    /**
     * Entity.mainSupportingBlockPos: the block the feet stand on, null without one.
     */
    public val supportingBlock: BlockPos?
        get() = if (hasSupport) BlockPos(supportX, supportY, supportZ) else null

    /**
     * The block the feet are in, or over an edge the one above the block the player still stands on.
     */
    public val blockPosition: BlockPos
        get() {
            val feet = BlockPos.containing(x, y + FEET_PROBE, z)
            return if (hasSupport) BlockPos(supportX, feet.y, supportZ) else feet
        }

    /**
     * Entity.onGroundNoBlocks
     */
    private var groundWithoutBlocks = false

    private var crouching = false
    private var sneaking = false
    private val edge = EdgeBackOff()

    public val position: Vec3
        get() = Vec3(x, y, z)

    public val deltaMovement: Vec3
        get() = Vec3(deltaX, deltaY, deltaZ)

    public val horizontalSpeed: Double
        get() = Mth.length(deltaX, deltaZ)

    public fun reset(position: Vec3, motion: Vec3, onGround: Boolean, isSprinting: Boolean = false, jumpDelay: Int = 0) {
        x = position.x
        y = position.y
        z = position.z
        deltaX = motion.x
        deltaY = motion.y
        deltaZ = motion.z
        this.onGround = onGround
        this.isSprinting = isSprinting
        this.jumpDelay = jumpDelay
        horizontalCollision = false
        crouching = false
        peakY = y
        if (onGround) findSupport(0.0, 0.0) else support.clear()
        groundWithoutBlocks = onGround && !hasSupport
    }

    /**
     * Call on the game thread.
     */
    public fun reset(player: Player) {
        reset(player.position(), player.deltaMovement, player.onGround(), player.isSprinting, player.noJumpDelay)
        crouching = player.isCrouching
        player.mainSupportingBlockPos?.let { support.set(it.x, it.y, it.z) }
    }

    public fun copy(): PlayerMotion = PlayerMotion(terrain, traits, colliders).also { it.copyFrom(this) }

    public fun copyFrom(other: PlayerMotion) {
        x = other.x
        y = other.y
        z = other.z
        deltaX = other.deltaX
        deltaY = other.deltaY
        deltaZ = other.deltaZ
        onGround = other.onGround
        horizontalCollision = other.horizontalCollision
        isSprinting = other.isSprinting
        landingFall = other.landingFall
        peakY = other.peakY
        jumpDelay = other.jumpDelay
        support.copyFrom(other.support)
        groundWithoutBlocks = other.groundWithoutBlocks
        crouching = other.crouching
    }

    /**
     * One tick with the [forward] and [strafe] keys (-1, 0 or 1, strafe positive to the left) relative to [yRot].
     */
    public fun tick(yRot: Float, forward: Int, strafe: Int, jump: Boolean, sprint: Boolean, sneak: Boolean = false) {
        if (jumpDelay > 0) {
            jumpDelay--
        }
        if (Mth.lengthSquared(deltaX, deltaZ) < MIN_HORIZONTAL_SQR) {
            deltaX = 0.0
            deltaZ = 0.0
        }
        if (abs(deltaY) < MIN_MOVEMENT_DISTANCE) {
            deltaY = 0.0
        }

        // LocalPlayer.aiStep works out crouching before it reads the keys, so the sneak key slows down a tick later.
        val slow = crouching
        crouching = sneak
        sneaking = sneak
        isSprinting = sprint && forward > 0 && !horizontalCollision && (isSprinting || !slow)

        if (!jump) {
            jumpDelay = 0
        } else if (onGround && jumpDelay == 0) {
            jumpFromGround(yRot)
            jumpDelay = JUMP_DELAY
        }

        travel(yRot, forward, strafe, if (slow) INPUT_FRICTION * SNEAK_SPEED else INPUT_FRICTION)
    }

    private fun jumpFromGround(yRot: Float) {
        deltaY = maxOf((traits.jumpStrength * jumpFactor() + traits.jumpBoostPower).toDouble(), deltaY)
        if (isSprinting) {
            val angle = yRot.toRadians()
            deltaX += -angle.fastSin().toDouble() * SPRINT_JUMP_BOOST
            deltaZ += angle.fastCos().toDouble() * SPRINT_JUMP_BOOST
        }
    }

    private fun travel(yRot: Float, forward: Int, strafe: Int, inputScale: Float) {
        val blockFriction = if (onGround) movementBlock().friction else 1f
        val walkingSpeed = if (isSprinting) traits.walkingSpeed * SPRINT_FACTOR else traits.walkingSpeed
        val speed = when {
            !onGround -> if (isSprinting) SPRINT_AIR_SPEED else AIR_SPEED
            blockFriction > PLAIN_FRICTION ->
                walkingSpeed * (GROUND_FACTOR / (blockFriction * blockFriction * blockFriction))
            else -> walkingSpeed
        }
        accelerate(yRot, forward, strafe, speed, inputScale)
        move(deltaX, deltaY, deltaZ)

        val friction = (blockFriction * BASE_HORIZONTAL_AIR_DRAG).toDouble()
        deltaX *= friction
        deltaY = (deltaY - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG
        deltaZ *= friction
        if (onGround) {
            stepOn()
        }
    }

    /**
     * LocalPlayer.modifyInput and Entity.getInputVector: the keys stretched to the unit square and turned by [yRot].
     */
    private fun accelerate(yRot: Float, forward: Int, strafe: Int, speed: Float, inputScale: Float) {
        if (forward == 0 && strafe == 0) {
            return
        }

        val length = sqrt((strafe * strafe + forward * forward).toFloat())
        var inputX = strafe / length * inputScale
        var inputZ = forward / length * inputScale
        val absX = abs(inputX)
        val absZ = abs(inputZ)
        val tan = if (absZ > absX) absX / absZ else absZ / absX
        val stretched = minOf(inputScale * Mth.sqrt(1f + tan * tan), 1f) / inputScale
        inputX *= stretched
        inputZ *= stretched

        var vectorX = inputX.toDouble()
        var vectorZ = inputZ.toDouble()
        val lengthSqr = Mth.lengthSquared(vectorX, vectorZ)
        if (lengthSqr > 1.0) {
            val vectorLength = sqrt(lengthSqr)
            vectorX /= vectorLength
            vectorZ /= vectorLength
        }
        vectorX *= speed
        vectorZ *= speed

        val angle = yRot.toRadians()
        val sin = angle.fastSin()
        val cos = angle.fastCos()
        deltaX += vectorX * cos - vectorZ * sin
        deltaZ += vectorZ * cos + vectorX * sin
    }

    private fun move(wantedX: Double, dy: Double, wantedZ: Double) {
        var dx = wantedX
        var dz = wantedZ
        if (sneaking && dy <= 0.0 && isAboveGround()) {
            edge.backOff(this, dx, dz)
            dx = edge.x
            dz = edge.z
        }
        val movement = collide(dx, dy, dz)
        x += movement.x
        y += movement.y
        z += movement.z

        val xCollision = !Mth.equal(dx, movement.x)
        val zCollision = !Mth.equal(dz, movement.z)
        val verticalCollision = dy != movement.y
        val wasOnGround = onGround
        horizontalCollision = xCollision || zCollision
        onGround = verticalCollision && dy < 0.0
        updateSupport(movement)

        if (xCollision) {
            deltaX = 0.0
        }
        if (zCollision) {
            deltaZ = 0.0
        }
        if (verticalCollision) {
            deltaY = bounce(movement.y)
        }

        if (onGround) {
            if (!wasOnGround) {
                landingFall = (peakY - y) * fallScale()
            }
            peakY = y
        } else {
            peakY = maxOf(peakY, y)
        }

        val speedFactor = speedFactor()
        deltaX *= speedFactor
        deltaZ *= speedFactor
    }

    /**
     * @agent claude-fable-5-2
     */
    private fun isAboveGround(): Boolean {
        val fallen = peakY - y
        return onGround || fallen < STEP_HEIGHT && !canFall(0.0, 0.0, STEP_HEIGHT - fallen)
    }

    private fun updateSupport(movement: Vec3) {
        if (!onGround) {
            support.clear()
            groundWithoutBlocks = false
            return
        }

        findSupport(0.0, 0.0)
        // Moved off the edge after landing: the block left behind still counts.
        if (!hasSupport && !groundWithoutBlocks) {
            findSupport(movement.x, movement.z)
        }
        groundWithoutBlocks = !hasSupport
    }

    /**
     * Vertical motion after hitting a floor or ceiling: none, or the rebound of a bouncy floor unless sneaking.
     */
    private fun bounce(moved: Double): Double {
        if (!onGround || sneaking) {
            return 0.0
        }

        val restitution = onPosLegacy().bounceRestitution.toDouble()
        if (-deltaY <= DEFAULT_BASE_GRAVITY || restitution <= 0.0) {
            return 0.0
        }

        val portion = moved / deltaY
        val compensation = portion * DEFAULT_BASE_GRAVITY
        val drag = Mth.lerp(portion, 1.0, BASE_VERTICAL_AIR_DRAG.toDouble())
        return (compensation - deltaY) * drag * restitution
    }

    /**
     * SlimeBlock.stepOn, which Entity.applyEffectsFromBlocks runs after the whole move.
     */
    private fun stepOn() {
        if (!sneaking && onPosLegacy().`is`(BlockKind.SLIME) && abs(deltaY) < SLIME_STEP_MOTION) {
            val scale = SLIME_STEP_BASE + abs(deltaY) * SLIME_STEP_FACTOR
            deltaX *= scale
            deltaZ *= scale
        }
    }

    private fun findSupport(backX: Double, backZ: Double) = support.find(colliders, x, y, z, backX, backZ)

    /**
     * Whether the player stands on the block at [x] [y] [z], the one Entity.checkSupportingBlock picks.
     */
    public fun standsOn(x: Int, y: Int, z: Int): Boolean =
        onGround && hasSupport && supportX == x && supportY == y && supportZ == z

    /**
     * How far the player could still move, the shorter way, and keep its box on the block.
     */
    public fun footing(blockX: Int, blockY: Int, blockZ: Int): Double =
        colliders.footing(blockX, blockY, blockZ, x, z, HALF_WIDTH)

    public companion object {
        public const val HALF_WIDTH: Double = DEFAULT_BB_WIDTH.toDouble() / 2.0
        public const val AIR_SPEED: Float = 0.02f
        public const val SPRINT_AIR_SPEED: Float = 0.025999999f
        public const val PLAIN_FRICTION: Float = 0.6f

        // LivingEntity.INPUT_FRICTION, which is protected.
        public const val INPUT_FRICTION: Float = 0.98f

        /**
         * Motion of a player standing still on the ground, which gravity keeps pulling down every tick.
         */
        @JvmField
        public val RESTING: Vec3 = Vec3(0.0, -DEFAULT_BASE_GRAVITY * BASE_VERTICAL_AIR_DRAG, 0.0)

        internal const val HEIGHT = DEFAULT_BB_HEIGHT.toDouble()
        internal const val STEP_HEIGHT = 0.6f
        private const val JUMP_DELAY = 10
        private const val SPRINT_JUMP_BOOST = 0.2f
        private const val GROUND_FACTOR = 0.21600002f
        private const val SNEAK_SPEED = 0.3f
        private const val MIN_HORIZONTAL_SQR = 9.0E-6
        private const val SLIME_STEP_MOTION = 0.1
        private const val SLIME_STEP_BASE = 0.4
        private const val SLIME_STEP_FACTOR = 0.2
        private const val FEET_PROBE = 1.0E-3
    }

}
