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
import net.ccbluex.agent.skills.sentHit
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.ChestType
import net.ccbluex.agent.world.item.ItemStack

/**
 * Whether the block at [pos] is [wanted] as built: placed the same way, clicked the same way and holding the same
 * liquid; for a liquid, its source; air is built where anything replaceable is.
 *
 * @agent claude-opus-5-5
 */
internal fun isBuilt(level: BlockGetter, pos: BlockPos, wanted: BlockState): Boolean {
    val actual = level.getBlockState(pos)
    if (wanted.isAir) return actual.canBeReplaced()
    if (isLiquid(wanted)) return actual.block == wanted.block && actual.fluidState.isSource
    return isPlaced(level, pos, actual, wanted) && actual.usedValues == wanted.usedValues &&
        actual.fluidState.type == wanted.fluidState.type
}

/**
 * Whether [actual] at [pos] is the block of [wanted], placed the same way. Half of a double chest is placed right as
 * a single one while its other half is not there yet.
 *
 * @agent claude-opus-5-5
 */
internal fun isPlaced(level: BlockGetter, pos: BlockPos, actual: BlockState, wanted: BlockState): Boolean {
    if (actual.block != wanted.block) return false
    val type = wanted.getOptionalValue(BlockStateProperties.CHEST_TYPE)
    if (type == null || type == ChestType.SINGLE) return actual.placedValues == wanted.placedValues
    val facing = wanted.getValue(BlockStateProperties.HORIZONTAL_FACING)
    val partner = pos.relative(if (type == ChestType.LEFT) facing.getClockWise() else facing.getCounterClockWise())
    val expected = if (level.getBlockState(partner).block == wanted.block) wanted else wanted.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE)
    return actual.placedValues == expected.placedValues
}

/**
 * A right-click on [face] of [against] at [point], made from [stand] or, for null, from where the player is;
 * [needsScaffold] when the stand is over air.
 */
internal class Click(val against: BlockPos, val face: Direction, val point: Vec3, val stand: Vec3?, val needsScaffold: Boolean)

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
    val clicks = clicks(pos, scaffold) { click, rotation ->
        predict(stack, click.against, click.face, click.point, rotation)?.let { isPlaced(body.level, pos, it, wanted) } == true
    }
    val usable = if (scaffold.hasSpare()) clicks else clicks.filter { !it.needsScaffold }
    if (usable.size < clicks.size) scaffold.lacking += pos
    for ((stand, group) in usable.groupBy { it.stand }.entries.take(3)) {
        if (group.none { fits(it, pos, wanted, stack) }) {
            if (stand == null) continue
            // Scaffold put up for another spot comes down first, so its block can go under this one
            scaffold.clear(BlockPos.containing(stand).below())
            stepTo(stand, pos, scaffold)
            if (group.none { fits(it, pos, wanted, stack) }) scaffold.walkTo(stand, 0.2)
        }
        for (click in group) {
            if (!fits(click, pos, wanted, stack)) continue
            // Looked up anew each time, as holding it moves it into the hotbar
            val held = body.player.inventory.slotOf(ItemMatch.of(wanted.block.asItem()))
            if (held < 0) return false
            val ok = placeWith(held, click, pos, wanted, stack)
            if (ok) return true
        }
    }
    return false
}

/**
 * Whether some click places the carried block [wanted] at [pos] as it is wanted, from here or from a spot to stand;
 * false while the neighbours it has to be placed against are missing.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.hasClick(pos: BlockPos, wanted: BlockState, scaffold: Scaffold): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.of(wanted.block.asItem()))
    if (slot < 0) return false
    val stack = body.player.inventory.getItem(slot)
    return clicks(pos, scaffold) { click, rotation ->
        predict(stack, click.against, click.face, click.point, rotation)?.let { isPlaced(body.level, pos, it, wanted) } == true
    }.isNotEmpty()
}

/**
 * Walks onto [stand], closer than `walkNear` goes: from a block off, the player could still stand where the block
 * goes at [pos], on the edge of the layer below. Over air, a spare block from [scaffold] goes under it first.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.stepTo(stand: Vec3, pos: BlockPos, scaffold: Scaffold) {
    val support = BlockPos.containing(stand).below()
    // From beside it rather than by the walk's own pillar up, which fails when it starts on or next to the spot
    if (body.level.getBlockState(support).canBeReplaced() && !scaffold.put(support)) return
    // Within 0.8: on the low half of a stair the feet are half a block under the spot
    scaffold.walkTo(stand, 0.8)
    // Nearer when that left the player partly in the cell to fill
    if (body.player.boundingBox.intersects(AABB(pos))) scaffold.walkTo(stand, 0.3)
}

/**
 * The clicks on a face of a solid neighbour of [pos], or with [onItself] of the block at [pos], that [gives] what is
 * wanted there with the rotation from the eyes to the click, from spots on each side at one to three blocks or from
 * here; from spots nothing of the build covers first, then nearest, those that need [scaffold] last.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.clicks(
    pos: BlockPos,
    scaffold: Scaffold,
    onItself: Boolean = false,
    gives: (Click, ViewRotation) -> Boolean,
): List<Click> {
    val level = body.level
    val eyeHeight = body.player.eyeHeight.toDouble()
    val clicks = ArrayList<Click>()
    for (side in Direction.entries) {
        val against = if (onItself) pos else pos.relative(side)
        val state = level.getBlockState(against)
        if (state.getCollisionShape(level, against).isEmpty) continue
        val face = if (onItself) side else side.opposite
        fun add(point: Vec3, stand: Vec3?) {
            val eye = stand?.add(0.0, eyeHeight, 0.0) ?: body.player.eyePosition
            val needsScaffold = stand != null && level.getBlockState(BlockPos.containing(stand).below()).canBeReplaced()
            val click = Click(against, face, point, stand, needsScaffold)
            if (gives(click, ViewRotation.fromVector(point.subtract(eye)))) clicks += click
        }
        for (point in facePoints(state.getShape(level, against).bounds(), against, face)) {
            if (canSee(body.player.eyePosition, point, against, face)) add(point, null)
            for (looking in Direction.Plane.HORIZONTAL) {
                // Also from right beside it, as over a wall or a ledge the face shows only from near
                for (distance in doubleArrayOf(1.5, 2.5, 3.5)) {
                    standSpots(point.relative(looking, -distance), pos, scaffold)
                        .firstOrNull { canSee(it.add(0.0, eyeHeight, 0.0), point, against, face) }
                        ?.let { add(point, it) }
                }
            }
        }
    }
    val position = body.player.position()
    // From spots with nothing of the build over them first, as a player under it gets walled in
    return clicks.sortedWith(
        compareBy({ it.needsScaffold }, { it.stand?.let { stand -> scaffold.isCovered(stand) } }, { it.stand?.distanceToSqr(position) ?: -1.0 }),
    )
}

/**
 * Points to click on [face] of the outline [bounds] of the block at [against]: its middle and, on a side, its upper
 * and lower half, as slabs and stairs take their half from it; on the top or bottom also beside each edge, as the
 * top of a hopper or a composter is a rim around a hollow. On the outline rather than the block's edge, as a look at
 * the edge of a chest or a hopper passes in front of it.
 *
 * @agent claude-opus-5-5
 */
internal fun facePoints(bounds: AABB, against: BlockPos, face: Direction): List<Vec3> {
    fun middle(axis: Direction.Axis) = when (axis) {
        face.axis -> if (face.isPositive) bounds.max(axis) else bounds.min(axis)
        else -> (bounds.min(axis) + bounds.max(axis)) / 2
    }
    val centre = Vec3(against.x + middle(Direction.Axis.X), against.y + middle(Direction.Axis.Y), against.z + middle(Direction.Axis.Z))
    if (face.axis.isVertical) {
        val x = (bounds.maxX - bounds.minX) / 2 - 1.0 / 16
        val z = (bounds.maxZ - bounds.minZ) / 2 - 1.0 / 16
        return listOf(centre, centre.add(x, 0.0, 0.0), centre.add(-x, 0.0, 0.0), centre.add(0.0, 0.0, z), centre.add(0.0, 0.0, -z))
    }
    val halves = listOf(0.25, -0.25).map { centre.add(0.0, it, 0.0) }
        .filter { it.y - against.y in bounds.minY + 0.05..bounds.maxY - 0.05 }
    return listOf(centre) + halves
}

/**
 * Where the player can stand in the column of [near], lowest first from a little under [pos] up, as the ground is
 * easier to walk to than the top of what is built; never in [pos] itself nor in a liquid, never on a block hollow in
 * the middle such as a composter, and over air only where [scaffold] can fill it.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.standSpots(near: Vec3, pos: BlockPos, scaffold: Scaffold): Sequence<Vec3> {
    val level = body.level
    val column = BlockPos.containing(near.x, pos.y.toDouble(), near.z)
    fun isEmpty(at: BlockPos) = level.getBlockState(at).getCollisionShape(level, at).isEmpty
    fun isFree(at: BlockPos) = at != pos && isEmpty(at) && level.getBlockState(at).fluidState.isEmpty
    fun isFooting(at: BlockPos) = level.getBlockState(at).getCollisionShape(level, at).toAabbs()
        .any { it.minX <= 0.5 && it.maxX >= 0.5 && it.minZ <= 0.5 && it.maxZ >= 0.5 && it.maxY >= 0.5 }
    return (-3..1).asSequence()
        .map { column.above(it) }
        .filter { isFree(it) && isFree(it.above()) && (isFooting(it.below()) || isEmpty(it.below()) && scaffold.canPlace(it.below())) }
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
        placed != null && isPlaced(body.level, pos, placed, wanted)
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
    if (body.containerMenu != null) body.closeContainer()
    try {
        val hotbar = body.hold(slot)
        var sent: BlockHitResult? = null
        var crouched = 0
        tickUntil { ticks ->
            // Sneaking, so a block clicked on, such as a chest or a repeater, does not take the click itself
            press(SNEAKING, true)
            crouched = if (body.player.isShiftKeyDown) crouched + 1 else 0
            body.selectHotbarSlot(hotbar)
            sent = sentHit(click.against, click.face)
            // At the click's own point rather than aimAt's least turn, as the half of a side gives slabs and stairs
            // their half; from the eyes of this tick, since the player may still be landing on a block it pillared
            if (sent == null) body.look(ViewRotation.fromVector(click.point.subtract(body.player.eyePosition)), LookPurpose.AIM)
            // The server learns of the sneak a tick after the client
            sent != null && crouched >= 2 || ticks >= 20
        }
        val hit = sent?.takeIf { crouched >= 2 } ?: return false
        val placed = predict(stack, click.against, click.face, hit.location, body.sentRotation)
        if (placed == null || !isPlaced(body.level, pos, placed, wanted)) return false
        if (!navigator.useItemOn(InteractionHand.MAIN_HAND, hit)) return false
        body.swing(InteractionHand.MAIN_HAND)
        tickConditional(20) { press(SNEAKING, true); !level.getBlockState(pos).canBeReplaced() }
        return isPlaced(level, pos, level.getBlockState(pos), wanted)
    } finally {
        body.releaseHotbarSlot()
    }
}

/**
 * Whether a look from [eye] at [point] hits [face] of [against] first, from well in front of the face rather than
 * along it: a click never goes through another block, and one past the edge of the face may miss it.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.canSee(eye: Vec3, point: Vec3, against: BlockPos, face: Direction): Boolean =
    eye.subtract(point).dot(face.unitVec3) > 0.2 && body.level.isSeen(eye, point, against, face, body.player)

/**
 * Whether a block at [cell] would take away every spot to place the carried block [wanted] at [pos] from: each
 * click from there looks through it or stands in it. False when there is none anyway, or the block is not carried.
 *
 * @agent claude-opus-5-5
 */
internal fun Agent.wouldShut(cell: BlockPos, pos: BlockPos, wanted: BlockState, scaffold: Scaffold): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.of(wanted.block.asItem()))
    if (slot < 0) return false
    val stack = body.player.inventory.getItem(slot)
    val clicks = clicks(pos, scaffold) { click, rotation ->
        click.stand != null &&
            predict(stack, click.against, click.face, click.point, rotation)?.let { isPlaced(body.level, pos, it, wanted) } == true
    }
    if (clicks.isEmpty()) return false
    val box = AABB(cell)
    val player = body.player
    return clicks.all { click ->
        val feet = click.stand ?: return@all true
        box.clip(feet.add(0.0, player.eyeHeight.toDouble(), 0.0), click.point) != null ||
            player.boundingBox.move(feet.subtract(player.position())).intersects(box)
    }
}

/**
 * The block [stack] places with a click on [face] of [against] at [point] while looking with [rotation]; null when
 * it places none.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.predict(stack: ItemStack, against: BlockPos, face: Direction, point: Vec3, rotation: ViewRotation): BlockState? =
    body.level.placedState(stack, BlockHitResult(point, face, against, false), rotation)

private val SNEAKING = Input(false, false, false, false, false, true, false)
