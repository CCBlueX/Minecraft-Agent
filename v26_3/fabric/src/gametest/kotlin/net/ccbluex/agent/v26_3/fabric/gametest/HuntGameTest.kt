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
import net.ccbluex.agent.skills.gather.Prey
import net.ccbluex.agent.skills.gather.huntFor
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.summon
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * A survival agent with a sword hunts the free cow for food and leaves the one in a fenced pen alone, though that
 * one stands nearer.
 *
 * @agent claude-opus-5-5
 */
internal val hunting: GameTestScope.() -> Unit = {
    run("clear $playerName")
    run("kill @e[type=minecraft:cow]")
    run("kill @e[type=minecraft:item]")
    fill(BlockPos(-10, -60, -4), BlockPos(12, -55, 4), "minecraft:air")
    fill(BlockPos(-8, -60, -2), BlockPos(-4, -60, 2), "minecraft:oak_fence hollow")
    summon("minecraft:cow", BlockPos(-6, -60, 0), "{NoAI:1b}")
    summon("minecraft:cow", BlockPos(9, -60, 0), "{NoAI:1b}")
    awaitServer("entity @e[type=minecraft:cow,x=9,y=-60,z=0,distance=..2]")
    give("minecraft:stone_sword")
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        check(agent.run(context, 1200, "Did not finish the hunt") { huntFor(Prey.FOOD) }) { "The hunt got nothing" }
        check(carried(Items.BEEF) > 0) { "Picked up no beef" }
        awaitServer("entity @e[type=minecraft:cow,x=-6,y=-60,z=0,distance=..2]")
    } finally {
        run("kill @e[type=minecraft:cow]")
        fill(BlockPos(-10, -60, -4), BlockPos(12, -55, 4), "minecraft:air")
    }
}
