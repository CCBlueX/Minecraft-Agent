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
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.HOTBAR_SLOTS
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.isBuildingBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

private const val DUG_MAX = 40
private const val DIG_REACH = 4.0
private const val DIG_WITHIN = 2.5
private const val DIG_TICKS = 200
private const val INNER_RING = 2.5
private const val DIG_DEPTH = 4

/**
 * Whether more blocks may be dug this round.
 *
 * @agent claude-opus-5-5
 */
internal val Round.canDig: Boolean
    get() = dugCount < DUG_MAX

/**
 * A block on top of the own island that gives something to build with, away from where the agent stands and from
 * the chests: in a ring around the island, the first block of each column within [DIG_DEPTH] below it.
 *
 * @agent claude-opus-5-5
 */
internal fun Round.digCandidate(): BlockPos? {
    if (!canDig) return null
    val level = agent.body.level
    val feet = player.blockPosition().below()
    val eye = player.eyePosition
    val top = Mth.floor(island.y) - 1
    val radius = SkyWars.ISLAND_RADIUS.toInt()
    var best: BlockPos? = null
    var bestDistance = Double.MAX_VALUE
    for (dx in -radius..radius) {
        for (dz in -radius..radius) {
            val across = hypot(dx.toDouble(), dz.toDouble())
            if (across < INNER_RING || across > SkyWars.ISLAND_RADIUS) continue
            for (y in top downTo top - DIG_DEPTH + 1) {
                val pos = BlockPos(Mth.floor(island.x) + dx, y, Mth.floor(island.z) + dz)
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                if (!terrain.isPassable(pos.x, pos.y + 1, pos.z) || pos == feet || nearChest(pos)) break
                if (isAvoided(pos) || !givesBlock(state)) break
                val distance = Vec3.atCenterOf(pos).distanceToSqr(eye)
                if (distance < bestDistance) {
                    best = pos
                    bestDistance = distance
                }
                break
            }
        }
    }
    return best
}

/**
 * @agent claude-opus-5-5
 */
private fun Round.nearChest(pos: BlockPos): Boolean =
    BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))
        .any { agent.body.level.getBlockState(it).hasBlockEntity() }

/**
 * Whether digging the block drops something to build with, with a tool the agent carries if it needs one.
 *
 * @agent claude-opus-5-5
 */
private fun Round.givesBlock(state: BlockState): Boolean {
    if (state.hasBlockEntity() || state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.IMPERMEABLE)) return false
    if (!isBuildingBlock(ItemStack(state.block.asItem()))) return false
    return !state.requiresCorrectToolForDrops() || toolSlot(state)?.let {
        player.inventory.getItem(it).isCorrectToolForDrops(state)
    } == true
}

/**
 * The hotbar slot whose item digs the block fastest; null when the hand is as good.
 *
 * @agent claude-opus-5-5
 */
private fun Round.toolSlot(state: BlockState): Int? {
    val inventory = player.inventory
    return (0 until HOTBAR_SLOTS)
        .filter { inventory.getItem(it).getDestroySpeed(state) > 1f }
        .maxByOrNull { inventory.getItem(it).getDestroySpeed(state) }
}

/**
 * Walks up to the block at [point] and digs it; what to leave alone when it could not.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.gather(point: Vec3): Any? {
    val pos = BlockPos.containing(point)
    val body = agent.body
    val level = body.level
    while (player.eyePosition.distanceTo(point) > DIG_REACH) {
        if (level.getBlockState(pos).isAir) return null
        if (step(point.add(0.0, 0.5, 0.0), DIG_WITHIN, pos, "dig") == Step.BLOCKED) return pos
        goal = "Going to dig"
        scope.nextTick()
    }
    val state = level.getBlockState(pos)
    if (state.isAir || !givesBlock(state)) return null
    toolSlot(state)?.let { body.hold(it) }
    goal = "Gathering blocks"
    try {
        var started = false
        repeat(DIG_TICKS) {
            if (level.getBlockState(pos).isAir) {
                dugCount++
                return null
            }
            agent.takeMovement()
            body.look(ViewRotation.fromVector(point.subtract(player.eyePosition)), LookPurpose.AIM)
            if (started) {
                body.continueDestroy(pos, Direction.UP)
            } else {
                started = body.startDestroy(pos, Direction.UP)
            }
            scope.nextTick()
        }
        return pos
    } finally {
        body.stopDestroy()
        body.releaseHotbarSlot()
    }
}
