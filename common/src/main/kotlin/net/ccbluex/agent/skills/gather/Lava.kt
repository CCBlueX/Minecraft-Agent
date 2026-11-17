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
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.goTo
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.math.Vec3
import java.util.function.Predicate

/**
 * Fills a carried empty bucket from the nearest lava source in sight within 24 blocks, clicking only while the look
 * meets that lava first; with none in sight it mines down and tunnels as for iron, where lava shows in caves. Whether
 * it carries lava now.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.fillLava(): Boolean {
    val inventory = body.player.inventory
    if (inventory.countItem(Items.LAVA_BUCKET) > 0) return true
    val bucket = (0 until 36).firstOrNull { inventory.getItem(it).`is`(Items.BUCKET) } ?: return false
    val level = body.level
    val lava = level.nearestBlocks(body.player.blockPosition(), 24, 12, 1) { pos, state ->
        state.`is`(Blocks.LAVA) && level.getFluidState(pos).isSource && level.isExposed(pos)
    }.firstOrNull() ?: return search(Resource.IRON, Predicate { true })
    if (goTo(Vec3.atCenterOf(lava), 3.5) == null) return false
    // The lava's surface, which the bucket scoops from
    val point = Vec3(lava.x + 0.5, lava.y + 0.9, lava.z + 0.5)
    try {
        val hotbar = body.hold(bucket)
        tickUntil { ticks ->
            takeMovement()
            body.selectHotbarSlot(hotbar)
            body.look(ViewRotation.fromVector(point.subtract(body.player.eyePosition)), LookPurpose.AIM)
            if (ticks % 5 == 0 && meetsLava(lava)) body.useItem(InteractionHand.MAIN_HAND)
            inventory.countItem(Items.LAVA_BUCKET) > 0 || ticks >= 60
        }
    } finally {
        body.releaseHotbarSlot()
    }
    return inventory.countItem(Items.LAVA_BUCKET) > 0
}

/**
 * Whether a look with the rotation the server has meets the lava source at [pos] before any block.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.meetsLava(pos: BlockPos): Boolean {
    val player = body.player
    val eye = player.eyePosition
    val end = eye.add(body.sentRotation.viewVector.scale(player.blockInteractionRange()))
    val hit = body.level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, player, ClipContext.Fluid.SOURCE_ONLY))
    return hit.type == HitResult.Type.BLOCK && hit.blockPos == pos
}
