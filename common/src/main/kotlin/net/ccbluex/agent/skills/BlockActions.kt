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

package net.ccbluex.agent.skills

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.isToolFor
import net.ccbluex.agent.navigation.isWearFree
import net.ccbluex.agent.navigation.seenPoint
import net.ccbluex.agent.pathfinder.walk.BreakTimes
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.item.ItemStack
import java.util.function.BooleanSupplier

/**
 * From the eyes to the point of a block to use or dig, a little under the 4.5 blocks the game allows.
 */
internal const val BLOCK_REACH = 4.2

/**
 * How near [reach] walks to a block's centre when it is out of sight from farther.
 */
private const val CLOSER = 2.8
private const val CLOSEST = 1.8

/**
 * The longest a turn to a block may take, in ticks.
 */
private const val AIM_TICKS = 20

/**
 * Walks until a point of the block at [pos] is in [reach] and in sight, on [face] when given: a look at it hits the
 * block first, through no other. Closer each try, as round a corner or over a ledge the block shows only from near;
 * false when it never came into sight or the way was blocked.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.reach(pos: BlockPos, face: Direction? = null, reach: Double = BLOCK_REACH): Boolean {
    if (seenPoint(pos, face, reach) != null) return true
    for (radius in doubleArrayOf(reach, CLOSER, CLOSEST)) {
        walkNear(Vec3.atCenterOf(pos), radius) { body.player.eyePosition.distanceTo(it) }
        if (seenPoint(pos, face, reach) != null) return true
    }
    return false
}

/**
 * The point of the block at [pos] within [reach] of the eyes that a look hits first, on [face] when given, turning
 * least from the rotation the server has; null when none is in sight.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.seenPoint(pos: BlockPos, face: Direction?, reach: Double = BLOCK_REACH): Vec3? {
    val player = body.player
    return body.level.seenPoint(player.eyePosition, pos, reach, face, body.sentRotation, player, body.viewStep)
}

/**
 * Walks to [goal] within [radius]; how the walk ended, or null when it took longer than a person would with a detour
 * or a dig on the way.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.goTo(goal: Vec3, radius: Double): Ending? {
    var ending: Ending? = null
    val limit = walkTicks(goal)
    navigator.moveTo(goal, radius) { ending = it }
    try {
        tickUntil { ticks -> ending != null || ticks >= limit || !body.player.isAlive }
    } finally {
        if (ending == null) navigator.stop()
    }
    return ending
}

/**
 * The longest a walk to [goal] may take: a person walks a block in about 5 ticks; a way around or a dig takes longer.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.walkTicks(goal: Vec3): Int = 200 + (body.player.position().distanceTo(goal) * 12).toInt()

/**
 * Walks until [distance] from [goal] is within [radius]; false when the way was blocked or took too long.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.walkNear(goal: Vec3, radius: Double, distance: (Vec3) -> Double): Boolean {
    if (distance(goal) <= radius) return true
    var ending: Ending? = null
    val limit = walkTicks(goal)
    navigator.moveTo(goal, (radius - 1.6).coerceAtLeast(1.0)) { ending = it }
    try {
        tickUntil { ticks -> ending != null || ticks >= limit || distance(goal) <= radius - 0.3 || !body.player.isAlive }
    } finally {
        if (ending == null) navigator.stop()
    }
    return distance(goal) <= radius
}

/**
 * Looks at a point of the block at [pos] in sight, on [face] when given, until the rotation the server has hits it
 * first, as a click or a dig carries that rotation; the hit then, or null when the block went out of sight or it
 * took too long. The [hotbar] slot, unless -1, stays selected meanwhile, as bodies let go of a slot not asked for
 * every tick.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.aimAt(pos: BlockPos, face: Direction? = null, hotbar: Int = -1): BlockHitResult? {
    var hit: BlockHitResult? = null
    tickUntil { ticks ->
        takeMovement()
        if (hotbar >= 0) body.selectHotbarSlot(hotbar)
        hit = sentHit(pos, face)
        val point = seenPoint(pos, face)
        if (hit == null && point != null) {
            body.look(ViewRotation.fromVector(point.subtract(body.player.eyePosition)), LookPurpose.AIM)
        }
        hit != null || point == null || ticks >= AIM_TICKS
    }
    return hit
}

/**
 * What a look with the rotation the server has hits first, if that is the block at [pos], on [face] when given.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.sentHit(pos: BlockPos, face: Direction?): BlockHitResult? {
    val player = body.player
    val eye = player.eyePosition
    val end = eye.add(body.sentRotation.viewVector.scale(player.blockInteractionRange()))
    val hit = body.level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, player))
    return hit.takeIf { it.type == HitResult.Type.BLOCK && it.blockPos == pos && (face == null || it.direction == face) }
}

/**
 * Right-clicks [pos], as to open a table, a furnace or a chest or to lie in a bed, until [done] holds; false when
 * it could not get there or nothing came of three clicks.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.useBlock(pos: BlockPos, done: BooleanSupplier): Boolean = useItemOn(-1, pos, null, done)

/**
 * Right-clicks [pos] on [face], or on any face in sight without one, holding inventory [slot], or what is held
 * for -1, until [done] holds; false when it could not get there or nothing came of three clicks.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.useItemOn(slot: Int, pos: BlockPos, face: Direction?, done: BooleanSupplier): Boolean {
    if (done.asBoolean) return true
    if (!reach(pos, face)) return false
    try {
        val hotbar = if (slot >= 0) body.hold(slot) else -1
        repeat(3) {
            val hit = aimAt(pos, face, hotbar) ?: return@repeat
            if (hotbar >= 0) body.selectHotbarSlot(hotbar)
            navigator.useItemOn(InteractionHand.MAIN_HAND, hit)
            body.swing(InteractionHand.MAIN_HAND)
            val timedOut = tickConditional(20) {
                takeMovement()
                if (hotbar >= 0) body.selectHotbarSlot(hotbar)
                done.asBoolean
            }
            if (!timedOut) return true
        }
    } finally {
        if (slot >= 0) body.releaseHotbarSlot()
    }
    return done.asBoolean
}

/**
 * Places the block held in inventory [slot] at [pos], against a solid neighbour whose face towards it is in sight;
 * false when there is none to place against, none came into sight, or the server did not keep it.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.placeBlock(slot: Int, pos: BlockPos): Boolean {
    val level = body.level
    if (!level.getBlockState(pos).canBeReplaced()) return false
    val supports = (listOf(Direction.DOWN) + Direction.Plane.HORIZONTAL + Direction.UP)
        .filter { !level.getBlockState(pos.relative(it)).getCollisionShape(level, pos.relative(it)).isEmpty }
    if (supports.isEmpty()) return false
    fun seenSupport() = supports.firstOrNull { seenPoint(pos.relative(it), it.opposite) != null }
    val support = seenSupport() ?: supports.firstOrNull { reach(pos.relative(it), it.opposite) }?.let { seenSupport() } ?: return false
    val against = pos.relative(support)
    val face = support.opposite
    if (body.player.boundingBox.intersects(AABB(pos))) return false
    try {
        val hotbar = body.hold(slot)
        val hit = aimAt(against, face, hotbar) ?: return false
        body.selectHotbarSlot(hotbar)
        navigator.useItemOn(InteractionHand.MAIN_HAND, hit)
        body.swing(InteractionHand.MAIN_HAND)
        return !tickConditional(20) { takeMovement(); !level.getBlockState(pos).canBeReplaced() }
    } finally {
        body.releaseHotbarSlot()
    }
}

/**
 * Digs [pos] with the carried item that digs it best, looking at a point of it in sight, from nearer when the look
 * does not get onto it from where the player stands; false when it could not get there or see it, the block cannot be
 * dug, or, with [forDrops], nothing carried makes it drop anything.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.dig(pos: BlockPos, forDrops: Boolean = true): Boolean = dig(pos, forDrops, ::diggingSlot)

/**
 * [dig] with the carried slot [slotFor] picks for the block, -1 for none in particular.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.dig(pos: BlockPos, forDrops: Boolean, slotFor: (BlockState) -> Int): Boolean {
    val level = body.level
    if (level.getBlockState(pos).isAir) return true
    if (forDrops && !canHarvest(level.getBlockState(pos))) return false
    if (!reach(pos)) return false
    val state = level.getBlockState(pos)
    if (state.isAir) return true
    val slot = slotFor(state)
    try {
        val hotbar = if (slot >= 0) body.hold(slot) else -1.also { body.releaseHotbarSlot() }
        waitTicks(1)
        val held = if (slot >= 0) body.player.inventory.getItem(slot) else ItemStack.EMPTY
        val ticksToBreak = BreakTimes.capture(listOf(held)).applyAsDouble(state)
        if (!ticksToBreak.isFinite()) return false
        // Some ticks over what the server counts, for the latency and a slower start
        val limit = ticksToBreak.toInt() + 20
        var started = false
        val centre = Vec3.atCenterOf(pos)
        var aimed = aimAt(pos, null, hotbar) != null
        if (!aimed && body.player.eyePosition.distanceTo(centre) > CLOSEST) {
            walkNear(centre, CLOSEST) { body.player.eyePosition.distanceTo(it) }
            aimed = aimAt(pos, null, hotbar) != null
        }
        if (!aimed) return false
        tickUntil { ticks ->
            if (level.getBlockState(pos).isAir || ticks > limit) return@tickUntil true
            takeMovement()
            if (hotbar >= 0) body.selectHotbarSlot(hotbar)
            val point = seenPoint(pos, null) ?: return@tickUntil true
            body.look(ViewRotation.fromVector(point.subtract(body.player.eyePosition)), LookPurpose.AIM)
            // Only while the rotation the server has hits the block itself, never through another
            val hit = sentHit(pos, null) ?: return@tickUntil false
            if (started) {
                navigator.continueDestroy(pos, hit.direction)
            } else {
                started = navigator.startDestroy(pos, hit.direction)
            }
            false
        }
        return level.getBlockState(pos).isAir
    } finally {
        body.stopDestroy()
        body.releaseHotbarSlot()
    }
}

/**
 * Whether [state] drops something when dug with what the agent carries.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.canHarvest(state: BlockState): Boolean {
    if (!state.requiresCorrectToolForDrops()) return true
    val inventory = body.player.inventory
    return (0 until INVENTORY_SLOTS).any { inventory.getItem(it).isCorrectToolForDrops(state) }
}

/**
 * The carried slot to dig [state] with: the fastest tool made for it, else one holding no tool, the selected one
 * first, as any other tool only wears out on it; -1 when everything carried is a tool.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.diggingSlot(state: BlockState): Int {
    val inventory = body.player.inventory
    val slots = 0 until INVENTORY_SLOTS
    slots.filter { isToolFor(inventory.getItem(it), state) }.maxByOrNull { inventory.getItem(it).getDestroySpeed(state) }
        ?.let { return it }
    val selected = inventory.selectedSlot
    if (isWearFree(inventory.getItem(selected))) return selected
    return slots.firstOrNull { isWearFree(inventory.getItem(it)) } ?: -1
}
