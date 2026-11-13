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

package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.isBuildingBlock
import net.ccbluex.agent.skills.slotOf
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Blocks put under a fall into the void, one level below the feet: into the cells the body comes down on, clicked
 * onto a block beside or under them, else into a cell next to those that a block can be clicked onto, which the next
 * one then leans on. The look leads the click by a tick, so it is aimed from where the eyes will be.
 *
 * @agent claude-opus-5-5
 */
internal object Clutch {

    private val SUPPORTS = Direction.Plane.HORIZONTAL.toList() + Direction.DOWN
    private const val PLACE_REACH = 4.5
    private const val HALF = 0.5

    /**
     * Air movement of a player without keys, per tick.
     */
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98
    private const val AIR_DRAG = 0.91
    private const val FALL_TICKS = 20

    /**
     * One tick of placing; false when nothing in reach can catch the fall.
     */
    fun tick(agent: Agent): Boolean {
        val body = agent.body
        val player = body.player
        val level = body.level
        val landing = landingCells(player)
        // Already placed, or about to land on something
        if (landing.any { !level.getBlockState(it).canBeReplaced() }) return true
        val slot = player.inventory.slotOf(::isBuildingBlock)
        if (slot < 0) return false

        val cells = landing + landing.flatMap { cell -> Direction.Plane.HORIZONTAL.map(cell::relative) }.distinct()
        val eyes = player.eyePosition.add(player.deltaMovement)
        val aim = cells.firstNotNullOfOrNull { supportFace(level, it, eyes) } ?: return false

        body.hold(slot)
        aimedFace(body, cells)?.let { hit ->
            if (body.useItemOn(InteractionHand.MAIN_HAND, hit)) {
                agent.decide("Clutch block", 1.0, "falling at ${hit.blockPos.relative(hit.direction).y}")
            }
        }
        body.look(ViewRotation.fromVector(aim.subtract(eyes)), LookPurpose.AIM)
        return true
    }

    /**
     * The cells one level below the feet that the body's box covers once its fall without keys gets down to their
     * top, nearest first.
     */
    private fun landingCells(player: Player): List<BlockPos> {
        val top = Mth.floor(player.y)
        var position = player.position()
        var motion = player.deltaMovement
        for (tick in 0 until FALL_TICKS) {
            if (position.y + motion.y <= top) {
                position = position.add(motion.x, 0.0, motion.z)
                break
            }
            position = position.add(motion)
            motion = Vec3(motion.x * AIR_DRAG, (motion.y - GRAVITY) * VERTICAL_DRAG, motion.z * AIR_DRAG)
        }
        val half = player.bbWidth / 2.0
        val cells = ArrayList<BlockPos>(4)
        for (x in Mth.floor(position.x - half)..Mth.floor(position.x + half - 1.0E-7)) {
            for (z in Mth.floor(position.z - half)..Mth.floor(position.z + half - 1.0E-7)) {
                cells += BlockPos(x, top - 1, z)
            }
        }
        val centre = position
        return cells.sortedBy { Vec3.atCenterOf(it).distanceToSqr(centre.x, it.y + HALF, centre.z) }
    }

    /**
     * The middle of the nearest face within reach of [eyes] that a block for [cell] can be clicked onto.
     */
    private fun supportFace(level: Level, cell: BlockPos, eyes: Vec3): Vec3? {
        if (!level.getBlockState(cell).canBeReplaced()) return null
        var aim: Vec3? = null
        for (face in SUPPORTS) {
            val support = cell.relative(face)
            if (level.getBlockState(support).getCollisionShape(level, support).isEmpty) continue
            val point = Vec3.atCenterOf(support).add(-face.stepX * HALF, -face.stepY * HALF, -face.stepZ * HALF)
            val distance = eyes.distanceToSqr(point)
            if (distance > PLACE_REACH * PLACE_REACH) continue
            if (aim == null || distance < eyes.distanceToSqr(aim)) aim = point
        }
        return aim
    }

    /**
     * The face the sent rotation points at, when a block clicked onto it goes into one of [cells].
     */
    private fun aimedFace(body: AgentBody, cells: List<BlockPos>): BlockHitResult? {
        val eyes = body.player.eyePosition
        val end = eyes.add(body.sentRotation.viewVector.scale(PLACE_REACH))
        val context = ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body.player)
        val hit = body.level.clip(context)
        return hit.takeIf { it.type == HitResult.Type.BLOCK && it.blockPos.relative(it.direction) in cells }
    }
}
