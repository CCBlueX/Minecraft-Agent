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

package net.ccbluex.agent.pathfinder.teleport

import net.ccbluex.agent.pathfinder.route.Failure
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Search
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3

/**
 * A teleport route of a single step from [start] to the closest of [goals], the blocks the feet end in. It fails
 * when that move is blocked or longer than the server takes in one packet.
 *
 * @agent claude-sonnet-5-5
 */
public class DirectSearch(
    private val terrain: Terrain,
    private val start: Vec3,
    private val goals: List<BlockPos>,
    private val limits: Limits,
) : Search<Route> {

    override fun advance(): Outcome<Route> {
        val standable = goals.mapNotNull(terrain::feet)
        val goal = standable.minByOrNull { it.distanceToSqr(start) } ?: return Outcome.Failed(Failure.INVALID_DESTINATION)
        val distance = start.distanceTo(goal)
        return when {
            distance < 0.25 -> Outcome.AlreadyThere
            distance > limits.maxLength || distance > Route.SAFE_DISTANCE -> Outcome.Failed(Failure.TOO_FAR)
            else -> Route.direct(terrain, start, goal)?.let { Outcome.Found(it) } ?: Outcome.Failed(Failure.NO_PATH)
        }
    }
}
