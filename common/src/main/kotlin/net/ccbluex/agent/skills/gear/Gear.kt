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

package net.ccbluex.agent.skills.gear

import net.ccbluex.agent.skills.Agent

/**
 * Keeps the best armor worn and the best weapon at hand.
 *
 * @agent claude-opus-5-5
 */
public interface Gear {

    public fun tick(agent: Agent)

    public companion object {

        /**
         * Puts on armor that beats the worn piece and keeps the best weapon in the hotbar.
         */
        @JvmStatic
        public fun builtIn(): Gear = Wardrobe()
    }
}
