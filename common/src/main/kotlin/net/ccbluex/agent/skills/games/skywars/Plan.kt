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

package net.ccbluex.agent.skills.games.skywars

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * What a [SkyWars] agent can work on between fights.
 *
 * @agent claude-opus-5-5
 */
internal enum class Activity(val label: String) {
    /**
     * The own island's chests.
     */
    LOOT("Looting its island"),

    /**
     * The middle's chests, or waiting there for the refill.
     */
    MIDDLE("Going for the middle"),

    /**
     * The chests of an island nobody is on.
     */
    RAID("Raiding an empty island"),
    HUNT("Hunting"),

    /**
     * Digging the own island for blocks to bridge with.
     */
    GATHER("Gathering blocks"),
    PICKUP("Picking up loot"),

    /**
     * Behind a wall on the own island, waiting for whoever comes across.
     */
    HOLD("Holding its ground"),
    RETREAT("Backing off to heal"),
    REGROUP("Regrouping"),

    /**
     * In to the middle while the border closes in.
     */
    DOOM("Holding the middle"),
    ;

    val isChests: Boolean
        get() = this == LOOT || this == MIDDLE || this == RAID
}

/**
 * One option an agent scored: an [activity] with what it is about, an entity by [targetId], a [point] or [chests].
 * [pearl] means the target is only reached by an ender pearl, [thirdParty] that someone else fights it already.
 *
 * @agent claude-opus-5-5
 */
internal data class Plan(
    val activity: Activity,
    val score: Double,
    val targetId: Int? = null,
    val point: Vec3? = null,
    val chests: List<BlockPos> = emptyList(),
    val pearl: Boolean = false,
    val thirdParty: Boolean = false,
    val reason: String = "",
) {

    /**
     * Whether [other] is about the same thing, so sticking with it earns the commit bonus.
     */
    fun same(other: Plan?): Boolean = other != null && activity == other.activity && targetId == other.targetId &&
        (point == null || other.point == null || BlockPos.containing(point) == BlockPos.containing(other.point))
}
