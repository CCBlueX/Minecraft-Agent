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

/**
 * Something an agent tells its team, as a player would in team chat. The host words it, or ignores it; the agent
 * calls out at most every 12 seconds and the same thing at most every 45.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public sealed interface Callout {

    public data object CoveringBed : Callout

    public data object GettingDiamonds : Callout

    public data object GettingEmeralds : Callout

    public data object ComingToHelp : Callout

    public data object NeedWool : Callout

    /**
     * Sets out to break [team]'s bed.
     */
    public data class GoingFor(val team: String) : Callout

    /**
     * [enemy] comes for the team's base.
     */
    public data class Incoming(val enemy: String) : Callout

    /**
     * Bought the team upgrade [upgrade], a key of `skills.shop.Wishes` such as `upgrade-forge`.
     */
    public data class BoughtUpgrade(val upgrade: String) : Callout

    /**
     * Put [count] resources it does not need into the team chest.
     */
    public data class LeftResources(val count: Int) : Callout

    public data class BrokeBed(val team: String) : Callout

    /**
     * The own bed is gone.
     */
    public data object BedLost : Callout
}

/**
 * Where an agent's [Callout]s go.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public fun interface Callouts {

    public fun say(callout: Callout)

    public companion object {
        @JvmField
        public val NONE: Callouts = Callouts { }
    }
}
