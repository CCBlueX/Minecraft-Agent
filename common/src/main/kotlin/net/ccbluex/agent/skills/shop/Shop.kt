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

package net.ccbluex.agent.skills.shop

import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody

/**
 * Buys at a shop the agent stands at; walking there is the game mode's part.
 *
 * @agent claude-opus-5-5
 */
public interface Shop {

    public val catalogue: Catalogue

    /**
     * Starts buying [wish] at the shop in reach. False when nothing sells it, the agent cannot pay, a purchase is
     * under way, or a failed one is being waited out.
     */
    public fun buy(wish: Wish): Boolean

    /**
     * Whether a purchase is under way; the agent stands still meanwhile.
     */
    public val isBuying: Boolean

    /**
     * How the last finished purchase went: true bought, false failed; null before the first.
     */
    public val lastPurchase: Boolean?

    public fun tick(agent: Agent)

    public companion object {

        /**
         * Buys through [counter], which runs the purchase the host's way.
         */
        @JvmStatic
        public fun builtIn(body: AgentBody, catalogue: Catalogue, counter: Counter): Shop =
            Shopping(body, catalogue, counter)
    }
}
