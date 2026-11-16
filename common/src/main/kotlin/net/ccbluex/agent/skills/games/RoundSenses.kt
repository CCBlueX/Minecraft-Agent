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

package net.ccbluex.agent.skills.games

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.Player

/**
 * Facts about the round every game needs, supplied by the host for its place: a client from what the server shows,
 * a server from the minigame plugin. Each game's senses extend it.
 *
 * @agent claude-opus-5-5
 */
public interface RoundSenses {

    public val state: RoundState

    /**
     * Seconds until the round starts while [RoundState.COUNTDOWN]; null when unknown or not counting down.
     */
    public val countdownSeconds: Int?

    /**
     * Seconds since the round started; 0 before.
     */
    public val matchSeconds: Int

    /**
     * Whether [entity] is someone to fight: alive, of another team, not a spectator, NPC or friend.
     */
    public fun isOpponent(entity: Entity): Boolean

    /**
     * Whether [entity] plays on the agent's own team.
     */
    public fun isTeammate(entity: Entity): Boolean

    /**
     * Whether [player] is a bot or NPC rather than a real player, as far as the host knows.
     */
    public fun isBot(player: Player): Boolean = false

    /**
     * Whether the agent only watches: dead and out, or joined as a spectator.
     */
    public val isSpectator: Boolean

    /**
     * Where the agent started the round: its cage, island, pedestal or team spawn; null when unknown.
     */
    public val ownSpawn: Vec3?
}

/**
 * Where a round is.
 *
 * @agent claude-opus-5-5
 */
public enum class RoundState {

    /**
     * Waiting for players, no countdown running.
     */
    LOBBY,

    /**
     * The countdown to the start runs.
     */
    COUNTDOWN,

    PLAYING,

    /**
     * Over: celebrating, resetting or gone.
     */
    ENDED,
}
