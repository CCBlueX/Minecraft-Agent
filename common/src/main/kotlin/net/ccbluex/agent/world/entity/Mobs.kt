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

package net.ccbluex.agent.world.entity

import net.ccbluex.agent.world.item.DyeColor

/**
 * An entity with the game's mob brain: it targets, walks, can be leashed.
 *
 * @agent claude-sonnet-5-5
 */
public interface Mob : LivingEntity {
    public val isLeashed: Boolean
}

/**
 * A mob that attacks players, as the game's marker interface of that name tells.
 *
 * @agent claude-sonnet-5-5
 */
public interface Enemy : Mob

/**
 * @agent claude-sonnet-5-5
 */
public interface Monster : Enemy

/**
 * @agent claude-sonnet-5-5
 */
public interface Creeper : Monster

/**
 * @agent claude-sonnet-5-5
 */
public interface Enderman : Monster {
    /**
     * Whether it is angry, as when a player looks at it.
     */
    public val isCreepy: Boolean
}

/**
 * @agent claude-sonnet-5-5
 */
public interface Spider : Monster

/**
 * Zombies, husks, drowned and zombie villagers.
 *
 * @agent claude-sonnet-5-5
 */
public interface Zombie : Monster

/**
 * Skeletons, strays, bogged and wither skeletons.
 *
 * @agent claude-sonnet-5-5
 */
public interface AbstractSkeleton : Monster

/**
 * @agent claude-sonnet-5-5
 */
public interface Animal : Mob {
    public val isBaby: Boolean
}

/**
 * @agent claude-sonnet-5-5
 */
public interface Sheep : Animal {
    public val isSheared: Boolean
    public val color: DyeColor
}

/**
 * @agent claude-sonnet-5-5
 */
public interface Pig : Animal

/**
 * Cows and mooshrooms.
 *
 * @agent claude-sonnet-5-5
 */
public interface AbstractCow : Animal

/**
 * @agent claude-sonnet-5-5
 */
public interface Rabbit : Animal
