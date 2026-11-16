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

package net.ccbluex.agent.pathfinder.route

import net.ccbluex.agent.math.Vec3

/**
 * A search that is advanced in slices until it has a result.
 *
 * From LiquidBounce: [RouteSearch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/RouteSearch.kt#L51-L53)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
public interface Search<out T> {

    public fun advance(): Outcome<T>

    /**
     * Feet positions from the start to the explored spot closest to the goal; read between slices.
     */
    public fun preview(): List<Vec3>? = null

}
