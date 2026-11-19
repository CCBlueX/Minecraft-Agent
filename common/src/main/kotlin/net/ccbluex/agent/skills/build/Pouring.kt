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
package net.ccbluex.agent.skills.build

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.aimAt
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.reach
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.item.Items

/**
 * Pours the liquid of [wanted] at [pos] from a bucket: into the placed block there when it holds water, else on a face
 * of a solid neighbour that shows through the empty cell, as the bucket empties in front of the face it looks at.
 * True once the liquid is there.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.pour(pos: BlockPos, wanted: BlockState): Boolean {
    val level = body.level
    val fluid = wanted.fluidState.type
    if (fluid == Fluid.EMPTY) return false
    val bucket = ItemMatch.of(if (fluid == Fluid.LAVA) Items.LAVA_BUCKET else Items.WATER_BUCKET)
    val into = !isLiquid(wanted)
    val targets = if (into) {
        listOf(pos to null)
    } else {
        Direction.entries.mapNotNull { side ->
            val against = pos.relative(side)
            val state = level.getBlockState(against)
            // Water poured at a block that can hold it goes into that block
            val holds = fluid == Fluid.WATER && state.hasProperty(BlockStateProperties.WATERLOGGED)
            if (state.getCollisionShape(level, against).isEmpty || holds) null else against to side.opposite
        }
    }
    for ((against, face) in targets) {
        if (isBuilt(level, pos, wanted)) return true
        if (!reach(against, face)) continue
        // Looked up anew each time, as holding it moves it into the hotbar
        val slot = body.player.inventory.slotOf(bucket)
        if (slot < 0) return false
        try {
            val hotbar = body.hold(slot)
            aimAt(against, face, hotbar) ?: continue
            body.selectHotbarSlot(hotbar)
            body.useItem(InteractionHand.MAIN_HAND)
            if (!tickConditional(20) { takeMovement(); isBuilt(level, pos, wanted) }) return true
        } finally {
            body.releaseHotbarSlot()
        }
    }
    return isBuilt(level, pos, wanted)
}
