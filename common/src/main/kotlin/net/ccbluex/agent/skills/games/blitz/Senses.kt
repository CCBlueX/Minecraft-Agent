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
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.skills.games.RoundSenses

/**
 * What a host tells a [Blitz] agent about the round. [RoundSenses.state] turns to playing when the games begin;
 * [RoundSenses.ownSpawn] is the pedestal, when known.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public interface Senses : RoundSenses {

    /**
     * The chests of the map the host knows of.
     */
    public val chests: Collection<BlockPos>

    /**
     * Times the chests were refilled this round; each refill makes every chest worth opening again.
     */
    public val refills: Int

    /**
     * Seconds until the next refill; null when none is due or it is unknown.
     */
    public val secondsToRefill: Int?

    /**
     * Seconds after the start the kit items arrive; null without a kit or before it is known.
     */
    public val kitDelaySeconds: Int?

    /**
     * Seconds until the deathmatch: before its teleport the time to the teleport, during the countdown after it the
     * time to the fight; null when not announced.
     */
    public val secondsToDeathmatch: Int?

    /**
     * Whether the server said the deathmatch fight began.
     */
    public val isDeathmatchStarted: Boolean

    /**
     * Where a carried compass points once the server set it onto a player; null without a compass or before.
     */
    public val compassTarget: BlockPos?
}
