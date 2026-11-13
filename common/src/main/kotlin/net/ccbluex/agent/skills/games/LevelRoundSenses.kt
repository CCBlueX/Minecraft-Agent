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

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * The round facts every game needs, read from a [level] and the [RoundFacts] a client takes from text. One instance
 * serves one round: it remembers where the agent stood when the round started.
 *
 * The host calls [tick] every client tick; what the level shows is read once a second.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public abstract class LevelRoundSenses(
    protected val level: Level,
    private val facts: RoundFacts,
) : RoundSenses {

    private var ticks = 0

    final override val state: RoundState
        get() = facts.state

    final override val countdownSeconds: Int?
        get() = facts.countdownSeconds

    final override val matchSeconds: Int
        get() = facts.matchSeconds

    private var startPosition: Vec3? = null

    /**
     * Where the agent stood the first tick it saw the round playing.
     */
    override val ownSpawn: Vec3?
        get() = startPosition

    /**
     * Whether the agent is out of the level or a spectator in it.
     */
    final override val isSpectator: Boolean
        get() = self?.isSpectator ?: true

    /**
     * The agent's own player; null while the level has none of that name.
     */
    protected val self: Player?
        get() = level.players().firstOrNull { it.scoreboardName == facts.ownName }

    final override fun isOpponent(entity: Entity): Boolean = entity is Player && entity !== self &&
        entity.isAlive && !entity.isSpectator && !isTeammate(entity) && !facts.isFriend(entity) && !isBot(entity)

    override fun isTeammate(entity: Entity): Boolean = false

    final override fun isBot(player: Player): Boolean = facts.isBot(player)

    /**
     * Reads the level, once every [SCAN_TICKS] calls.
     */
    protected abstract fun scan()

    public fun tick() {
        if (startPosition == null && state == RoundState.PLAYING) {
            startPosition = self?.position()
        }
        if (ticks++ % SCAN_TICKS == 0) {
            scan()
        }
    }

    private companion object {
        const val SCAN_TICKS = 20
    }
}
