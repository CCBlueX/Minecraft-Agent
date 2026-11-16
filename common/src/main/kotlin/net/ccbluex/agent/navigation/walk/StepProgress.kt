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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.ProgressWatch
import net.ccbluex.agent.pathfinder.walk.Leap

/**
 * Least distance a tick has to gain on a step to count as progress.
 */
internal const val PROGRESS = 0.1
private const val STUCK_TICKS = 40

/**
 * Without progress, the way on is predicted every so many ticks.
 */
private const val PREDICT_INTERVAL = 5
private const val ACTION_TICKS = 60

/**
 * How far along the bridge line the walk aims ahead of the player.
 */
private const val PURSUIT_AHEAD = 2.0

/**
 * How a step is walked in a tick.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L741-L756)
 *
 * @agent claude-fable-5-2
 */
internal class Movement(
    val target: Vec3? = null,
    val sprint: Boolean = false,

    /**
     * Swims under water towards [target], which also lies higher or lower.
     */
    val dive: Boolean = false,

    /**
     * A plain walk other features may add to, such as an extra jump.
     */
    val isPlainWalk: Boolean = false,

    /**
     * Keeps to the edge of the ground, as sneaking does, without its slowdown.
     */
    val safeWalk: Boolean = false,
) {
    companion object {
        val NONE = Movement()
    }
}

/**
 * The progress on one step; a new one with every step.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L760-L777)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal class StepProgress {
    val leap = Leap()
    var jumpTicks = 0
    val watch = ProgressWatch(PROGRESS, STUCK_TICKS)
    var acting: BlockPos? = null
    private var actingTicks = 0

    val isDueToPredict: Boolean
        get() = watch.idleTicks > 0 && watch.idleTicks % PREDICT_INTERVAL == 0

    fun actsTooLong(pos: BlockPos): Boolean {
        if (pos != acting) {
            acting = pos
            actingTicks = 0
            return false
        }
        return ++actingTicks >= ACTION_TICKS
    }
}

/**
 * A straight bridge crossing; the walk aims [PURSUIT_AHEAD] along it, which pulls a drift back gently.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L344-L356)
 *
 * @agent claude-opus-5-5
 */
internal class BridgeLine(val from: Vec3, val to: Vec3) {
    private val length = from.horizontalDistanceTo(to)
    private val dx = if (length > 0.0) (to.x - from.x) / length else 0.0
    private val dz = if (length > 0.0) (to.z - from.z) / length else 0.0

    val heading: Float
        get() = to.subtract(from).yRot

    fun aimFrom(position: Vec3): Vec3 {
        val along = (position.x - from.x) * dx + (position.z - from.z) * dz
        val ahead = (along + PURSUIT_AHEAD).coerceIn(0.0, length)
        return Vec3(from.x + dx * ahead, to.y, from.z + dz * ahead)
    }
}
