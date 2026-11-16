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

package net.ccbluex.agent.skills.fight

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Block queries and placing for the skills that build: walls, pillars, webs, crystals and anchors.
 *
 * @agent claude-opus-5-5
 */
internal object Building {

    fun solid(fight: BuiltInFight, pos: BlockPos): Boolean =
        !fight.body.level.getBlockState(pos).getCollisionShape(fight.body.level, pos).isEmpty

    fun air(fight: BuiltInFight, pos: BlockPos): Boolean = fight.body.level.getBlockState(pos).isAir

    fun waterSource(fight: BuiltInFight, pos: BlockPos): Boolean =
        fight.body.level.getFluidState(pos).let { it.isSource && it.type.isSame(Fluids.WATER) }

    fun lavaSource(fight: BuiltInFight, pos: BlockPos): Boolean =
        fight.body.level.getFluidState(pos).let { it.isSource && it.type.isSame(Fluids.LAVA) }

    /**
     * The middle of the block's top face.
     */
    fun top(pos: BlockPos): Vec3 = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

    fun centre(pos: BlockPos, y: Double): Vec3 = Vec3(pos.x + 0.5, pos.y + y, pos.z + 0.5)

    /**
     * Where an entity in the air comes down: the free cell over the ground below it, a few blocks down at most.
     */
    fun landing(fight: BuiltInFight, entity: Entity): BlockPos {
        var cell = entity.blockPosition()
        repeat(LANDING_DEPTH) { if (!solid(fight, cell.below())) cell = cell.below() }
        return cell
    }

    /**
     * The horizontal face from [from] that points most towards [to].
     */
    fun towards(from: Vec3, to: Vec3): Direction {
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (abs(dx) > abs(dz)) return if (dx > 0) Direction.EAST else Direction.WEST
        return if (dz > 0) Direction.SOUTH else Direction.NORTH
    }

    /**
     * Whether the fighter, its target or an entity near the target stands in the [height] cells from [cell] up.
     */
    fun occupied(fight: BuiltInFight, cell: BlockPos, height: Int): Boolean {
        val box = AABB(
            cell.x.toDouble(), cell.y.toDouble(), cell.z.toDouble(),
            cell.x + 1.0, cell.y + height.toDouble(), cell.z + 1.0,
        )
        return fight.body.level
            .getEntities(null as Entity?, box) { !it.isSpectator && it.isPickable || it === fight.player }
            .isNotEmpty()
    }

    /**
     * Right-clicks [face] of [against] with the main hand, as placing a block or using an item on a block does; false
     * when the rotation the server has does not hit that face first.
     */
    fun placeOn(fight: BuiltInFight, against: BlockPos, face: Direction): Boolean {
        val point = Vec3.atCenterOf(against).add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)
        return fight.navigator.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(point, face, against, false))
    }

    /**
     * Builds the cell beside the fighter towards [point], at its feet ([level] 0) or eyes (1): true once the cell is
     * filled, false when it cannot be, null while the rotation the server has does not hit the face it goes against.
     */
    fun wall(fight: BuiltInFight, point: Vec3, level: Int): Boolean? {
        val here = fight.player.blockPosition()
        val cell = here.relative(towards(fight.player.position(), point)).above(level)
        if (solid(fight, cell)) return true
        val below = cell.below()
        if (!solid(fight, below) || !fight.hands.hold { it.isBuildingBlock }) return false
        fight.lookAt(top(below), 3.0)
        if (!fight.navigator.canClick(below, Direction.UP)) return null
        return placeOn(fight, below, Direction.UP)
    }

    private const val LANDING_DEPTH = 5
}
