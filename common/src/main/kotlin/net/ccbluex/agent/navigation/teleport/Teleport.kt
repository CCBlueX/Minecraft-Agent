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

package net.ccbluex.agent.navigation.teleport

import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.pathfinder.teleport.Route
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * The steps of a route on their way to the server, one batch per tick.
 *
 * From LiquidBounce: [Teleport.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/teleport/Teleport.kt#L32-L130)
 *
 * @agent claude-opus-5-5
 */
public class Teleport internal constructor(
    public val owner: Owner,
    internal val route: Route,
    private val batches: IntArray,
    public val finish: Finish,
) : Progress {

    public enum class State {
        ACTIVE,
        DONE,

        /**
         * The server set the player back.
         */
        REJECTED,

        /**
         * A solid block appeared ahead.
         */
        INVALID,
        CANCELLED,
    }

    public enum class Finish {
        /**
         * The client moves to the destination as well.
         */
        LAND,

        /**
         * The server stays at the destination until [Teleports.undo] brings it back to the client.
         */
        HOLD,

        /**
         * The route ends where the client is.
         */
        RESYNC,
    }

    public var state: State = State.ACTIVE
        private set

    internal var isBlocked = false
        private set

    private val steps = route.steps
    private var sent = 0
    private var batch = 0
    private var previousPosition = route.start

    private val position: Vec3
        get() = if (sent == 0) route.start else steps[sent - 1].position

    public val ticks: Int
        get() = batches.size

    override val isMoving: Boolean
        get() = state == State.ACTIVE

    override val ahead: List<Vec3>
        get() = steps.subList(sent, steps.size).map(Route.Step::position)

    internal val isSent: Boolean
        get() = batch >= batches.size

    override fun head(partialTicks: Float): Vec3 = previousPosition.lerp(position, partialTicks.toDouble())

    internal fun nextBatch(): List<Route.Step> {
        previousPosition = position
        val from = sent
        sent += batches[batch++]
        return steps.subList(from, sent)
    }

    internal fun settle() {
        previousPosition = position
    }

    internal fun end(state: State) {
        this.state = state
    }

    internal fun onBlockChange(level: Level, pos: BlockPos, state: BlockState) {
        if (isMoving && !state.getCollisionShape(level, pos).isEmpty && isAhead(AABB(pos))) {
            isBlocked = true
        }
    }

    private fun isAhead(block: AABB): Boolean {
        var previous = position
        for (index in sent until steps.size) {
            val next = steps[index].position
            if (sweptBox(previous, next).intersects(block)) {
                return true
            }
            previous = next
        }
        return false
    }

    private fun sweptBox(from: Vec3, to: Vec3): AABB {
        val half = Terrain.PLAYER_WIDTH / 2.0
        val height = Terrain.PLAYER_HEIGHT
        return AABB(
            minOf(from.x, to.x) - half, minOf(from.y, to.y), minOf(from.z, to.z) - half,
            maxOf(from.x, to.x) + half, maxOf(from.y, to.y) + height, maxOf(from.z, to.z) + half,
        )
    }

}
