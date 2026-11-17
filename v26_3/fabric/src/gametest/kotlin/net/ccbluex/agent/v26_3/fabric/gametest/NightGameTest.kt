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
import net.ccbluex.agent.skills.night.shelter
import net.ccbluex.agent.skills.night.sleepThroughNight
import net.ccbluex.agent.skills.night.waitForMorning
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.world.phys.Vec3

/**
 * A survival agent with a bed at night places it, sleeps through to the morning and takes the bed along again.
 *
 * @agent claude-opus-5-5
 */
internal val sleeping: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    give("minecraft:white_bed")
    run("time set 18000")
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        val bed = agent.run(context, 600, "Did not sleep through the night") { sleepThroughNight(null, takeBack = true) }
        checkNotNull(bed) { "Did not lie down" }
        awaitServer("block ${bed.x} ${bed.y} ${bed.z} minecraft:air")
        val beds = context.fromClient { mc -> checkNotNull(mc.player).inventory.let { inventory -> (0 until 36).count { inventory.getItem(it).`is`(ItemTags.BEDS) } } }
        check(beds == 1) { "Carries $beds beds after the night, not the one it took back" }
    } finally {
        run("time set day")
        fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    }
}

/**
 * A survival agent at night, standing off the middle of its block on plain ground, digs in three blocks deep, closes
 * the hole over its head, and comes out of its wait once it is day.
 *
 * @agent claude-opus-5-5
 */
internal val sheltering: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    run("time set 18000")
    run("tp $playerName 0.85 -60 0.5")
    awaitServer("entity @a[name=$playerName,x=0.85,y=-60,z=0.5,distance=..0.2]")
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        check(agent.run(context, 600, "Did not dig in") { shelter() }) { "Found nowhere to dig in" }
        val (feet, roofed) = context.fromClient { mc ->
            val player = checkNotNull(mc.player)
            val head = player.blockPosition().above(2)
            player.blockY to player.level().getBlockState(head).isFaceSturdy(player.level(), head, Direction.DOWN)
        }
        check(feet <= -63) { "Stands at y $feet, not at the bottom of a hole" }
        check(roofed) { "The hole is open above the head" }
        run("time set day")
        agent.run(context, 200, "Kept waiting once it was day") { waitForMorning() }
    } finally {
        run("time set day")
        moveTo(Vec3(3.5, -60.0, 3.5))
        fill(BlockPos(-4, -63, -4), BlockPos(4, -62, 4), "minecraft:dirt")
        fill(BlockPos(-4, -61, -4), BlockPos(4, -61, 4), "minecraft:grass_block")
        fill(BlockPos(-4, -60, -4), BlockPos(4, -56, 4), "minecraft:air")
    }
}
