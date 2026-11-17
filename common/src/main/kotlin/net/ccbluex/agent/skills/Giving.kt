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

package net.ccbluex.agent.skills

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.Item
import kotlin.math.abs

/**
 * Walks up to [mate] and throws it [count] of [item], one at a time while looking at it, as a player hands something
 * over; how many it threw. Zero when [mate] is farther than 64 blocks, dead, or not reached.
 *
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.give(mate: Player, item: Item, count: Int): Int {
    val player = body.player
    if (!mate.isAlive || mate.distanceTo(player) > GIVE_SEARCH) return 0
    goTo(mate.position(), 2.5)
    if (mate.distanceTo(player) > GIVE_REACH) return 0
    fun aimed(): Boolean {
        val aim = ViewRotation.fromVector(mate.eyePosition.subtract(player.eyePosition))
        body.look(aim, LookPurpose.AIM)
        val sent = body.sentRotation
        return abs(Mth.wrapDegrees(sent.yRot - aim.yRot)) < AIM_DEGREES && abs(sent.xRot - aim.xRot) < AIM_DEGREES
    }
    tickConditional(20, ::aimed)
    var given = 0
    while (given < count) {
        val slot = (0 until Inventory.INVENTORY_SIZE).firstOrNull { player.inventory.getItem(it).`is`(item) } ?: break
        aimed()
        body.drop(slot, wholeStack = false)
        given++
        waitTicks(2)
    }
    return given
}

private const val GIVE_SEARCH = 64f
private const val GIVE_REACH = 4f
private const val AIM_DEGREES = 10f
