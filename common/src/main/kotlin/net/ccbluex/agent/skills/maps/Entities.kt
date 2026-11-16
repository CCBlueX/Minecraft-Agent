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

package net.ccbluex.agent.skills.maps

import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Vec3

/**
 * The entities in the box [range] blocks around [pos], as a client lists those it renders.
 *
 * @agent claude-sonnet-5-5
 */
internal fun Level.entitiesNear(pos: Vec3, range: Double): List<Entity> =
    getEntities(null as Entity?, AABB(pos, pos).inflate(range)) { true }
