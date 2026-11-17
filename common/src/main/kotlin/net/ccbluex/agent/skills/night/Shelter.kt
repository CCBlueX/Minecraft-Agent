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

package net.ccbluex.agent.skills.night

import net.ccbluex.agent.world.Heightmap
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.entity.Zombie
import net.ccbluex.agent.world.entity.AbstractSkeleton
import net.ccbluex.agent.world.entity.Monster
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.walkTo
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.dig
import net.ccbluex.agent.skills.goTo
import net.ccbluex.agent.skills.isBuildingBlock
import net.ccbluex.agent.skills.placeBlock
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.math.Vec3

/**
 * Gets into shelter for the night: under a roof already, or dug in where it stands or at the nearest spot within 12
 * blocks whose ground allows it; false when nowhere near does.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.shelter(): Boolean {
    val player = body.player
    val level = body.level
    val head = player.blockPosition().above(2)
    if (level.getBlockState(head).isFaceSturdy(level, head, Direction.DOWN)) return true
    val feet = player.blockPosition()
    if (!mayDigIn(feet)) {
        val spot = (-12..12).flatMap { dx -> (-12..12).map { dz -> feet.offset(dx, 0, dz) } }
            .map { BlockPos(it.x, level.getHeight(Heightmap.MOTION_BLOCKING_NO_LEAVES, it.x, it.z), it.z) }
            .filter(::mayDigIn)
            .minByOrNull { it.distSqr(feet) }
        if (spot == null || goTo(Vec3.atBottomCenterOf(spot), 0.5) != Ending.ARRIVED) {
            listener.step("found nowhere to dig in near ${feet.toShortString()}")
            return false
        }
    }
    if (!digIn()) {
        listener.step("could not dig in at ${player.blockPosition().toShortString()}")
        return false
    }
    listener.step("dug in at ${player.blockPosition().toShortString()}")
    return true
}

/**
 * Waits where it is for the morning, and then until the zombies and skeletons within 24 blocks have burnt, for at
 * most two minutes, as they burn longer in the shade.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.waitForMorning() {
    val player = body.player
    val level = body.level
    tickUntil { ticks -> takeMovement(); !level.isDarkOutside || !player.isAlive || ticks >= 12000 }
    tickUntil { ticks ->
        takeMovement()
        !player.isAlive || ticks >= 2400 || level.getEntitiesOfClass(Monster::class.java, player.boundingBox.inflate(24.0)) {
            it.isAlive && (it is Zombie || it is AbstractSkeleton)
        }.isEmpty()
    }
}

/**
 * Digs three blocks straight down where it stands and closes the hole over its head with a block, one it carried or
 * dug out, so no monster gets at it; false when the ground there is not safe to dig into (fluids, a cave below,
 * blocks that fall), the navigator's options keep it from breaking them, or it has no block left. A walk out later
 * digs or builds its way up.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.digIn(): Boolean {
    val player = body.player
    if (!player.onGround()) return false
    val inventory = player.inventory
    val top = player.blockPosition()
    val hole = (1..3).map { top.below(it) }
    if (!mayDigIn(top)) return false
    fun isIn(cell: BlockPos) = player.onGround() && player.blockPosition().y <= cell.y
    // Standing partly over a neighbour, the player would stay up on it once the hole opens; from the middle it drops
    navigator.walkTo(Vec3.atBottomCenterOf(top), 0.2)
    for (cell in hole) {
        if (!dig(cell, forDrops = false)) {
            listener.step("could not dig ${cell.toShortString()} to dig in")
            return false
        }
        if (tickConditional(20) { takeMovement(); isIn(cell) }) {
            listener.step("did not drop into ${cell.toShortString()}, standing at ${player.position()}")
            return false
        }
    }
    // What the hole gave is picked up on the way down
    tickConditional(10) { takeMovement(); (0 until 36).any { isBuildingBlock(inventory.getItem(it)) } }
    val block = (0 until 36).firstOrNull { isBuildingBlock(inventory.getItem(it)) }
    if (block == null) {
        listener.step("has no block to close the hole over its head")
        return false
    }
    if (!placeBlock(block, hole.first())) {
        listener.step("could not close the hole at ${hole.first().toShortString()}")
        return false
    }
    return true
}

/**
 * Whether the agent may dig in at [top]: [canDigIn], and the navigator's options let it break the hole's blocks.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.mayDigIn(top: BlockPos): Boolean {
    val level = body.level
    val breakable = navigator.options.breakableBlocks
    return canDigIn(level, top) && (1..3).all { breakable.isBreakable(level.getBlockState(top.below(it))) }
}

/**
 * Whether the three cells under [top] can be dug as a shelter: solid, no fluid next to them, nothing that falls, and
 * firm ground below.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun canDigIn(level: BlockGetter, top: BlockPos): Boolean {
    val hole = (1..3).map { top.below(it) }
    val floor = top.below(4)
    return hole.all { cell ->
        val state = level.getBlockState(cell)
        !state.isAir && state.fluidState.isEmpty && state.getDestroySpeed(level, cell) in 0f..2f &&
            !state.`is`(BlockKind.FALLING) && Direction.Plane.HORIZONTAL.all { level.getFluidState(cell.relative(it)).isEmpty }
    } && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.getFluidState(floor).isEmpty
}
