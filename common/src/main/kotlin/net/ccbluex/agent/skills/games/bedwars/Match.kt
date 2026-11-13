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
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import java.util.Random

/**
 * One running Bed Wars round as its agents see it: each team's plan, shared by the agents of the team. A host makes
 * one per round and hands it to every [BedWars] playing it; an agent alone makes its own.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class Match @JvmOverloads constructor(private val random: Random = Random()) {

    private val plans = HashMap<String, TeamPlan>()
    private val players = LinkedHashSet<BedWars>()

    internal fun plan(team: String): TeamPlan = plans.getOrPut(team) { TeamPlan(team, random) }

    internal fun join(game: BedWars) {
        players += game
    }

    internal fun leave(game: BedWars) {
        players -= game
        for (plan in plans.values) plan.leave(game)
    }

    /**
     * The bed of [team] was broken, by [breaker] when known; the agents of the round think again at once.
     */
    public fun bedDestroyed(team: String, breaker: Player?) {
        for (game in players.toList()) game.bedDestroyed(team, breaker)
    }

    /**
     * The blocks the agents of [team] mean to place: the bed defence they work on and their bridges.
     */
    public fun planned(level: BlockGetter, team: BedWarsTeam): Set<BlockPos> =
        plans[team.name]?.planned(level, team.isBedAlive) ?: emptySet()

    /**
     * Forgets the round: plans and agents.
     */
    public fun clear() {
        plans.clear()
        players.clear()
    }
}
