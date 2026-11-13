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

/**
 * The rules a duel plays by, besides the items the fighters get.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public enum class Mode {
    /**
     * Fight until one dies or falls out.
     */
    CLASSIC,

    /**
     * Knock the other off the platform; hits do no damage.
     */
    SUMO,

    /**
     * Hits do no damage; a hundred hits win.
     */
    BOXING,

    /**
     * Snow breaks under the fighters; whoever falls loses.
     */
    SPLEEF,

    /**
     * The ground falls away where the fighters step; no hits.
     */
    TNT_RUN,

    /**
     * Whoever holds the TNT when its fuse runs out loses; a hit passes it on.
     */
    TNT_TAG,

    /**
     * Lava rises layer by layer.
     */
    FLOOR_IS_LAVA,

    /**
     * Both fighters get a random item now and then.
     */
    PILLAR_OF_FORTUNE,
}
