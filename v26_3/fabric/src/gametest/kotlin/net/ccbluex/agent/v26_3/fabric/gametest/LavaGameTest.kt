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
import net.ccbluex.agent.skills.gather.fillLava
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * A survival agent with an empty bucket walks up to a lava source sunk into the ground and scoops it up, unhurt.
 *
 * @agent claude-opus-5-5
 */
internal val fillingLava: GameTestScope.() -> Unit = {
    run("clear $playerName")
    fill(BlockPos(-4, -60, -4), BlockPos(10, -56, 4), "minecraft:air")
    setBlock(BlockPos(7, -61, 0), "minecraft:lava")
    awaitServer("block 7 -61 0 minecraft:lava")
    give("minecraft:bucket")
    moveTo(Vec3(0.5, -60.0, 0.5))
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    try {
        unhurt("Filling the bucket") {
            check(agent.run(context, 600, "Did not fill the bucket") { fillLava() }) { "Came back without lava" }
        }
        check(carried(Items.LAVA_BUCKET) == 1) { "Carries no lava bucket" }
        awaitServer("block 7 -61 0 minecraft:air")
    } finally {
        setBlock(BlockPos(7, -61, 0), "minecraft:grass_block")
    }
}
