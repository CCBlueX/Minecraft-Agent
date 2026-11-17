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

import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import kotlin.math.abs
import kotlin.math.sign

/**
 * The steps from [start] until [end] of a path that place their own floor over a gap, level or one block up at a
 * time: what a [net.ccbluex.agent.navigation.Bridging] method is picked for.
 *
 * @agent claude-opus-5-5
 */
internal class Bridge(steps: List<Step>, val start: Int, val end: Int) {

    val length: Int
        get() = end - start

    /**
     * Where the last step of the bridge leads, which tells the same bridge in a path searched again.
     */
    val to: BlockPos = steps[end - 1].to

    val rises: Boolean = (start until end).any { steps[it].isRisingBridge }

    /**
     * Every step goes the same way along an axis, rather than as a staircase across.
     */
    val isStraight: Boolean = (start until end).all { direction(steps[it]) == direction(steps[start]) }

    /**
     * The yaw from where the bridge begins to where it ends.
     */
    val yRot: Float = Vec3.atCenterOf(to).subtract(Vec3.atCenterOf(steps[start].from)).yRot

    /**
     * The way the path turns right after the bridge: -1 to the left of [yRot], 1 to the right, 0 without a turn.
     */
    val turnAfter: Int = steps.getOrNull(end)?.let { next ->
        val turn = Mth.wrapDegrees(Vec3.atLowerCornerOf(direction(next)).yRot - yRot)
        if (abs(turn) < STRAIGHT_ON) 0 else turn.sign.toInt()
    } ?: 0

    private fun direction(step: Step) = BlockPos(step.to.x - step.from.x, 0, step.to.z - step.from.z)

    companion object {
        /**
         * Turns smaller than this in degrees count as going straight on.
         */
        private const val STRAIGHT_ON = 30f

        /**
         * The bridge [index] of [steps] is part of, null if that step is none.
         */
        fun at(steps: List<Step>, index: Int): Bridge? {
            if (!steps[index].crossesGap) {
                return null
            }
            var start = index
            while (start > 0 && steps[start - 1].crossesGap) {
                start--
            }
            var end = index + 1
            while (end < steps.size && steps[end].crossesGap) {
                end++
            }
            return Bridge(steps, start, end)
        }

        private val Step.crossesGap: Boolean
            get() = isBridge || isRisingBridge
    }
}
