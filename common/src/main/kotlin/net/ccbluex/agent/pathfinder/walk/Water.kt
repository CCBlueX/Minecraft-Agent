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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.terrain.hasRoom
import net.minecraft.world.entity.Avatar.SWIMMING_BB_HEIGHT
import net.minecraft.world.entity.Entity.TOTAL_AIR_SUPPLY

/**
 * Where a swimmer fits in water: upright, or submerged in the swimming pose, which needs sprinting.
 *
 * From LiquidBounce: [Water.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Water.kt#L28-L42)
 *
 * @agent claude-opus-5-5
 */
internal class Water(private val terrain: Terrain, private val canSprint: Boolean) {

    fun fits(x: Int, y: Int, z: Int) = terrain.isWater(x, y, z) &&
        (!isLow(x, y, z) || canSprint && isSubmerged(x, y, z) && terrain.hasRoom(x, y, z, SWIMMING_HEIGHT))

    fun isLow(x: Int, y: Int, z: Int) = !terrain.hasRoom(x, y, z)

    fun isSubmerged(x: Int, y: Int, z: Int) = terrain.isWater(x, y + 1, z) || !terrain.isPassable(x, y + 1, z)

    companion object {
        private const val SWIMMING_HEIGHT = SWIMMING_BB_HEIGHT.toDouble()
        const val MAX_AIR = TOTAL_AIR_SUPPLY
        private const val AIR_MARGIN = 2

        fun movesUnderwater(air: Int) = (air / AIR_MARGIN / Costs.SWIM).toInt()
    }

}
