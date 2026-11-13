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

import net.minecraft.world.entity.player.Player

/**
 * What a client learns about a round from text: the sidebar, chat, titles and the tab list. A [LevelRoundSenses] reads
 * the rest, the blocks and entities, from the level.
 *
 * @agent claude-sonnet-5-5
 */
public interface RoundFacts {

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
     * The name the server calls the agent by, as the players of the level carry it; null before it is known.
     */
    public val ownName: String?

    public fun isFriend(player: Player): Boolean = false

    /**
     * Whether [player] is an NPC or a bot, such as one the tab list does not show.
     */
    public fun isBot(player: Player): Boolean = false
}
