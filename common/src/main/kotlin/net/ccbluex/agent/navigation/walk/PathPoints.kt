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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.distanceToSegment
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.Step
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How many steps ahead shortcuts and looks reach.
 */
internal const val SHORTCUT_STEPS = 8

private const val LOOK_STEPS = 4
private const val MIN_SEGMENT = 0.3

/**
 * A plain step counts as passed this close beside its way and level with it.
 */
private const val PASS_BESIDE = 0.6
private const val PASS_VERTICAL = 1.25
private const val OFF_PATH = 1.5

/**
 * A block down from the path counts as off it.
 */
private const val FALL_BELOW = 0.9

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L721-L722)
 *
 * @agent claude-fable-5-2
 */
internal val Step.builds: Boolean
    get() = breaks.isNotEmpty() || places.isNotEmpty()

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L724-L725)
 *
 * @agent claude-fable-5-2
 */
internal val Step.isLevelWalk: Boolean
    get() = (move == Move.TRAVERSE || move == Move.DIAGONAL) && !shift && !edge

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L727-L728)
 *
 * @agent claude-fable-5-2
 */
internal val Step.isPlain: Boolean
    get() = isLevelWalk && jump == null && !builds && opens.isEmpty() && !isUnderWater

/**
 * The index after the bridge steps in a row from [start].
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L335-L341)
 *
 * @agent claude-opus-5-5
 */
internal fun runEnd(steps: List<Step>, start: Int): Int {
    var end = start
    while (end < steps.size && steps[end].isBridge) {
        end++
    }
    return end
}

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L635-L637)
 *
 * @agent claude-fable-5-2
 */
internal fun continuesStraight(step: Step, next: Step) =
    next.to.x - next.from.x == step.to.x - step.from.x && next.to.z - next.from.z == step.to.z - step.from.z &&
        (step.to.x != step.from.x || step.to.z != step.from.z)

/**
 * Whether [position] lies beyond the end of [step], seen along its way, and close beside and level with it.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L640-L651)
 *
 * @agent claude-opus-5-5
 */
internal fun hasPassed(step: Step, position: Vec3): Boolean {
    val from = Vec3.atBottomCenterOf(step.from)
    val dx = step.feet.x - from.x
    val dz = step.feet.z - from.z
    val length = sqrt(dx * dx + dz * dz)
    if (length == 0.0 || abs(position.y - step.feet.y) >= PASS_VERTICAL) {
        return false
    }
    val along = ((position.x - from.x) * dx + (position.z - from.z) * dz) / length
    val beside = abs((position.x - from.x) * dz - (position.z - from.z) * dx) / length
    return along >= length && beside <= PASS_BESIDE
}

/**
 * Whether [position] left the path at step [index]: below it, away from the run-up of its jump, and away from the
 * plain walks ahead whose corners shortcuts cut.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L143-L173)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun Route.isOffPath(index: Int, position: Vec3): Boolean {
    val step = steps.getOrNull(index) ?: return false
    val from = points[index]
    if (position.y < min(from.y, step.feet.y) - FALL_BELOW) {
        return true
    }

    // A jump may first walk back to its run-up.
    val runUp = step.jump?.runUp
    if (runUp != null && position.distanceToSegment(runUp, from) <= OFF_PATH) {
        return false
    }

    for (i in index until min(points.size - 1, index + SHORTCUT_STEPS)) {
        if (position.distanceToSegment(points[i], points[i + 1]) <= OFF_PATH) {
            return false
        }
        if (!steps[i].isPlain) {
            break
        }
    }
    return true
}

/**
 * The first point of the path at least [distance] from [position], horizontally, within a few steps from [target]
 * if that is a point ahead of step [index], else from the step walked; null when the path stays closer.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L548-L558)
 *
 * @agent claude-opus-5-5
 */
internal fun Route.lookAhead(index: Int, target: Vec3, position: Vec3, distance: Double): Vec3? {
    val end = min(points.size, index + 1 + SHORTCUT_STEPS)
    val from = (index + 1 until end).firstOrNull { points[it] == target } ?: (index + 1)
    for (i in from until min(points.size, from + LOOK_STEPS)) {
        if (points[i].horizontalDistanceToSqr(position) >= distance * distance) {
            return points[i]
        }
    }
    return null
}

/**
 * The point [distance] blocks ahead along the rest of the path from [position] at step [index], measured
 * horizontally and up or down ladders and vines; the end if the path is shorter.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L564-L580)
 *
 * @agent claude-opus-5-5
 */
internal fun Route.pointAhead(index: Int, position: Vec3, distance: Double): Vec3 {
    var from = position
    var left = distance
    for (i in index + 1 until points.size) {
        val to = points[i]
        val climbs = steps[i - 1].move.let { it == Move.CLIMB_UP || it == Move.CLIMB_DOWN }
        val length = if (climbs) from.distanceTo(to) else from.horizontalDistanceTo(to)
        if (length >= left) {
            return from.lerp(to, left / length)
        }
        left -= length
        from = to
    }
    return points.last()
}

/**
 * The sharpest turn of the path within [distance] blocks ahead of [position] at step [index], in degrees.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L681-L704)
 *
 * @agent claude-opus-5-5
 */
internal fun Route.turnAhead(index: Int, position: Vec3, distance: Double): Float {
    var from = position
    var left = distance
    var heading = Float.NaN
    var sharpest = 0f
    for (i in index + 1 until points.size) {
        val to = points[i]
        val length = from.horizontalDistanceTo(to)
        if (length > MIN_SEGMENT) {
            val yRot = to.subtract(from).yRot
            if (!heading.isNaN()) {
                sharpest = max(sharpest, abs(Mth.wrapDegrees(yRot - heading)))
            }
            heading = yRot
        }
        left -= length
        if (left <= 0.0) {
            break
        }
        from = to
    }
    return sharpest
}
