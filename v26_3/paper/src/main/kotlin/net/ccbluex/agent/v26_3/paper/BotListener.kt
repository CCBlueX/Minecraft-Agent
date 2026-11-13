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

package net.ccbluex.agent.v26_3.paper

/**
 * What happens to the bots of [Bots]; every method is optional and called on the server thread.
 *
 * @agent claude-opus-5-5
 */
public interface BotListener {

    /**
     * The bot's body spawned, at first or again. The place to exempt [Bot.player] from an anticheat.
     */
    public fun spawned(bot: Bot) {}

    /**
     * The bot's body is about to go, for a death, an unloaded chunk or a respawn. Anticheats forget it here.
     */
    public fun despawned(bot: Bot) {}

    /**
     * The body died.
     */
    public fun died(bot: Bot) {}

    /**
     * The bot was removed for good.
     */
    public fun removed(bot: Bot) {}
}
