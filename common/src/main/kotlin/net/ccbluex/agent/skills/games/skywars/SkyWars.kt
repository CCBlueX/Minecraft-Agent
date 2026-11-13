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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.skills.games.RoundSenses
import net.minecraft.world.entity.Entity

/**
 * Sky Wars the way players play it, solo or in teams. The agent waits in its cage and loots its island, then every
 * couple of seconds scores what the round offers: the middle's chests, an island left empty, a player it can reach
 * and beat, two players fighting, digging its island for blocks, holding a spot behind a wall, backing off to heal,
 * its teammates, refilled chests, the middle once the border closes in. Whoever comes close or hits it, and players
 * out on a bridge within a shot, are fought at once. Its [net.ccbluex.agent.skills.Personality] weighs and stirs the
 * scores. Gaps are bridged, or flown over with [Options.flight].
 *
 * One instance plays for one agent; a new round needs a new instance.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class SkyWars @JvmOverloads constructor(
    private val skyWarsSenses: Senses,
    private val options: Options = Options(),
) : GameMode {

    private var round: Round? = null

    override val name: String
        get() = "SkyWars"

    override val phase: String
        get() = round?.phase ?: "Waiting"

    override val goal: String?
        get() = round?.goal

    override val senses: RoundSenses
        get() = skyWarsSenses

    override fun tick(agent: Agent) {
        val current = round?.takeIf { it.agent === agent } ?: Round(agent, skyWarsSenses, options).also { round = it }
        current.tick()
    }

    override fun damaged(agent: Agent, attacker: Entity?) {
        round?.damaged(attacker)
    }

    override fun stop(agent: Agent) {
        round?.stop()
        round = null
    }

    internal companion object {
        const val ISLAND_RADIUS = 6.0
    }
}
