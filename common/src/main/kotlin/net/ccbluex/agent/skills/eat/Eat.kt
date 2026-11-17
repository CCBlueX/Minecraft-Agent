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

package net.ccbluex.agent.skills.eat

import net.ccbluex.agent.navigation.isPlainFood
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.world.item.ItemStack

/**
 * Eats, drinks and heals: food by hunger, golden apples, healing potions and soup.
 *
 * @agent claude-opus-5-5
 */
public interface Eat {

    /**
     * Whether it eats or heals now or is about to, so nothing else should take the hand or use an item.
     */
    public val wantsToEat: Boolean

    public fun tick(agent: Agent)

    public companion object {

        /**
         * Hunger at or below which the built-in ability eats; a player with nothing to eat goes for food then.
         */
        public const val HUNGRY: Int = net.ccbluex.agent.navigation.HUNGRY

        @JvmStatic
        public fun builtIn(): Eat = Eating()

        /**
         * Whether [stack] is food to eat when hungry: no golden apple, which is kept for healing, and nothing that
         * hurts, poisons or teleports.
         */
        @JvmStatic
        public fun isFood(stack: ItemStack): Boolean = isPlainFood(stack)
    }
}
