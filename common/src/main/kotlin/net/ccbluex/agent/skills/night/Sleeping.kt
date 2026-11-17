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

import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.gather.mine
import net.ccbluex.agent.skills.placeBlock
import net.ccbluex.agent.skills.useBlock
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.item.ItemTags

/**
 * Sleeps through the night in [bed] when that is a bed within 64 blocks, else in a carried one it places where it
 * stands; with [takeBack] it takes the bed along again in the morning. A bed it placed but could not lie down in goes
 * back into the inventory. The bed it slept in, or null when it had none or the game did not let it lie down, as with
 * monsters near.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.sleepThroughNight(bed: BlockPos?, takeBack: Boolean): BlockPos? {
    val player = body.player
    val near = bed?.takeIf { body.level.getBlockState(it).`is`(BlockKind.BED) && it.closerToCenterThan(player.position(), 64.0) }
    val placed = if (near == null) placeBed() ?: return null else null
    placed?.let { listener.step("placed bed at ${it.toShortString()}") }
    val slept = near ?: placed ?: return null
    if (!sleepIn(slept)) {
        listener.step("could not sleep at ${slept.toShortString()}")
        placed?.let { mine(it, forDrops = false) }
        return null
    }
    listener.step("slept at ${slept.toShortString()}")
    if (takeBack) {
        // The server stands the player up a few ticks after it wakes; a dig before that is lost
        waitTicks(10)
        if (!mine(slept, forDrops = false)) listener.step("could not take the bed at ${slept.toShortString()} back")
    }
    return slept
}

/**
 * Lies down in [bed] and sleeps until the player wakes, at most [limitTicks]; false when the game did not let it lie
 * down, as by day or with monsters near. Using a bed also sets the respawn point there.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.sleepIn(bed: BlockPos, limitTicks: Int = 12000): Boolean {
    if (!body.level.getBlockState(bed).`is`(BlockKind.BED)) return false
    if (!useBlock(bed) { body.player.isSleeping }) return false
    tickUntil { ticks ->
        takeMovement()
        !body.player.isSleeping || ticks >= limitTicks
    }
    return true
}

/**
 * Places a carried bed on the ground next to the player, its head away from the player; where its foot is, or null
 * when it carries none or there is no room for both halves.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.placeBed(): BlockPos? {
    val inventory = body.player.inventory
    val slot = (0 until 36).firstOrNull { inventory.getItem(it).`is`(ItemTags.BEDS) } ?: return null
    val level = body.level
    val feet = body.player.blockPosition()
    fun isFree(pos: BlockPos) = level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty &&
        level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
    for (direction in Direction.Plane.HORIZONTAL) {
        for (distance in 1..2) {
            val foot = feet.relative(direction, distance)
            if (!isFree(foot) || !isFree(foot.relative(direction))) continue
            if (placeBlock(slot, foot)) return foot
        }
    }
    return null
}
