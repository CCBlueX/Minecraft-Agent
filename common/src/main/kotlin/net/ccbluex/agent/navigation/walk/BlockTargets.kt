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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.clickPoint
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.hotbar
import net.ccbluex.agent.navigation.isSeen
import net.ccbluex.agent.navigation.isToolFor
import net.ccbluex.agent.navigation.isWearFree
import net.ccbluex.agent.navigation.pick
import net.ccbluex.agent.navigation.seenPoint
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.item.ItemStack
import kotlin.math.abs

/**
 * Where to look and click to use or place blocks, and which slot to hold for it.
 *
 * @agent claude-opus-5-5
 */
internal class BlockTargets(private val context: NavigationContext) {

    /**
     * A placement at [pos]: a click on [face] of [against] at [point], seen with [rotation], holding [slot] (-1 for
     * the offhand).
     */
    class Plan(
        val pos: BlockPos,
        val against: BlockPos,
        val face: Direction,
        val point: Vec3,
        val rotation: ViewRotation,
        val slot: Int,
    ) {
        val hand: InteractionHand
            get() = if (slot < 0) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
    }

    private val player get() = context.body.player
    private val level get() = context.body.level

    /**
     * A rotation that sees a point of the block at [pos] within reach, turning least from the rotation sent last.
     */
    fun aimAt(pos: BlockPos): ViewRotation? {
        val eye = player.eyePosition
        val body = context.body
        val reach = player.blockInteractionRange()
        val point = level.seenPoint(eye, pos, reach, null, body.sentRotation, player, body.viewStep)
        return point?.let { ViewRotation.fromVector(it.subtract(eye)) }
    }

    /**
     * What the player hits looking with [rotation], up to [reach] blocks away.
     */
    fun raycast(rotation: ViewRotation, reach: Double): BlockHitResult = player.pick(rotation, reach)

    /**
     * The click that places a block at [pos] against a neighbour, turning least from the rotation sent last. Below
     * the feet, where no way is better than another, and when [nearest], the point on the face nearest to the current
     * look; else the [clickPoint] of the face, its middle when that point cannot be seen.
     */
    fun planPlacement(pos: BlockPos, slot: Int, nearest: Boolean = false): Plan? {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return null
        }
        val eye = player.eyePosition
        val reach = player.blockInteractionRange()
        val feet = player.blockPosition()
        val underFeet = nearest || pos.x == feet.x && pos.z == feet.z && pos.y <= feet.y

        var best: Plan? = null
        var bestTurn = Float.MAX_VALUE
        for (direction in Direction.entries) {
            val against = pos.relative(direction)
            val state = level.getBlockState(against)
            if (state.canBeReplaced() || state.getShape(level, against).isEmpty) {
                continue
            }
            val face = direction.opposite
            val center = Vec3.atCenterOf(against).add(face.unitVec3.scale(HALF))
            val outward = eye.subtract(center).dot(face.unitVec3)
            if (outward <= 0.0) {
                continue
            }
            val click = clickPoint(against, face, player.uuid.mostSignificantBits xor against.asLong() + face.ordinal)
            val point = when {
                underFeet -> nearestOnFace(against, face, eye)
                eye.distanceToSqr(click) <= reach * reach && sees(eye, click, against, face) -> click
                else -> center
            }
            if (eye.distanceToSqr(point) > reach * reach || !sees(eye, point, against, face)) {
                continue
            }
            val rotation = ViewRotation.fromVector(point.subtract(eye))
            val turn = turn(rotation)
            if (turn < bestTurn) {
                best = Plan(pos, against, face, point, rotation, slot)
                bestTurn = turn
            }
        }
        return best
    }

    /**
     * The click [rotation] makes from the eye as it is, when the face it hits first places a block at [pos].
     *
     * @agent claude-opus-5-5
     */
    fun heldClick(pos: BlockPos, rotation: ViewRotation): Plan? {
        val slot = placeableSlot() ?: return null
        val hit = raycast(rotation, player.blockInteractionRange())
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos.relative(hit.direction) != pos ||
            !level.getBlockState(pos).canBeReplaced()
        ) {
            return null
        }
        return Plan(pos, hit.blockPos, hit.direction, hit.location, rotation, slot)
    }

    /**
     * Whether [rotation] from [eye] hits first a face that places a block at [pos].
     *
     * @agent claude-opus-5-5
     */
    fun clicksFrom(eye: Vec3, rotation: ViewRotation, pos: BlockPos): Boolean {
        val hit = player.pick(rotation, player.blockInteractionRange(), eye)
        return hit.type == HitResult.Type.BLOCK && hit.blockPos.relative(hit.direction) == pos
    }

    /**
     * The hotbar slot to dig [state] with: the fastest tool made for it, efficiency counted; else one holding no
     * tool, nearest the selected one, as any other tool only wears out on it. Null when every slot holds a tool.
     */
    fun diggingSlot(state: BlockState): Int? {
        val hotbar = context.hotbar()
        var best: Int? = null
        var bestSpeed = 0f
        hotbar.forEachIndexed { slot, stack ->
            if (!isToolFor(stack, state)) return@forEachIndexed
            var speed = stack.getDestroySpeed(state)
            val level = stack.getEnchantmentLevel(Enchantments.EFFICIENCY)
            if (speed > 1f && level > 0) {
                speed += level * level + 1f
            }
            if (speed > bestSpeed) {
                best = slot
                bestSpeed = speed
            }
        }
        if (best != null) return best
        val selected = player.inventory.selectedSlot
        return hotbar.indices.filter { isWearFree(hotbar[it]) }.minByOrNull { abs(it - selected) }
    }

    /**
     * The offhand or the hotbar slot nearest the selected one with a block to place, favourable blocks first; -1 for
     * the offhand.
     */
    fun placeableSlot(): Int? {
        val blocks = context.options().placeableBlocks
        return closestSlot { blocks.isPlaceable(it) && !blocks.isUnfavourable(it) }
            ?: closestSlot(blocks::isPlaceable)
    }

    private fun closestSlot(accepts: (ItemStack) -> Boolean): Int? {
        val inventory = player.inventory
        if (accepts(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return -1
        }
        val selected = inventory.selectedSlot
        return context.hotbar().indices.filter { accepts(inventory.getItem(it)) }.minByOrNull { abs(it - selected) }
    }

    /**
     * The point on [face] of [against] closest to where the rotation sent last looks, kept off its edges.
     */
    private fun nearestOnFace(against: BlockPos, face: Direction, eye: Vec3): Vec3 {
        val normal = face.unitVec3
        val center = Vec3.atCenterOf(against).add(normal.scale(HALF))
        val look = context.body.sentRotation.viewVector
        val along = look.dot(normal)
        val onPlane = if (abs(along) < EPSILON) {
            center
        } else {
            eye.add(look.scale(center.subtract(eye).dot(normal) / along))
        }
        fun inside(axis: Direction.Axis, min: Int): Double = if (face.axis == axis) {
            center.get(axis)
        } else {
            onPlane.get(axis).coerceIn(min + EDGE, min + 1.0 - EDGE)
        }
        return Vec3(
            inside(Direction.Axis.X, against.x),
            inside(Direction.Axis.Y, against.y),
            inside(Direction.Axis.Z, against.z),
        )
    }

    private fun sees(eye: Vec3, point: Vec3, pos: BlockPos, face: Direction?): Boolean =
        level.isSeen(eye, point, pos, face, player)

    private fun turn(rotation: ViewRotation): Float {
        val sent = context.body.sentRotation
        return abs(Mth.wrapDegrees(rotation.yRot - sent.yRot)) + abs(rotation.xRot - sent.xRot)
    }

    private companion object {
        const val HALF = 0.5
        const val EDGE = 0.05
        const val EPSILON = 1.0E-3
    }
}
