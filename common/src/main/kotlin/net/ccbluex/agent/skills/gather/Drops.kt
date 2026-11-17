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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.ItemStack

/**
 * Walks over the items lying within [radius] of [around], nearest first, until none is left that fits into the
 * inventory or [limitTicks] passed; how many stacks it picked up.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.pickUpItems(around: Vec3, radius: Double = 8.0, limitTicks: Int = 300): Int {
    val tried = HashSet<Int>()
    var picked = 0
    val start = ticks
    while (ticks - start < limitTicks) {
        val player = body.player
        val item = body.level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(around, radius * 2, 8.0, radius * 2))
            .filter { it.isAlive && it.id !in tried && fits(player, it.item) }
            .minByOrNull { it.distanceToSqr(player) } ?: break
        tried += item.id
        var ending: Ending? = null
        navigator.moveTo(item.position(), 0.5) { ending = it }
        try {
            tickUntil { t -> ending != null || !item.isAlive || t >= 100 || ticks - start >= limitTicks }
        } finally {
            if (ending == null) navigator.stop()
        }
        if (!item.isAlive) picked++
    }
    return picked
}

/**
 * Whether some of [stack] goes into the player's main inventory.
 *
 * @agent claude-opus-5-5
 */
internal fun fits(player: Player, stack: ItemStack): Boolean {
    val inventory = player.inventory
    return (0 until 36).any {
        val held = inventory.getItem(it)
        held.isEmpty || ItemStack.isSameItemSameComponents(held, stack) && held.count < held.maxStackSize
    }
}
