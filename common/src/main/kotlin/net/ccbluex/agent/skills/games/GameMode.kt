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

package net.ccbluex.agent.skills.games

import net.ccbluex.agent.skills.Agent
import net.minecraft.world.entity.Entity

/**
 * How an [Agent] plays one game, ticked by [Agent.tick] before the abilities.
 *
 * @agent claude-opus-5-5
 */
public interface GameMode {

    public val name: String

    /**
     * The phase or activity it is in now, such as `Looting`.
     */
    public val phase: String

    /**
     * What it is going for, shown to players or in logs; null when nothing in particular.
     */
    public val goal: String?

    /**
     * The round facts this game reads, for abilities that need them, such as who is an opponent.
     */
    public val senses: RoundSenses?
        get() = null

    public fun tick(agent: Agent)

    /**
     * The body took a hit; [attacker] is null for falls, fire and the like.
     */
    public fun damaged(agent: Agent, attacker: Entity?) {}

    /**
     * The agent stops playing: undo what the game mode changed.
     */
    public fun stop(agent: Agent) {}
}
