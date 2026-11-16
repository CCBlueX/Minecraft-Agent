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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.simulation.PlayerMotion.Companion.HALF_WIDTH
import net.ccbluex.agent.simulation.PlayerMotion.Companion.HEIGHT
import net.ccbluex.agent.simulation.PlayerMotion.Companion.STEP_HEIGHT
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.Blocks
import kotlin.math.abs

// 1.8's movement: EntityLiving.m (aiStep), EntityLiving.g (travel), Entity.move, in float arithmetic where 1.8 uses it

private const val X = 0
private const val Y = 1
private const val Z = 2

// The client's key slowdowns, which a 1.8 server leaves to the client
private const val SNEAK_INPUT = 0.3f
private const val MIN_MOTION = 0.005

// The attribute modifier sprinting adds (multiply total) and the air speed it adds to
private const val SPRINT_MODIFIER = 0.30000001192092896
private const val AIR_SPEED = 0.02f

/**
 * One 1.8 tick: the keys as a 1.8 client sends them, then the server's aiStep.
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.tick1_8(yRot: Float, forward: Int, strafe: Int, jump: Boolean, sprint: Boolean, sneak: Boolean) {
    if (jumpDelay > 0) jumpDelay--
    if (abs(deltaX) < MIN_MOTION) deltaX = 0.0
    if (abs(deltaY) < MIN_MOTION) deltaY = 0.0
    if (abs(deltaZ) < MIN_MOTION) deltaZ = 0.0

    sneaking = sneak
    crouching = sneak
    // EntityPlayerSP stops sprinting below 0.8 forward (sneaking keys are 0.3) and on running into a wall
    isSprinting = sprint && forward > 0 && !sneak && !horizontalCollision

    if (!jump) {
        jumpDelay = 0
    } else if (onGround && jumpDelay == 0) {
        jump1_8(yRot)
        jumpDelay = JUMP_DELAY
    }

    var moveStrafe = strafe.toFloat()
    var moveForward = forward.toFloat()
    if (sneak) {
        moveStrafe *= SNEAK_INPUT
        moveForward *= SNEAK_INPUT
    }
    travel1_8(yRot, moveStrafe * PlayerMotion.INPUT_FRICTION, moveForward * PlayerMotion.INPUT_FRICTION)
    // EntityHuman.m sets the air speed after the move, so the next tick's air speed follows this tick's sprinting
    airSprint = isSprinting
}

private const val JUMP_DELAY = 10

/**
 * EntityLiving.bF (jump)
 */
private fun PlayerMotion.jump1_8(yRot: Float) {
    deltaY = traits.jumpStrength.toDouble() + traits.jumpBoostPower
    if (isSprinting) {
        val angle = yRot * 0.017453292f
        deltaX -= Mth.sin1_8(angle) * 0.2f
        deltaZ += Mth.cos1_8(angle) * 0.2f
    }
}

private fun PlayerMotion.travel1_8(yRot: Float, strafe: Float, forward: Float) {
    val friction = if (onGround) frictionBelow() * 0.91f else 0.91f
    val speed = if (onGround) {
        val walking = if (isSprinting) (traits.walkingSpeed * (1.0 + SPRINT_MODIFIER)).toFloat() else traits.walkingSpeed
        walking * (0.16277136f / (friction * friction * friction))
    } else if (airSprint) {
        (AIR_SPEED + AIR_SPEED * 0.3).toFloat()
    } else {
        AIR_SPEED
    }
    accelerate1_8(yRot, strafe, forward, speed)
    move1_8(deltaX, deltaY, deltaZ)
    deltaY -= 0.08
    deltaY *= 0.98f
    deltaX *= friction
    deltaZ *= friction
}

/**
 * The slipperiness of the block under the centre, as 1.8 picks it: at `floor(minY) - 1`.
 */
private fun PlayerMotion.frictionBelow(): Float = terrain.state(Mth.floor(x), Mth.floor(y) - 1, Mth.floor(z)).block.friction

/**
 * Entity.a(float, float, float) (moveFlying)
 */
private fun PlayerMotion.accelerate1_8(yRot: Float, strafe: Float, forward: Float, speed: Float) {
    var length = strafe * strafe + forward * forward
    if (length < 1.0E-4f) return
    length = Mth.sqrt(length)
    if (length < 1f) length = 1f
    length = speed / length
    val scaledStrafe = strafe * length
    val scaledForward = forward * length
    val angle = yRot * Mth.PI / 180f
    val sin = Mth.sin1_8(angle)
    val cos = Mth.cos1_8(angle)
    deltaX += (scaledStrafe * cos - scaledForward * sin).toDouble()
    deltaZ += (scaledForward * cos + scaledStrafe * sin).toDouble()
}

private fun PlayerMotion.box() = AABB(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH)

/**
 * Entity.move as Spigot 1.8.8 runs it: block effects first, then the sneak edge, Y X Z collisions and the step up.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun PlayerMotion.move1_8(wantedX: Double, wantedY: Double, wantedZ: Double) {
    var dx = wantedX
    var dy = wantedY
    var dz = wantedZ
    // Entity.checkBlockCollisions: cobwebs slow this move, soul sand the motion after it
    var box = box()
    if (touchBlocks(box)) {
        dx *= 0.25
        dy *= 0.05f
        dz *= 0.25
        deltaX = 0.0
        deltaY = 0.0
        deltaZ = 0.0
    }
    if (dx == 0.0 && dy == 0.0 && dz == 0.0) return

    val startX = x
    val startZ = z
    val edge = onGround && sneaking
    if (edge) {
        while (dx != 0.0 && isFree(box.move(dx, -1.0, 0.0))) dx = backOff(dx)
        while (dz != 0.0 && isFree(box.move(0.0, -1.0, dz))) dz = backOff(dz)
        while (dx != 0.0 && dz != 0.0 && isFree(box.move(dx, -1.0, dz))) {
            dx = backOff(dx)
            dz = backOff(dz)
        }
    }
    val wantX = dx
    val wantY = dy
    val wantZ = dz

    val start = box
    colliders.collect(box.expandTowards(dx, dy, dz))
    dy = colliders.offset(Y, box, dy)
    box = box.move(0.0, dy, 0.0)
    val grounded = onGround || wantY != dy && wantY < 0.0
    dx = colliders.offset(X, box, dx)
    box = box.move(dx, 0.0, 0.0)
    dz = colliders.offset(Z, box, dz)
    box = box.move(0.0, 0.0, dz)

    if (grounded && (wantX != dx || wantZ != dz)) {
        val step = STEP_HEIGHT.toDouble()
        colliders.collect(start.expandTowards(wantX, step, wantZ))
        // Up first over the box swept sideways, then sideways
        var wide = start
        val wideUp = colliders.offset(Y, start.expandTowards(wantX, 0.0, wantZ), step)
        wide = wide.move(0.0, wideUp, 0.0)
        val wideX = colliders.offset(X, wide, wantX)
        wide = wide.move(wideX, 0.0, 0.0)
        val wideZ = colliders.offset(Z, wide, wantZ)
        wide = wide.move(0.0, 0.0, wideZ)
        // Up over the box where it stands, then sideways
        var narrow = start
        val narrowUp = colliders.offset(Y, narrow, step)
        narrow = narrow.move(0.0, narrowUp, 0.0)
        val narrowX = colliders.offset(X, narrow, wantX)
        narrow = narrow.move(narrowX, 0.0, 0.0)
        val narrowZ = colliders.offset(Z, narrow, wantZ)
        narrow = narrow.move(0.0, 0.0, narrowZ)

        val stepX: Double
        val stepZ: Double
        var stepped: AABB
        var down: Double
        if (wideX * wideX + wideZ * wideZ > narrowX * narrowX + narrowZ * narrowZ) {
            stepX = wideX
            stepZ = wideZ
            down = -wideUp
            stepped = wide
        } else {
            stepX = narrowX
            stepZ = narrowZ
            down = -narrowUp
            stepped = narrow
        }
        down = colliders.offset(Y, stepped, down)
        stepped = stepped.move(0.0, down, 0.0)
        if (dx * dx + dz * dz < stepX * stepX + stepZ * stepZ) {
            dx = stepX
            dy = down
            dz = stepZ
            box = stepped
        }
    }

    x = (box.minX + box.maxX) / 2.0
    y = box.minY
    z = (box.minZ + box.maxZ) / 2.0
    horizontalCollision = wantX != dx || wantZ != dz
    val vertical = wantY != dy
    val wasOnGround = onGround
    onGround = vertical && wantY < 0.0
    updateSupport(Vec3(dx, dy, dz))
    if (onGround) {
        if (!wasOnGround) landingFall = if (landedOn().`is`(BlockKind.SLIME)) 0.0 else peakY - y
        peakY = y
    } else {
        peakY = maxOf(peakY, y)
    }

    if (wantX != dx) deltaX = 0.0
    if (wantZ != dz) deltaZ = 0.0
    val landed = landedOn()
    if (vertical) {
        // Block.a(World, Entity) (onLanded): a slime floor bounces unless sneaking
        deltaY = if (landed.`is`(BlockKind.SLIME) && !sneaking && deltaY < 0.0) -deltaY else 0.0
    }
    if (!edge) stepEffects(startX, startZ, landed)
}

private fun backOff(movement: Double): Double = when {
    movement < 0.05 && movement >= -0.05 -> 0.0
    movement > 0.0 -> movement - 0.05
    else -> movement + 0.05
}

/**
 * World.getCubes(...).isEmpty() for blocks only.
 */
private fun PlayerMotion.isFree(box: AABB): Boolean {
    for (bx in Mth.floor(box.minX) until Mth.floor(box.maxX + 1.0)) {
        for (bz in Mth.floor(box.minZ) until Mth.floor(box.maxZ + 1.0)) {
            for (by in Mth.floor(box.minY) - 1 until Mth.floor(box.maxY + 1.0)) {
                if (colliders.intersects(bx, by, bz, box)) return false
            }
        }
    }
    return true
}

/**
 * Entity.checkBlockCollisions over the box shrunk by 0.001: soul sand scales the motion once per block, and whether
 * a cobweb is among the blocks.
 */
private fun PlayerMotion.touchBlocks(box: AABB): Boolean {
    var web = false
    for (bx in Mth.floor(box.minX + 0.001)..Mth.floor(box.maxX - 0.001)) {
        for (by in Mth.floor(box.minY + 0.001)..Mth.floor(box.maxY - 0.001)) {
            for (bz in Mth.floor(box.minZ + 0.001)..Mth.floor(box.maxZ - 0.001)) {
                val block = terrain.state(bx, by, bz).block
                if (block === Blocks.COBWEB) web = true
                if (block.speedFactor != 1f) {
                    deltaX *= block.speedFactor.toDouble()
                    deltaZ *= block.speedFactor.toDouble()
                }
            }
        }
    }
    return web
}

/**
 * The block Entity.move hands to the landing and step effects: 0.2 under the feet, or a fence, wall or gate below
 * that air.
 */
private fun PlayerMotion.landedOn(): net.ccbluex.agent.world.block.Block {
    val bx = Mth.floor(x)
    val by = Mth.floor(y - 0.2f)
    val bz = Mth.floor(z)
    val state = terrain.state(bx, by, bz)
    if (!state.isAir) return state.block
    val below = terrain.state(bx, by - 1, bz).block
    return if (below.`is`(BlockKind.FENCE) || below.`is`(BlockKind.WALL) || below.`is`(BlockKind.FENCE_GATE)) below else
        state.block
}

/**
 * Block.a(World, BlockPosition, Entity) at every step sound: slime slows the walk when the motion is low.
 */
private fun PlayerMotion.stepEffects(startX: Double, startZ: Double, block: net.ccbluex.agent.world.block.Block) {
    val movedX = x - startX
    val movedZ = z - startZ
    walked = (walked + Math.sqrt(movedX * movedX + movedZ * movedZ).toFloat() * 0.6).toFloat()
    if (walked <= nextStep || block === Blocks.AIR || block === Blocks.VOID_AIR) return
    nextStep = walked.toInt() + 1
    if (block.`is`(BlockKind.SLIME) && abs(deltaY) < 0.1 && !sneaking) {
        val scale = 0.4 + abs(deltaY) * 0.2
        deltaX *= scale
        deltaZ *= scale
    }
}
