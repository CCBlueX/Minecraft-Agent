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

import net.ccbluex.agent.world.entity.LivingEntity
import java.util.UUID

/**
 * The best weapon each player was seen holding this round, which still counts while it holds something else.
 *
 * @agent claude-opus-5-5
 */
internal class WeaponMemory {

    private val seen = HashMap<UUID, Double>()

    fun remember(entity: LivingEntity) {
        val weapon = GearScore.weaponScore(entity.mainHandItem)
        if (weapon > 0.0) seen.merge(entity.uuid, weapon, ::maxOf)
    }

    /**
     * The best weapon score seen for [uuid]; 0 when none was.
     */
    fun best(uuid: UUID): Double = seen[uuid] ?: 0.0

    fun clear() {
        seen.clear()
    }
}
