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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.world.entity.Sheep
import net.ccbluex.agent.world.entity.Rabbit
import net.ccbluex.agent.world.entity.Pig
import net.ccbluex.agent.world.entity.Animal
import net.ccbluex.agent.world.entity.AbstractCow
import net.ccbluex.agent.ExperimentalAgentApi

/**
 * What an animal is hunted for.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public enum class Prey {

    /**
     * Meat worth eating: cows, pigs, sheep and rabbits. Raw chicken can poison, so chickens are left alone.
     */
    FOOD,

    /**
     * Sheep with their wool on.
     */
    WOOL,
    ;

    internal fun matches(animal: Animal): Boolean = when (this) {
        FOOD -> animal is AbstractCow || animal is Pig || animal is Sheep || animal is Rabbit
        WOOL -> animal is Sheep && !animal.isSheared
    }
}
