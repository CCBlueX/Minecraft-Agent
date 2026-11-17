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

package net.ccbluex.agent.v26_3.fabric.gametest

import kotlinx.coroutines.launch
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.Skills
import net.ccbluex.agent.skills.build.BuildResult
import net.ccbluex.agent.skills.build.build
import net.ccbluex.agent.tick.TickScope
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.v26_3.toAgent
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/**
 * Runs the build skill for the local player as a client host would: an [Agent] on a [ClientNavigation], its skills
 * waiting on a [TickScope] ticked at the start of every client tick. Make it on the client thread.
 *
 * @agent claude-opus-5-5
 */
internal class TestBuilder {

    private val navigation = ClientNavigation()
    private val agent = Agent(navigation.body, navigation.navigator, Skills())
    private val scope = TickScope()
    private val tick = Runnable { scope.tick() }

    @Volatile
    var result: BuildResult? = null

    fun start(context: ClientGameTestContext, blocks: Map<BlockPos, BlockState>, canBreak: Boolean) = context.onClient {
        navigation.agent = agent
        GameTicks.clientTick += tick
        val wanted = blocks.entries.associate { it.key.toAgent() to it.value.toAgent() }
        scope.launch { result = agent.build(wanted, canBreak) }
    }

    fun stop(context: ClientGameTestContext) = context.onClient {
        scope.cancel()
        GameTicks.clientTick -= tick
        navigation.agent = null
        agent.stop()
    }
}
