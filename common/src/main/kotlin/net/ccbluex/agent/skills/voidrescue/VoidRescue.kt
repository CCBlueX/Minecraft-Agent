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

package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.world.Input

/**
 * Keeps the agent out of the void: holds back walks off an edge, and catches falls that happen anyway.
 *
 * @agent claude-opus-5-5
 */
public interface VoidRescue {

    /**
     * Whether a fall into the void is being caught; the agent's movement is its own meanwhile.
     */
    public val isRescuing: Boolean

    public fun tick(agent: Agent)

    /**
     * The keys to press instead of [input], which the navigator or an ability such as the fight pressed this tick:
     * as little held back as keeps the agent from falling into the void. [Agent] calls it after the navigator.
     */
    public fun guard(agent: Agent, input: Input): Input = input

    public companion object {

        @JvmStatic
        public fun builtIn(): VoidRescue = VoidSafety()
    }
}
