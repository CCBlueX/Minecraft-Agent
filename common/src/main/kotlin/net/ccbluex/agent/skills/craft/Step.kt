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

package net.ccbluex.agent.skills.craft

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.gather.Prey
import net.ccbluex.agent.skills.gather.Resource
import net.ccbluex.agent.world.item.Item

/**
 * The next thing to do towards items the agent wants, as [nextStep] plans it; each is one skill call.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public sealed interface Step {

    /**
     * Mines [count] blocks of [resource] ([net.ccbluex.agent.skills.gather.gather]).
     */
    public data class Gather(public val resource: Resource, public val count: Int) : Step

    /**
     * Crafts [recipe] [times] times ([craft]).
     */
    public data class Craft(public val recipe: GridRecipe, public val times: Int) : Step

    /**
     * Smelts [count] of [input] with [fuels], the best first ([smelt]).
     */
    public data class Smelt(public val input: Set<Item>, public val count: Int, public val fuels: List<Item>) : Step

    /**
     * Hunts for [prey] ([net.ccbluex.agent.skills.gather.huntFor]).
     */
    public data class Hunt(public val prey: Prey) : Step

    /**
     * Nothing it knows leads to the items; [reason] says why.
     */
    public data class Stuck(public val reason: String) : Step
}
