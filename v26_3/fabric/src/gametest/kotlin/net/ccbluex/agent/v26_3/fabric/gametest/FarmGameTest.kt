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
import net.ccbluex.agent.skills.farm.Farm
import net.ccbluex.agent.skills.farm.farm
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.ccbluex.agent.math.AABB as AgentAABB
import net.ccbluex.agent.math.BlockPos as AgentBlockPos

/**
 * A survival agent on a small farm harvests the ripe wheat, carrots, sugar cane, melon, nether wart, cocoa and
 * berries, sows each crop again with the one seed of it it carries or with what it picked up, and sows the empty
 * farmland beside the carrots.
 *
 * @agent claude-opus-5-5
 */
internal val farming: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-7, -60, -7), BlockPos(7, -55, 7), "minecraft:air")
    setBlock(BlockPos(-4, -61, 2), "minecraft:water")
    setBlock(BlockPos(4, -61, 2), "minecraft:water")
    fill(BlockPos(-3, -61, 2), BlockPos(3, -61, 2), "minecraft:farmland")
    fill(BlockPos(-3, -60, 2), BlockPos(-1, -60, 2), "minecraft:wheat[age=7]")
    fill(BlockPos(0, -60, 2), BlockPos(1, -60, 2), "minecraft:carrots[age=7]")
    fill(BlockPos(-5, -60, 2), BlockPos(-5, -58, 2), "minecraft:sugar_cane")
    setBlock(BlockPos(-2, -61, -2), "minecraft:farmland")
    // The melon first: a stem placed with nothing beside it falls back to an unattached one
    setBlock(BlockPos(-1, -60, -2), "minecraft:melon")
    setBlock(BlockPos(-2, -60, -2), "minecraft:attached_melon_stem[facing=east]")
    setBlock(BlockPos(1, -61, -2), "minecraft:soul_sand")
    setBlock(BlockPos(1, -60, -2), "minecraft:nether_wart[age=3]")
    setBlock(BlockPos(3, -60, -4), "minecraft:jungle_log")
    setBlock(BlockPos(3, -60, -3), "minecraft:cocoa[age=2,facing=north]")
    setBlock(BlockPos(-4, -60, -2), "minecraft:sweet_berry_bush[age=3]")
    awaitServer("block -4 -60 -2 minecraft:sweet_berry_bush")
    for (seed in listOf("wheat_seeds", "carrot", "nether_wart", "cocoa_beans")) give("minecraft:$seed")
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        val area = AgentAABB(-6.0, -61.0, -5.0, 5.99, -57.0, 4.99)
        val harvested = agent.run(context, 2400, "Did not finish the farm") { farm(Farm(area)) }
        check(harvested >= 10) { "Harvested $harvested crops, not all 10" }
        for (x in -3..1) awaitServer("block $x -60 2 #minecraft:crops")
        awaitServer("block 2 -60 2 #minecraft:crops")
        awaitServer("block -5 -59 2 minecraft:air")
        awaitServer("block -5 -60 2 minecraft:sugar_cane")
        awaitServer("block -1 -60 -2 minecraft:air")
        awaitServer("block 1 -60 -2 minecraft:nether_wart")
        awaitServer("block 3 -60 -3 minecraft:cocoa")
        check(carried(Items.WHEAT) == 3) { "Carries ${carried(Items.WHEAT)} wheat, not 3" }
        check(carried(Items.SWEET_BERRIES) > 0) { "Picked no berries" }
        check(carried(Items.MELON_SLICE) > 0) { "Picked up no melon" }
    } finally {
        fill(BlockPos(-7, -61, -7), BlockPos(7, -61, 7), "minecraft:grass_block")
        fill(BlockPos(-7, -60, -7), BlockPos(7, -55, 7), "minecraft:air")
    }
}

/**
 * A survival agent with a hoe, a water bucket and eight wheat seeds on a meadow pours the water into the middle of a
 * five by five patch, tills the rest into farmland and sows it.
 *
 * @agent claude-opus-5-5
 */
internal val tilling: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    give("minecraft:wooden_hoe")
    give("minecraft:water_bucket")
    give("minecraft:wheat_seeds", 8)
    moveTo(Vec3(3.5, -60.0, 3.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        val farm = Farm(AgentAABB(-2.0, -61.0, -2.0, 2.99, -60.0, 2.99), canTill = true, canHydrate = true, pickUp = null)
        agent.run(context, 2400, "Did not finish tilling") { farm(farm) }
        awaitServer("block 0 -61 0 minecraft:water")
        val (untilled, wheat) = context.fromClient { mc ->
            val level = checkNotNull(mc.level)
            val patch = BlockPos.betweenClosed(BlockPos(-2, -61, -2), BlockPos(2, -61, 2)).map { it.immutable() }
            patch.filter { it != BlockPos(0, -61, 0) && !level.getBlockState(it).`is`(Blocks.FARMLAND) }
                .map { "${it.toShortString()} ${level.getBlockState(it)} under ${level.getBlockState(it.above())}" } to
                patch.count { level.getBlockState(it.above()).`is`(Blocks.WHEAT) }
        }
        check(untilled.isEmpty()) { "Left untilled: $untilled" }
        check(wheat == 8) { "Sowed $wheat wheat, not 8" }
    } finally {
        fill(BlockPos(-4, -61, -4), BlockPos(4, -61, 4), "minecraft:grass_block")
        fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    }
}

/**
 * A survival agent whose inventory is full, of stone and of more potatoes than its chest holds, empties the potatoes
 * into the farm's chest, keeping one stack for sowing, then harvests the ripe wheat and picks it up.
 *
 * @agent claude-opus-5-5
 */
internal val depositing: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    setBlock(BlockPos(1, -61, 2), "minecraft:water")
    fill(BlockPos(-2, -61, 2), BlockPos(0, -61, 2), "minecraft:farmland")
    fill(BlockPos(-2, -60, 2), BlockPos(0, -60, 2), "minecraft:wheat[age=7]")
    setBlock(BlockPos(3, -60, -2), "minecraft:chest")
    awaitServer("block 3 -60 -2 minecraft:chest")
    give("minecraft:stone", 8 * 64)
    give("minecraft:potato", 28 * 64)
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        val farm = Farm(AgentAABB(-3.0, -61.0, -3.0, 3.99, -59.0, 3.99), canPlant = false, chest = AgentBlockPos(3, -60, -2))
        agent.run(context, 1200, "Did not finish the farm") { farm(farm) }
        awaitServer("items block 3 -60 -2 container.* minecraft:potato")
        check(carried(Items.POTATO) == 64) { "Kept ${carried(Items.POTATO)} potatoes, not one stack" }
        check(carried(Items.WHEAT) == 3) { "Carries ${carried(Items.WHEAT)} wheat, not 3" }
    } finally {
        setBlock(BlockPos(3, -60, -2), "minecraft:air")
        fill(BlockPos(-4, -61, -4), BlockPos(4, -61, 4), "minecraft:grass_block")
        fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    }
}
