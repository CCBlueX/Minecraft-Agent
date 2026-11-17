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

package net.ccbluex.agent.skills.farm

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.AABB

/**
 * A farm an agent tends: the [area] its crops, soil and drops lie in, and what it may do there besides harvesting.
 *
 * @property canWalk walks to what is out of reach; otherwise it works only where it stands and picks up nothing.
 * @property canPlant sows what it harvested again, and empty farmland and soul sand with seeds it carries.
 * @property canWalkToPlant walks to empty soil to sow it; otherwise it sows only soil within reach.
 * @property canTill hoes grass and dirt with water near into farmland.
 * @property canHydrate pours a water bucket into the ground in the middle of [area] when no water is near it.
 * @property shouldUseFortune harvests holding the item with the most Fortune, for more drops.
 * @property pickUp the drops it picks up within [pickUpRadius]; null for none.
 * @property chest where its harvest goes once the inventory is full; null keeps it.
 * @property delayTicks how long it waits after each click.
 * @property boneMealDelayTicks how long it waits between two uses of bone meal.
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public data class Farm @JvmOverloads constructor(
    public val area: AABB,
    public val canWalk: Boolean = true,
    public val canPlant: Boolean = true,
    public val canWalkToPlant: Boolean = true,
    public val canUseBoneMeal: Boolean = false,
    public val canTill: Boolean = false,
    public val canHydrate: Boolean = false,
    public val shouldUseFortune: Boolean = true,
    public val pickUp: ItemMatch? = ItemMatch { true },
    public val pickUpRadius: Double = 16.0,
    public val chest: BlockPos? = null,
    public val delayTicks: IntRange = 2..4,
    public val boneMealDelayTicks: IntRange = 0..0,
)
