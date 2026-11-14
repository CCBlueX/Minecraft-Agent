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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.Vec3

internal const val OWN_CHEST_RANGE = 7.0
private const val REAL_PLAYER_NEAR_CHEST = 12.0
private const val FIRST_PICK_MIN = 60
private const val FIRST_PICK_MAX = 100

/**
 * Chests a bridge or flight leads to first, before the loot ability walks up to them.
 */
private const val CHEST_TRAVEL = 8.0

/**
 * @agent claude-opus-5-5
 */
internal fun Round.chestsNear(at: Vec3, range: Double): List<BlockPos> =
    senses.chests.filter { Vec3.atBottomCenterOf(it).horizontalDistanceTo(at) <= range }

/**
 * @agent claude-opus-5-5
 */
internal fun Round.middleChests(): List<BlockPos> {
    val middle = middle()
    return senses.chests.filter {
        senses.isMiddleChest(it) ||
            middle != null && Vec3.atBottomCenterOf(it).horizontalDistanceTo(middle) <= SkyWars.ISLAND_RADIUS + 2
    }
}

/**
 * The chests the agent may loot now, which it did not give up on or open since the refill. Real players get first
 * pick: with [leftovers] the agent takes what they left once it is its turn, without them it only goes for chests
 * no real player opened.
 *
 * @agent claude-opus-5-5
 */
internal fun Round.lootable(chests: List<BlockPos>, leftovers: Boolean): List<BlockPos> {
    val loot = agent.skills.loot ?: return emptyList()
    val level = agent.body.level
    return chests.filter { pos ->
        if (pos in unreachable || loot.chests.isOpened(pos) || isAvoided(pos)) return@filter false
        if (level.isLoaded(pos) && level.getBlockEntity(pos) !is ChestBlockEntity) return@filter false
        if (!leftovers && (senses.isChestInUse(pos) || senses.ticksSinceChestClosed(pos) != null)) return@filter false
        val freeAt = freeAt(pos)
        if (freeAt > ticks) {
            chestFreeAt = minOf(chestFreeAt, freeAt)
            false
        } else {
            true
        }
    }
}

/**
 * When it is the agent's turn at [pos]. A real player who opened it has it while they look into it and for a moment
 * after they closed it; one standing close by who did not open it has it for a few seconds.
 *
 * @agent claude-opus-5-5
 */
private fun Round.freeAt(pos: BlockPos): Long {
    if (senses.isChestInUse(pos)) {
        firstPick.remove(pos)
        return Long.MAX_VALUE
    }
    senses.ticksSinceChestClosed(pos)?.let {
        firstPick.remove(pos)
        return ticks + leftoverDelay - it
    }
    val center = Vec3.atCenterOf(pos)
    val realPlayerNear = agent.body.players().any {
        it !== player && it.isAlive && !it.isSpectator && !senses.isBot(it) &&
            it.position().closerThan(center, REAL_PLAYER_NEAR_CHEST)
    }
    if (!realPlayerNear) {
        firstPick.remove(pos)
        return ticks
    }
    return firstPick.getOrPut(pos) { ticks + random.nextInt(FIRST_PICK_MIN, FIRST_PICK_MAX + 1) }
}

/**
 * Loots the nearest of [chests] one after the other while any is the agent's to loot: a far chest is travelled to
 * first, a close one left to the loot ability, which walks up, opens it and takes what is wanted. Returns once none
 * is left.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.loot(chests: () -> List<BlockPos>, where: String, leftovers: Boolean) {
    val loot = agent.skills.loot ?: return
    while (true) {
        val current = chest
        if (current != null && loot.isLooting) {
            loot.open(current)
            goal = "Looting"
            waitTicks(1)
            continue
        }
        val next = lootable(chests(), leftovers).minByOrNull { it.distToCenterSqr(player.position()) } ?: return
        chest = next
        val center = Vec3.atCenterOf(next)
        if (player.position().distanceTo(center) > CHEST_TRAVEL) {
            if (step(center, CHEST_TRAVEL / 2, next, where) == Step.BLOCKED) unreachable += next
        } else if (loot.open(next)) {
            openedAny = true
            goal = if (loot.isLooting) "Looting" else "Going to $where"
        } else {
            unreachable += next
            chest = null
        }
        waitTicks(1)
    }
}
