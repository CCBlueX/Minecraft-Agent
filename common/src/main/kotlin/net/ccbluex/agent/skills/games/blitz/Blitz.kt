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

package net.ccbluex.agent.skills.games.blitz

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.skills.games.RoundSenses
import net.minecraft.world.entity.Entity

/**
 * Blitz Survival Games on one large map: the agent runs from its pedestal to the center and loots its chests first,
 * stops to eat and sort its gear, and keeps looting while under-equipped. Once equipped it hunts the closest players,
 * opening chests close to the way, and looks for players when nobody is in sight. After the deathmatch teleport it
 * loots and eats through the countdown, then fights everyone.
 *
 * One instance plays for one agent; a new round needs a new instance.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class Blitz(private val blitzSenses: Senses) : GameMode {

    private var round: Round? = null

    override val name: String
        get() = "Blitz"

    override val phase: String
        get() = round?.phase?.label ?: Phase.IDLE.label

    override val goal: String?
        get() = round?.goal

    override val senses: RoundSenses
        get() = blitzSenses

    override fun tick(agent: Agent) {
        val current = round?.takeIf { it.agent === agent } ?: Round(agent, blitzSenses).also { round = it }
        current.tick()
    }

    override fun damaged(agent: Agent, attacker: Entity?) {
        round?.targets?.damaged(attacker)
    }

    override fun stop(agent: Agent) {
        round?.stop()
        round = null
    }
}
