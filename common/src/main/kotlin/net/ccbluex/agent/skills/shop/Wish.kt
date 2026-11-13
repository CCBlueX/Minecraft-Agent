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

package net.ccbluex.agent.skills.shop

import net.ccbluex.agent.skills.ItemMatch

/**
 * Something the agent wants from a shop: [amount] items [owned] accepts, bought as the cheapest offer [buy] accepts,
 * or the team upgrade [upgrade].
 *
 * @property urgency how much getting it is worth next to the rest of what the agent could do
 * @agent claude-opus-5-5
 */
public class Wish @JvmOverloads constructor(
    public val name: String,
    public val owned: ItemMatch,
    public val buy: ItemMatch,
    public val amount: Int = 1,
    public val urgency: Double = 0.0,
    public val upgrade: String? = null,
) {

    override fun toString(): String = name

    public companion object {

        /**
         * The next tier of the team upgrade [name], such as `upgrade-forge`.
         */
        @JvmStatic
        public fun upgrade(name: String): Wish = Wish(name, NOTHING, NOTHING, upgrade = name)

        private val NOTHING = ItemMatch { false }
    }
}
