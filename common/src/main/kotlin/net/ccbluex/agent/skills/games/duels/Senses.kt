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

package net.ccbluex.agent.skills.games.duels

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.skills.games.RoundSenses
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.LivingEntity

/**
 * What a one-on-one duel looks like to the agent, supplied by the host that runs it. [matchSeconds] counts the
 * fight, [RoundSenses.state] goes from the countdown to the fight to the end.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public interface Senses : RoundSenses {

    /**
     * The one the agent duels; null while they are not there.
     */
    public val opponent: LivingEntity?

    public val mode: Mode

    /**
     * Below this height a fighter is out of the duel.
     */
    public val fallHeight: Double

    /**
     * Where the fighters may build; null for anywhere.
     */
    public val battleArea: AABB?

    /**
     * How long the fight lasts before health decides it; null without a limit.
     */
    public val timeLimitSeconds: Int?

    /**
     * Whether health comes back by itself; off in modes such as UHC.
     */
    public val naturalRegen: Boolean
        get() = true

    /**
     * [Mode.FLOOR_IS_LAVA]: the height the lava rises to next; null in other modes.
     */
    public val nextLavaLevel: Int?
        get() = null

    /**
     * [Mode.FLOOR_IS_LAVA]: ticks until it rises; null in other modes.
     */
    public val ticksToLavaRise: Int?
        get() = null

    /**
     * [Mode.TNT_TAG]: who carries the TNT; null in other modes.
     */
    public val tagged: Entity?
        get() = null

    /**
     * How the duel went for the agent once [RoundSenses.state] is ended; null before.
     */
    public val result: Outcome?
}
