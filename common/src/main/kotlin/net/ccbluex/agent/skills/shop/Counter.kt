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

/**
 * Runs a purchase at the shop the agent stands at: on a server through the shop plugin, on a client by clicking
 * through the shop menu. It is called every tick of the purchase until it returns false; [ticks] counts from 0.
 * Whether the purchase went through is told by the money spent.
 *
 * @agent claude-opus-5-5
 */
public fun interface Counter {

    /**
     * One step of buying [offer]; whether more steps follow.
     */
    public fun step(offer: Offer, ticks: Int): Boolean
}
