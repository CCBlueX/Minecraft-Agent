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

package net.ccbluex.agent.skills.build

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.isSeen
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.BLOCK_REACH
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.reach
import net.ccbluex.agent.skills.sentHit
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.item.ItemStack

/**
 * Whether [actual] is the block of [wanted], placed the same way; air is built where anything replaceable is.
 *
 * @agent claude-opus-5-5
 */
internal fun isBuilt(actual: BlockState, wanted: BlockState): Boolean {
    if (wanted.isAir) return actual.canBeReplaced()
    return actual.block == wanted.block && actual.placedValues == wanted.placedValues
}

/**
 * A right-click on [face] of [against] at [point], which gives the wanted block when made from [stand] or null for
 * nowhere to stand; [needsScaffold] when the stand is over air.
 */
private class Click(val against: BlockPos, val face: Direction, val point: Vec3, val stand: Vec3?, val needsScaffold: Boolean)

/**
 * Places the carried block [wanted] at [pos] as it is wanted: from where the player is when a click from there gives
 * it, else from the nearest spots on the side it has to be placed from, up on [scaffold] where the ground shows no
 * face and a spare block is carried. False when no click gives it or none went through.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.place(pos: BlockPos, wanted: BlockState, scaffold: Scaffold): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.of(wanted.block.asItem()))
    if (slot < 0) return false
    val stack = body.player.inventory.getItem(slot).copy()
    val clicks = clicks(pos, wanted, stack, scaffold)
    val usable = if (scaffold.hasSpare()) clicks else clicks.filter { !it.needsScaffold }
    if (usable.size < clicks.size) scaffold.lacking += pos
    for ((stand, group) in usable.groupBy { it.stand }.entries.take(3)) {
        if (group.none { fits(it, pos, wanted, stack) }) {
            if (stand == null) continue
            // Scaffold put up for another spot comes down first, so its block can go under this one
            scaffold.clear(BlockPos.containing(stand).below())
            stepTo(stand, scaffold)
        }
        for (click in group) {
            if (!reach(click.against, click.face) || !fits(click, pos, wanted, stack)) continue
            val ok = placeWith(slot, click, pos, wanted, stack)
            if (ok) return true
        }
    }
    return false
}

/**
 * Walks onto [stand], closer than `walkNear` goes: from a block off, the player could still stand where the block
 * goes, on the edge of the layer below. Over air, a spare block from [scaffold] goes under it first.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.stepTo(stand: Vec3, scaffold: Scaffold) {
    val support = BlockPos.containing(stand).below()
    // From beside it rather than by the walk's own pillar up, which fails when it starts on or next to the spot
    if (body.level.getBlockState(support).canBeReplaced() && !scaffold.put(support)) return
    // Within 0.8: on the low half of a stair the feet are half a block under the spot
    scaffold.walkTo(stand, 0.8)
}

/**
 * The clicks that place [stack] at [pos] as [wanted], against each solid neighbour and from each side, nearest first
 * and those that need [scaffold] last.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.clicks(pos: BlockPos, wanted: BlockState, stack: ItemStack, scaffold: Scaffold): List<Click> {
    val level = body.level
    val clicks = ArrayList<Click>()
    for (side in Direction.entries) {
        val against = pos.relative(side)
        if (level.getBlockState(against).getCollisionShape(level, against).isEmpty) continue
        val face = side.opposite
        val centre = Vec3.atCenterOf(against).relative(face, 0.5)
        // The upper or lower half of a side, as slabs and stairs take their half from it
        val points = if (face.axis.isVertical) listOf(centre) else listOf(centre, centre.add(0.0, 0.25, 0.0), centre.add(0.0, -0.25, 0.0))
        for (point in points) {
            for (looking in Direction.Plane.HORIZONTAL) {
                val eyeHeight = body.player.eyeHeight.toDouble()
                // The lowest spot on that side the face shows from, else here if it shows from here
                val stand = standSpots(point.relative(looking, -2.5), pos, scaffold)
                    .firstOrNull { canSee(it.add(0.0, eyeHeight, 0.0), point, against, face) }
                val eye = stand?.add(0.0, eyeHeight, 0.0) ?: body.player.eyePosition
                if (stand == null && !canSee(eye, point, against, face)) continue
                val placed = predict(stack, against, face, point, ViewRotation.fromVector(point.subtract(eye))) ?: continue
                if (!isBuilt(placed, wanted)) continue
                val needsScaffold = stand != null && level.getBlockState(BlockPos.containing(stand).below()).canBeReplaced()
                clicks += Click(against, face, point, stand, needsScaffold)
            }
        }
    }
    val position = body.player.position()
    return clicks.sortedWith(compareBy({ it.needsScaffold }, { it.stand?.distanceToSqr(position) ?: Double.MAX_VALUE }))
}

/**
 * Where the player can stand in the column of [near], lowest first from a little under [pos] up, as the ground is
 * easier to walk to than the top of what is built; never in [pos] itself, and over air only where [scaffold] can
 * fill it.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.standSpots(near: Vec3, pos: BlockPos, scaffold: Scaffold): Sequence<Vec3> {
    val level = body.level
    val column = BlockPos.containing(near.x, pos.y.toDouble(), near.z)
    fun isEmpty(at: BlockPos) = at != pos && level.getBlockState(at).getCollisionShape(level, at).isEmpty
    return (-3..1).asSequence()
        .map { column.above(it) }
        .filter { isEmpty(it) && isEmpty(it.above()) && (!isEmpty(it.below()) || scaffold.canPlace(it.below())) }
        .map(Vec3::atBottomCenterOf)
}

/**
 * Whether [click] made from where the player is now shows and gives [wanted], even when the turn to it ends a little
 * off.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.fits(click: Click, pos: BlockPos, wanted: BlockState, stack: ItemStack): Boolean {
    val player = body.player
    val eye = player.eyePosition
    if (player.boundingBox.intersects(AABB(pos)) || eye.distanceTo(click.point) > BLOCK_REACH) return false
    if (!canSee(eye, click.point, click.against, click.face)) return false
    val aim = ViewRotation.fromVector(click.point.subtract(eye))
    return floatArrayOf(-8f, 0f, 8f).all { turn ->
        val placed = predict(stack, click.against, click.face, click.point, ViewRotation(aim.yRot + turn, aim.xRot))
        placed != null && isBuilt(placed, wanted)
    }
}

/**
 * Turns to [click] until the rotation the server has hits its face there, checks that this rotation gives [wanted],
 * and places the block held in inventory [slot] through the navigator, which sends no click through other blocks;
 * true once [pos] holds it.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.placeWith(slot: Int, click: Click, pos: BlockPos, wanted: BlockState, stack: ItemStack): Boolean {
    val level = body.level
    try {
        val hotbar = body.hold(slot)
        var sent: BlockHitResult? = null
        tickUntil { ticks ->
            takeMovement()
            body.selectHotbarSlot(hotbar)
            sent = sentHit(click.against, click.face)
            // At the click's own point rather than aimAt's least turn, as the half of a side gives slabs and stairs
            // their half; from the eyes of this tick, since the player may still be landing on a block it pillared
            if (sent == null) body.look(ViewRotation.fromVector(click.point.subtract(body.player.eyePosition)), LookPurpose.AIM)
            sent != null || ticks >= 20
        }
        val hit = sent ?: return false
        val placed = predict(stack, click.against, click.face, hit.location, body.sentRotation)
        if (placed == null || !isBuilt(placed, wanted)) return false
        if (!navigator.useItemOn(InteractionHand.MAIN_HAND, hit)) return false
        body.swing(InteractionHand.MAIN_HAND)
        tickConditional(20) { takeMovement(); !level.getBlockState(pos).canBeReplaced() }
        return isBuilt(level.getBlockState(pos), wanted)
    } finally {
        body.releaseHotbarSlot()
    }
}

/**
 * Whether a look from [eye] at [point] hits [face] of [against] first: a click never goes through another block.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.canSee(eye: Vec3, point: Vec3, against: BlockPos, face: Direction): Boolean =
    body.level.isSeen(eye, point, against, face, body.player)

/**
 * The block [stack] places with a click on [face] of [against] at [point] while looking with [rotation]; null when
 * it places none.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.predict(stack: ItemStack, against: BlockPos, face: Direction, point: Vec3, rotation: ViewRotation): BlockState? =
    body.level.placedState(stack, BlockHitResult(point, face, against, false), rotation)
