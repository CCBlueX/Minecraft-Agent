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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.games.RoundSenses
import net.ccbluex.agent.skills.shop.Currency
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * What a Bed Wars round looks like to the agent, supplied by the host: a server from the minigame plugin, a client
 * from the sidebar, the beds and the shopkeepers it sees.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public interface Senses : RoundSenses {

    /**
     * The agent's team; null before teams are handed out and after its final death.
     */
    public val ownTeam: BedWarsTeam?

    /**
     * Every team of the round, the own one included.
     */
    public val teams: List<BedWarsTeam>

    public fun teamOf(player: Player): BedWarsTeam?

    /**
     * The generators known: those of the teams' islands and the neutral diamond and emerald ones.
     */
    public val generators: List<Generator>

    /**
     * Whether the agent is dead or spectating and comes back, its bed still standing.
     */
    public val isRespawning: Boolean

    public fun isRespawning(player: Player): Boolean = false

    /**
     * Whether a player placed the block during the round; map blocks are never broken for a bed.
     */
    public fun isPlayerPlaced(pos: BlockPos): Boolean

    /**
     * Whether the game lets players place a block there: inside the build heights, outside protected regions.
     */
    public fun canPlace(pos: BlockPos): Boolean = true
}

/**
 * A team of the round.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public interface BedWarsTeam {

    public val name: String

    /**
     * The middle of the team's island, where its players spawn.
     */
    public val spawn: Vec3

    /**
     * Either half of the team's bed; null while unknown.
     */
    public val bed: BlockPos?

    public val isBedAlive: Boolean

    /**
     * The players still in the round, respawning ones included; empty once the team is out.
     */
    public val members: List<Player>

    /**
     * Where the item shopkeeper stands; null without one.
     */
    public val shopkeeper: Vec3?

    /**
     * Where the team upgrades keeper stands; null without one.
     */
    public val upgrader: Vec3?

    /**
     * The chest the game keeps for the team by its spawn; null without one.
     */
    public val chest: BlockPos?
}

/**
 * A resource generator; [team] is the name of the team whose island it is on, null for a neutral one.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public data class Generator(
    public val resource: Currency,
    public val position: Vec3,
    public val team: String?,
)
