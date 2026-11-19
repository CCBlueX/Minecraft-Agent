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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.isSeen
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.BLOCK_REACH
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.aimAt
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.item.Items

/**
 * Pours the liquid of [wanted] at [pos] from a bucket: on a face of the placed block there when it holds water, else
 * on a face of a solid neighbour that shows through the empty cell, as the bucket empties in front of the face it
 * looks at, from a spot clear of where the liquid runs at first, up on [scaffold] if need be. True once the liquid is
 * there.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Agent.pour(pos: BlockPos, wanted: BlockState, scaffold: Scaffold): Boolean {
    val level = body.level
    val fluid = wanted.fluidState.type
    if (fluid == Fluid.EMPTY) return false
    val bucket = ItemMatch.of(if (fluid == Fluid.LAVA) Items.LAVA_BUCKET else Items.WATER_BUCKET)
    val into = !isLiquid(wanted)
    // The cell and the open cells beside and under it, where the liquid runs before it settles
    val wet = if (into) emptyList() else (Direction.entries - Direction.UP).map(pos::relative).filter { level.getBlockState(it).canBeReplaced() } + pos
    fun isDry(feet: Vec3): Boolean {
        val box = body.player.boundingBox.move(feet.subtract(body.player.position()))
        return wet.none { box.intersects(AABB(it)) }
    }
    val clicks = clicks(pos, scaffold, into) { click, _ ->
        // Water poured at a block that can hold it goes into that block
        val holds = fluid == Fluid.WATER && level.getBlockState(click.against).hasProperty(BlockStateProperties.WATERLOGGED)
        (into || !holds) && isDry(click.stand ?: body.player.position())
    }
    val usable = if (scaffold.hasSpare()) clicks else clicks.filter { !it.needsScaffold }
    for ((stand, group) in usable.groupBy { it.stand }.entries.take(3)) {
        if (stand != null) {
            if (group.first().needsScaffold && !scaffold.carriesSpare()) scaffold.free(BlockPos.containing(stand).below())
            stepTo(stand, pos, scaffold)
        }
        if (!isDry(body.player.position())) continue
        for (click in group) {
            val eye = body.player.eyePosition
            if (eye.distanceTo(click.point) > BLOCK_REACH || !level.isSeen(eye, click.point, click.against, click.face, body.player)) continue
            if (pourOn(bucket, click.against, click.face, pos, wanted)) return true
        }
    }
    return isBuilt(level, pos, wanted)
}

/**
 * Empties the bucket [bucket] at [face] of [against], or at any face of it in sight for null; true once [wanted]
 * stands at [pos].
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.pourOn(bucket: ItemMatch, against: BlockPos, face: Direction?, pos: BlockPos, wanted: BlockState): Boolean {
    val level = body.level
    // Looked up anew each time, as holding it moves it into the hotbar
    val slot = body.player.inventory.slotOf(bucket)
    if (slot < 0) return false
    try {
        val hotbar = body.hold(slot)
        // A sneaking player's bucket pours beside a block that holds water instead of into it; the server learns of
        // the sneak ending a tick after the client
        var standing = 0
        tickUntil { ticks ->
            takeMovement()
            body.selectHotbarSlot(hotbar)
            standing = if (body.player.isShiftKeyDown) 0 else standing + 1
            standing >= 2 || ticks >= 10
        }
        val hit = aimAt(against, face, hotbar)
        hit ?: return false
        body.selectHotbarSlot(hotbar)
        val used = body.useItem(InteractionHand.MAIN_HAND)
        tickConditional(20) { takeMovement(); isBuilt(level, pos, wanted) }
    } finally {
        body.releaseHotbarSlot()
    }
    return isBuilt(level, pos, wanted)
}
