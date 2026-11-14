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
import net.ccbluex.agent.math.clickPoint
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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Blocks put under a fall into the void, one level below the feet: into the cells the body comes down on, clicked
 * onto a block beside or under them, else into the nearest cell around them that one can be clicked onto, which the
 * next one then leans on, up to [CHAIN] cells out. The look leads the click by a tick, so it is aimed from where the eyes will be.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal object Clutch {

    private val SUPPORTS = Direction.Plane.HORIZONTAL.toList() + Direction.DOWN

    /**
     * A fall gives a few ticks, so the look is a flick a body paces at flick speed, not the aim's: at the aim's pace
     * of a normal bot (15 degrees of yaw, 10.5 of pitch a tick) it takes seven ticks to look down from level.
     */
    val LOOK = LookPurpose.STEER
    private const val PLACE_REACH = 4.5
    private const val HALF = 0.5

    /**
     * How many cells out from where the body comes down the chain of blocks may start.
     */
    private const val CHAIN = 4

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
        val landing = landingCells(player.position(), player.deltaMovement, player.bbWidth.toDouble())
        // Already placed, or about to land on something
        if (landing.any { !level.getBlockState(it).canBeReplaced() }) return true
        val slot = player.inventory.slotOf(::isBuildingBlock)
        if (slot < 0) return false

        val eyes = player.eyePosition.add(player.deltaMovement)
        val placement = nextPlacement(
            landing, eyes, body.sentRotation.viewVector, player.uuid.mostSignificantBits,
            isOpen = { level.getBlockState(it).canBeReplaced() },
            isSolid = { pos -> level.getBlockState(pos).let { !it.getCollisionShape(level, pos).isEmpty } },
        ) ?: return false
        val cells = placement.cells
        val aim = placement.aim

        // Nobody places while eating: a use still held goes first
        if (body.isUsingItem) body.releaseUsingItem()
        body.hold(slot)
        aimedFace(body, cells)?.let { hit ->
            if (agent.navigator.useItemOn(InteractionHand.MAIN_HAND, hit)) {
                agent.decide("Clutch block", 1.0, "falling at ${hit.blockPos.relative(hit.direction).y}")
            }
        }
        body.look(ViewRotation.fromVector(aim.subtract(eyes)), LOOK)
        return true
    }

    /**
     * The cells one level below the feet that a box [width] wide at [start] covers once its fall without keys from
     * [startMotion] gets down to their top, nearest first.
     */
    fun landingCells(start: Vec3, startMotion: Vec3, width: Double): List<BlockPos> {
        val top = Mth.floor(start.y)
        var position = start
        var motion = startMotion
        for (tick in 0 until FALL_TICKS) {
            if (position.y + motion.y <= top) {
                position = position.add(motion.x, 0.0, motion.z)
                break
            }
            position = position.add(motion)
            motion = Vec3(motion.x * AIR_DRAG, (motion.y - GRAVITY) * VERTICAL_DRAG, motion.z * AIR_DRAG)
        }
        val half = width / 2.0
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
     * The next block to put in: the nearest cell to [landing] that a block can be clicked onto, looking at most
     * [CHAIN] cells away from it over open ones, so a player knocked a few blocks off a bridge or an island still
     * gets a chain of blocks that leads out to where it falls. [Placement.cells] are the cells a click may fill.
     */
    fun nextPlacement(
        landing: List<BlockPos>,
        eyes: Vec3,
        looking: Vec3,
        seed: Long,
        isOpen: (BlockPos) -> Boolean,
        isSolid: (BlockPos) -> Boolean,
    ): Placement? {
        val seen = LinkedHashSet<BlockPos>()
        var ring = landing.filter(isOpen)
        seen += ring
        repeat(CHAIN + 1) {
            val aim = ring.mapNotNull { supportFace(it, eyes, looking, seed, isSolid) }.maxByOrNull { it.second }?.first
            if (aim != null) return Placement(seen.toList(), aim)
            ring = ring.flatMap { cell -> Direction.Plane.HORIZONTAL.map(cell::relative) }
                .filter { it !in seen && isOpen(it) }.distinct()
            seen += ring
        }
        return null
    }

    /**
     * What to place next: [aim] is the point to look at, on the block a click fills one of [cells] from.
     */
    class Placement(val cells: List<BlockPos>, val aim: Vec3)

    /**
     * The [clickPoint] of the face within reach of [eyes] that a block for [cell] can be clicked onto, the one the
     * body already [looking] towards, with how well it lines up: two faces equally near, such as a corner's two walls,
     * are told apart by where the look goes, not by the level the body falls past, which would turn it back and forth.
     */
    private fun supportFace(
        cell: BlockPos,
        eyes: Vec3,
        looking: Vec3,
        seed: Long,
        isSolid: (BlockPos) -> Boolean,
    ): Pair<Vec3, Double>? {
        var best: Pair<Vec3, Double>? = null
        for (face in SUPPORTS) {
            val support = cell.relative(face)
            if (!isSolid(support)) continue
            val point = clickPoint(support, face.opposite, seed xor support.asLong())
            val way = point.subtract(eyes)
            if (way.lengthSqr() > PLACE_REACH * PLACE_REACH) continue
            val alignment = way.normalize().dot(looking)
            if (best == null || alignment > best.second) best = point to alignment
        }
        return best
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
