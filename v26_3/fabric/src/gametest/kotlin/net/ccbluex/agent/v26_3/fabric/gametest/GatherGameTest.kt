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

@file:OptIn(ExperimentalAgentApi::class)

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.navigation.BreakableBlocks
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.skills.gather.Resource
import net.ccbluex.agent.skills.gather.explore
import net.ccbluex.agent.skills.gather.gather
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * The options a survival agent on a shared server walks with: it digs and builds its way, through natural blocks only.
 */
internal val SURVIVAL_OPTIONS = Options(canBreak = true, canPlace = true, breakableBlocks = BreakableBlocks.NATURAL)

/**
 * A survival agent gathers logs from a tree, not from the bare post of oak logs standing nearer, and picks them up.
 *
 * @agent claude-opus-5-5
 */
internal val gatherLogs: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-8, -60, -8), BlockPos(10, -50, 8), "minecraft:air")
    fill(BlockPos(5, -57, -1), BlockPos(7, -56, 1), "minecraft:oak_leaves[persistent=true]")
    fill(BlockPos(6, -60, 0), BlockPos(6, -57, 0), "minecraft:oak_log")
    fill(BlockPos(-4, -60, 0), BlockPos(-4, -58, 0), "minecraft:oak_log")
    awaitServer("block -4 -58 0 minecraft:oak_log")
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        val gathered = agent.run(context, 1200, "Did not gather 3 logs") { gather(Resource.LOGS, 3) }
        check(gathered) { "The gather ended without a log" }
        val logs = carried(Items.OAK_LOG)
        check(logs >= 3) { "Carries $logs oak logs, not 3" }
        awaitServer("block -4 -58 0 minecraft:oak_log")
    } finally {
        fill(BlockPos(-8, -60, -8), BlockPos(10, -50, 8), "minecraft:air")
    }
}

/**
 * A survival agent with a wooden pickaxe on a mound of stone under a layer of dirt, no stone in sight, mines a
 * staircase down into it and gathers the stone it opens up.
 *
 * @agent claude-opus-5-5
 */
internal val gatherStone: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-6, -60, -6), BlockPos(6, -53, 6), "minecraft:dirt")
    fill(BlockPos(-5, -60, -5), BlockPos(5, -54, 5), "minecraft:stone")
    awaitServer("block 0 -54 0 minecraft:stone")
    give("minecraft:wooden_pickaxe")
    moveTo(Vec3(0.5, -52.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        agent.run(context, 2400, "Did not gather 4 stone") {
            var gathered = 0
            while (body.player.inventory.countItem(Items.COBBLESTONE.toAgent()) < 4 && gathered++ < 5) gather(Resource.STONE, 4)
        }
        val stone = carried(Items.COBBLESTONE)
        check(stone >= 4) { "Carries $stone cobblestone, not 4" }
    } finally {
        fill(BlockPos(-6, -60, -6), BlockPos(6, -50, 6), "minecraft:air")
    }
}

/**
 * A survival agent on open ground walks off some way to look around.
 *
 * @agent claude-opus-5-5
 */
internal val exploring: GameTestScope.() -> Unit = {
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    val start = context.playerPosition()
    check(agent.run(context, 800, "Did not explore") { explore() }) { "Found nowhere to explore" }
    val walked = context.playerPosition().distanceTo(start)
    check(walked > 20.0) { "Walked only %.1f blocks".format(walked) }
}

/**
 * How many of [item] the player carries.
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.carried(item: Item): Int = context.fromClient { checkNotNull(it.player).inventory.countItem(item) }
