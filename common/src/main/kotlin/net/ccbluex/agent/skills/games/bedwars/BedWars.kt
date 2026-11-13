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
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.games.GameMode
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import java.util.Random

/**
 * Bed Wars as an agent plays it, any team size. Every two seconds or so it weighs what it could do: cover the bed,
 * defend the base, gear up, gather, fetch diamonds or emerald, buy team upgrades, rush a bed, hunt a bedless team or
 * help a teammate, by the state of the round, its traits and what its teammates in [match] already do. Enemies close
 * by or at the bed are fought whatever it does. It leaves generators to real teammates, puts what it has to spare
 * into the team chest for them, and tells its team what it is up to through [callouts]. Buying goes through the
 * agent's shop ability; without one it plans with what it has.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class BedWars @JvmOverloads constructor(
    override val senses: Senses,
    public val match: Match = Match(),
    public val strategy: Strategy = Strategy.ADAPTIVE,
    callouts: Callouts = Callouts.NONE,
    random: Random = Random(),
) : GameMode {

    private val activities = Activities(this, senses, match, strategy, callouts, random)

    override val name: String
        get() = "BedWars"

    override val phase: String
        get() = activities.activity?.name ?: "WAITING"

    override val goal: String?
        get() = activities.goal

    override fun tick(agent: Agent) {
        activities.tick(agent)
    }

    override fun damaged(agent: Agent, attacker: Entity?) {
        activities.damaged(attacker)
    }

    override fun stop(agent: Agent) {
        activities.stop()
    }

    /**
     * Whether the agent may pick [item] up: never one a real teammate stands by.
     */
    public fun takes(item: ItemEntity): Boolean {
        val team = senses.ownTeam ?: return true
        val self = activities.player
        return team.members.none { mate ->
            mate !== self && !senses.isBot(mate) && !mate.isSpectator && mate.level() === item.level() &&
                mate.position().distanceTo(item.position()) < OCCUPIED
        }
    }

    internal fun bedDestroyed(team: String, breaker: Player?) {
        activities.bedDestroyed(team, breaker)
    }
}
