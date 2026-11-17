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
import net.ccbluex.agent.v26_3.fabric.ClientBody
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentListener
import net.ccbluex.agent.skills.Skills
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.tick.TickScope
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * An [Agent] on the local player, ticked every player tick through a [ClientNavigation] as a mod would run one, its
 * skill steps written to the test log; make it on the client thread.
 *
 * @agent claude-opus-5-5
 */
internal class TestAgent(options: Options, skills: (ClientBody) -> Skills = { Skills() }) {

    val body = ClientBody()

    private val navigation = ClientNavigation(body, options)

    val agent = Agent(body, navigation.navigator, skills(body), listener = object : AgentListener {
        override fun step(description: String) = logger.info("[agent] {}", description)
    })

    /**
     * Plays [game] until [isDone] holds, failing with [failure] after [ticks]; the ticks it took.
     */
    fun play(context: ClientGameTestContext, game: GameMode, ticks: Int, failure: String, isDone: () -> Boolean): Int {
        context.onClient {
            agent.game = game
            navigation.agent = agent
        }
        try {
            return await(context, ticks, failure) { context.fromClient { isDone() } }
        } finally {
            context.onClient {
                navigation.agent = null
                agent.stop()
                agent.game = null
            }
        }
    }

    /**
     * Runs [skill] to its end, failing with [failure] after [ticks]; what it returned.
     */
    fun <T> run(context: ClientGameTestContext, ticks: Int, failure: String, skill: suspend Agent.() -> T): T {
        val play = Play(skill)
        play(context, play, ticks, failure) { play.result != null }
        return checkNotNull(play.result).getOrThrow()
    }

    /**
     * A game mode that runs [skill] once on the agent's tick scope.
     *
     * @agent claude-opus-5-5
     */
    private class Play<T>(private val skill: suspend Agent.() -> T) : GameMode {

        override val name: String = "Test"

        override val phase: String = "Playing"

        override val goal: String? = null

        @Volatile
        var result: Result<T>? = null

        private val scope = TickScope()
        private var started = false

        override fun tick(agent: Agent) {
            if (!started) {
                started = true
                scope.launch { result = runCatching { agent.skill() } }
            }
            scope.tick()
        }

        override fun stop(agent: Agent) = scope.cancel()
    }
}
