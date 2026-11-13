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
import net.ccbluex.agent.skills.games.RoundSenses
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * What a host tells a [SkyWars] agent about the round. [RoundSenses.ownSpawn] is the own cage; without it the agent
 * finds its cage by the blocks around it and takes where it dropped out as its island.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public interface Senses : RoundSenses {

    /**
     * Every cage of the map, the own and teammates' included, at feet height; empty when unknown.
     */
    public val cages: List<Vec3>

    /**
     * Over the middle island, such as the spectator spawn; null when unknown, then the middle of the chests counts.
     */
    public val middle: Vec3?

    /**
     * The chests of the map the host knows of, islands and middle.
     */
    public val chests: Collection<BlockPos>

    /**
     * Whether [chest] is one of the middle's; unknown chests within a few blocks of [middle] count as well.
     */
    public fun isMiddleChest(chest: BlockPos): Boolean = false

    /**
     * Times the chests were refilled this round; each refill makes every chest worth opening again.
     */
    public val refills: Int

    /**
     * Seconds until the next refill; null when none is due or it is unknown.
     */
    public val secondsToRefill: Int?

    /**
     * Whether the border closes in or the deathmatch began, which drives everyone to the middle.
     */
    public val isDoom: Boolean

    /**
     * Whether a real player looks into [chest] now; they get to pick from it first.
     */
    public fun isChestInUse(chest: BlockPos): Boolean = false

    /**
     * Ticks since a real player last closed [chest] after the last refill; null when none opened it since.
     */
    public fun ticksSinceChestClosed(chest: BlockPos): Int? = null
}
