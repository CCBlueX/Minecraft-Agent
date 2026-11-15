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

package net.ccbluex.agent.bukkit

import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.navigation.walk.PlacedBlock
import net.ccbluex.agent.pathfinder.walk.Route
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Passes what a navigator reports on to every listener of its bot.
 *
 * @agent claude-opus-5-5
 */
internal class NavigationListeners : Listener {

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun add(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun remove(listener: Listener) {
        listeners.remove(listener)
    }

    override fun routeFound(path: Route) = listeners.forEach { it.routeFound(path) }

    override fun stepReached(index: Int, pos: BlockPos) = listeners.forEach { it.stepReached(index, pos) }

    override fun stuck(pos: BlockPos) = listeners.forEach { it.stuck(pos) }

    override fun arrived(goal: Vec3) = listeners.forEach { it.arrived(goal) }

    override fun failed(goal: Vec3) = listeners.forEach { it.failed(goal) }

    override fun blockPlaced(block: PlacedBlock) = listeners.forEach { it.blockPlaced(block) }

    override fun blockKept(block: PlacedBlock) = listeners.forEach { it.blockKept(block) }

    override fun blockRejected(block: PlacedBlock) = listeners.forEach { it.blockRejected(block) }

    override fun routeSearching(start: Vec3, goal: Vec3, radius: Double, preview: List<Vec3>?) =
        listeners.forEach { it.routeSearching(start, goal, radius, preview) }

    override fun routeFollowing(points: List<Vec3>, progress: Progress) =
        listeners.forEach { it.routeFollowing(points, progress) }

    override fun blocksAhead(breaks: Collection<BlockPos>, places: Collection<BlockPos>) =
        listeners.forEach { it.blocksAhead(breaks, places) }

    override fun routeDone() = listeners.forEach { it.routeDone() }

    override fun routeInvalid() = listeners.forEach { it.routeInvalid() }

    override fun routeCleared() = listeners.forEach { it.routeCleared() }

    override fun touched(pos: BlockPos, direction: Direction) = listeners.forEach { it.touched(pos, direction) }
}
