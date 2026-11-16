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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.hotbar
import net.ccbluex.agent.navigation.pick
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
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
     * A rotation that sees a point of [state] at [pos] within reach, turning least from the rotation sent last.
     */
    fun aimAt(pos: BlockPos, state: BlockState): ViewRotation? {
        val eye = player.eyePosition
        val reach = player.blockInteractionRange()
        val shape = state.getShape(level, pos)
        val boxes = if (shape.isEmpty) listOf(FULL_BLOCK) else shape.toAabbs()
        var best: ViewRotation? = null
        var bestTurn = Float.MAX_VALUE
        for (box in boxes) {
            for (i in 0 until GRID) for (j in 0 until GRID) for (k in 0 until GRID) {
                val point = Vec3(
                    pos.x + Mth.lerp(fraction(i), box.minX, box.maxX),
                    pos.y + Mth.lerp(fraction(j), box.minY, box.maxY),
                    pos.z + Mth.lerp(fraction(k), box.minZ, box.maxZ),
                )
                if (eye.distanceToSqr(point) > reach * reach || !sees(eye, point, pos, null)) {
                    continue
                }
                val rotation = ViewRotation.fromVector(point.subtract(eye))
                val turn = turn(rotation)
                if (turn < bestTurn) {
                    best = rotation
                    bestTurn = turn
                }
            }
        }
        return best
    }

    /**
     * What the player hits looking with [rotation], up to [reach] blocks away.
     */
    fun raycast(rotation: ViewRotation, reach: Double): BlockHitResult = player.pick(rotation, reach)

    /**
     * The click that places a block at [pos] against a neighbour, turning least from the rotation sent last. Below
     * the feet, where no way is better than another, the point on the face nearest to the current look; else the
     * middle of the face.
     */
    fun planPlacement(pos: BlockPos, slot: Int): Plan? {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return null
        }
        val eye = player.eyePosition
        val reach = player.blockInteractionRange()
        val feet = player.blockPosition()
        val underFeet = pos.x == feet.x && pos.z == feet.z && pos.y <= feet.y

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
            val point = if (underFeet) nearestOnFace(against, face, eye) else center
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
     * The hit [plan] clicks with [rotation], null when that rotation does not click its face.
     */
    fun verifyClick(plan: Plan, rotation: ViewRotation): BlockHitResult? {
        val hit = raycast(rotation, player.blockInteractionRange())
        val clicks = hit.type == HitResult.Type.BLOCK && hit.blockPos == plan.against && hit.direction == plan.face
        return hit.takeIf { clicks }
    }

    /**
     * The hotbar slot with the tool that mines [state] fastest, if it is faster than a hand.
     */
    fun bestTool(state: BlockState): Int? {
        val efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.EFFICIENCY)
        var best: Int? = null
        var bestSpeed = 1f
        context.hotbar().forEachIndexed { slot, stack ->
            var speed = stack.getDestroySpeed(state)
            val level = EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack)
            if (speed > 1f && level > 0) {
                speed += level * level + 1f
            }
            if (speed > bestSpeed) {
                best = slot
                bestSpeed = speed
            }
        }
        return best
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

    /**
     * Whether a look from [eye] at [point] hits [pos] first, on [face] when given.
     */
    private fun sees(eye: Vec3, point: Vec3, pos: BlockPos, face: Direction?): Boolean {
        val past = eye.add(point.subtract(eye).scale(1.0 + EPSILON))
        val hit = level.clip(ClipContext(eye, past, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.BLOCK && hit.blockPos == pos && (face == null || hit.direction == face)
    }

    private fun turn(rotation: ViewRotation): Float {
        val sent = context.body.sentRotation
        return abs(Mth.wrapDegrees(rotation.yRot - sent.yRot)) + abs(rotation.xRot - sent.xRot)
    }

    private fun fraction(index: Int) = (index + 0.5) / GRID

    private companion object {
        const val GRID = 5
        const val HALF = 0.5
        const val EDGE = 0.05
        const val EPSILON = 1.0E-3
        val FULL_BLOCK = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    }
}
