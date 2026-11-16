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

package net.ccbluex.agent.skills.loot

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.skills.Agent

/**
 * Opens chests and takes what the agent wants from them.
 *
 * @agent claude-opus-5-5
 */
public interface Loot {

    /**
     * The chests of the round and which are done.
     */
    public val chests: Chests

    /**
     * Goes to [chest], opens it and takes what is wanted; walking there is part of it. True while it does so, also
     * when called again for the chest it is on; false when the chest is done or no container. Called on every tick
     * the chest is wanted: a tick without the call lets go of it, as the agent turned to something else.
     */
    public fun open(chest: BlockPos): Boolean

    /**
     * Whether a chest is being walked to, opened or emptied.
     */
    public val isLooting: Boolean

    public fun tick(agent: Agent)

    public companion object {

        @JvmStatic
        @JvmOverloads
        public fun builtIn(chests: Chests = Chests(), wanted: Wanted = Wanted.DEFAULT): Loot = Looting(chests, wanted)
    }
}
