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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.buildingBlocks
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.isBuildingBlock
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

private const val HOLD_STRETCH = 200
private const val STAND_OUT = 1.2
private const val WALL_HALF = 2
private const val WALL_DISTANCE = 3
private const val WALL_HEIGHT = 2
private const val WALL_BLOCKS = 24
private const val PLACE_REACH = 4.5
private const val BACK_WITHIN = 1.5
private const val STAND_WITHIN = 1.0

/**
 * On its island the agent holds where it stands, behind a wall towards the nearest enemy; elsewhere it heads back
 * first. With [counts], the ticks count against the hold budget and the plan is weighed again after a while.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.holdSpot(what: String, counts: Boolean): Any? {
    val since = ticks
    var wallBase: BlockPos? = null
    var wall: List<BlockPos> = emptyList()
    while (true) {
        if (counts) {
            heldTicks++
            if (ticks - since > HOLD_STRETCH) decideAt = minOf(decideAt, ticks)
        }
        val here = player.position()
        val nearIsland = here.horizontalDistanceTo(island) <= SkyWars.ISLAND_RADIUS
        val base = if (nearIsland) {
            island
        } else if (onIsland(here) || step(island, BACK_WITHIN, "island", "its island") != Step.MOVING) {
            here
        } else {
            goal = "Going back to its island"
            waitTicks(1)
            continue
        }
        val facing = enemies().minByOrNull { it.distanceToSqr(player) }?.position()
            ?: middle()
            ?: here.add(player.lookAngle)
        val (dx, dz) = direction(base, facing)
        val baseBlock = BlockPos.containing(base)
        if (baseBlock != wallBase) {
            wallBase = baseBlock
            wall = wallCells(base, dx, dz)
        }
        val stand = base.add(dx * STAND_OUT, 0.0, dz * STAND_OUT)
        if (here.horizontalDistanceTo(stand) > STAND_WITHIN) {
            agent.walkTo(stand, STAND_WITHIN)
            goal = what
            waitTicks(1)
            continue
        }
        val cell = wall.firstOrNull { canPlaceAt(it) }
        if (cell != null && player.inventory.buildingBlocks() >= WALL_BLOCKS) {
            goal = "Building a wall"
            place(cell.below())
        } else {
            goal = what
            val eye = player.eyePosition
            agent.body.look(ViewRotation(ViewRotation.fromVector(facing.subtract(eye)).yRot, 0f), LookPurpose.AIM)
            waitTicks(1)
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
private fun direction(base: Vec3, facing: Vec3): Pair<Double, Double> {
    val dx = facing.x - base.x
    val dz = facing.z - base.z
    val length = hypot(dx, dz)
    return if (length < 0.001) 1.0 to 0.0 else dx / length to dz / length
}

/**
 * A wall [WALL_HALF] to each side, [WALL_HEIGHT] high, [WALL_DISTANCE] ahead of [base] towards the facing.
 *
 * @agent claude-opus-5-5
 */
private fun Round.wallCells(base: Vec3, dx: Double, dz: Double): List<BlockPos> {
    val feetY = Mth.floor(player.y)
    return (0 until WALL_HEIGHT).flatMap { height ->
        (-WALL_HALF..WALL_HALF).map { side ->
            val x = base.x + dx * WALL_DISTANCE - dz * side
            val z = base.z + dz * WALL_DISTANCE + dx * side
            BlockPos(Mth.floor(x), feetY + height, Mth.floor(z))
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
private fun Round.canPlaceAt(cell: BlockPos): Boolean {
    val terrain = terrain
    return terrain.isPassable(cell.x, cell.y, cell.z) && !terrain.isPassable(cell.x, cell.y - 1, cell.z) &&
        player.eyePosition.distanceTo(Vec3.atCenterOf(cell)) <= PLACE_REACH
}

/**
 * Puts a building block onto the top of [below]: aims at it until the rotation the server has hits that face, at most
 * a second, then clicks.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal suspend fun Round.place(below: BlockPos) {
    val slot = player.inventory.slotOf(ItemMatch(::isBuildingBlock))
    if (slot < 0) return
    val body = agent.body
    val hit = Vec3(below.x + 0.5, below.y + 1.0, below.z + 0.5)
    val rotation = ViewRotation.fromVector(hit.subtract(player.eyePosition))
    agent.takeMovement()
    body.hold(slot)
    body.look(rotation, LookPurpose.AIM)
    var ticks = 0
    do {
        waitTicks(1)
        agent.takeMovement()
        body.look(rotation, LookPurpose.AIM)
    } while (!agent.navigator.canClick(below, Direction.UP) && ticks++ < 20)
    agent.navigator.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(hit, Direction.UP, below, false))
    body.releaseHotbarSlot()
    waitTicks(1)
}
