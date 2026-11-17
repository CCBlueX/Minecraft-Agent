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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.world.Input

/**
 * [Body.safeWalk] as a player holds at a ledge: sneaking, without sprint, from the tick a step without sneaking
 * would carry it past a ledge, and for [HOLD_TICKS] after. For client bodies, since anticheats see a back-off at an
 * edge without sneaking; a server bot backs off as sneaking does without it.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public class LedgeSneak {

    private var ticks = 0

    /**
     * [input] as [body] presses it this tick while it holds at ledges.
     */
    public fun keys(body: Body, input: Input): Input = keys(input, atLedge = reachesLedge(body, input))

    internal fun keys(input: Input, atLedge: Boolean): Input {
        ticks = if (atLedge) HOLD_TICKS else maxOf(0, ticks - 1)
        return if (ticks > 0) input.withKeys(input.jump, sneak = true, sprint = false) else input
    }

    /**
     * For ticks the body does not hold at ledges.
     */
    public fun reset() {
        ticks = 0
    }

    /**
     * Whether a tick of [input] without sneaking takes the player standing on the ground past a ledge, other than a
     * jump. That tick still ends on the ground, as the box drops before it moves on, so only the sneak's back-off
     * tells; a tick later the player falls, sneaking or not.
     */
    private fun reachesLedge(body: Body, input: Input): Boolean {
        val player = body.player
        if (!player.onGround() || input.jump || input.shift) {
            return false
        }
        return body.simulateTick(input).hasBackedOffEdge
    }

    private companion object {
        /**
         * A sneak held a little past the edge, rather than let go of and pressed again every other tick.
         */
        const val HOLD_TICKS = 3
    }
}

/**
 * The player after one more tick holding [input], moved as the game moves it.
 *
 * @agent claude-opus-5-5
 */
internal fun Body.simulateTick(input: Input): PlayerSimulation.Steered {
    val simulated = simulate(SimulatedInput(input), inputYRot)
    simulated.tick()
    return simulated
}
