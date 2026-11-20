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
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.simulateTick
import net.ccbluex.agent.navigation.withKeys
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.world.Input
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
private const val JOIN_POINTS = 8
private const val JOIN_DISTANCE = 1.0

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
 * A step one block up over a gap: its floor is placed beside the one stood on and a block on top of that, as a
 * one-stack does.
 *
 * @agent claude-opus-5-5
 */
internal val Step.isRisingBridge: Boolean
    get() = move == Move.ASCEND && breaks.isEmpty() && opens.isEmpty() && jump == null && places.size == 2 &&
        places[0] == to.below(2) && places[1] == to.below()

/**
 * How far [position] stands past the far edge of the floor this step starts on, along the step; below 0 short of it.
 *
 * @agent claude-opus-5-5
 */
internal fun Step.pastEdge(position: Vec3): Double {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val edge = Vec3.atBottomCenterOf(from).add(dx * 0.5, 0.0, dz * 0.5)
    return (position.x - edge.x) * dx + (position.z - edge.z) * dz
}

/**
 * The keys bringing [position] back over the floor this step starts on once it leans past the far edge with
 * [velocity] still outwards, else none: the client stands on a block placed past that edge at once, and while the
 * server has yet to keep it, the player would fall with it. Stopped with the box still over the floor, the player
 * stays there without keys, rather than stepping back and having to start again.
 *
 * @agent claude-opus-5-5
 */
internal fun Step.holdBack(position: Vec3, velocity: Vec3, frameYRot: Float): Input {
    val past = pastEdge(position)
    if (past <= 0.0 || outward(velocity) <= 0.0 && past < 0.3) {
        return Input.EMPTY
    }
    return getDirectionalInputTowards(Vec3.atLowerCornerOf(from.subtract(to)), frameYRot)
}

/**
 * Whether a floor past the far edge of this step's floor can be clicked next tick: pressing [keys] this tick, then
 * braking with [holdBack], sneaking if [sneaks], keeps the box of [body] over the floor it stands on until it stops,
 * so it is not carried onto the new block before the server keeps it. Pressing on at an edge a sneak holds the player
 * at keeps a speed that carries it on once the block is there, so a sneak there lets go of the movement keys until
 * the speed died down.
 *
 * @agent claude-opus-5-5
 */
internal fun Step.canClickFloor(body: Body, keys: MovementKeys, frameYRot: Float, sneaks: Boolean): Boolean {
    if (staysOverFloor(body, keys.toInput(sprint = false), frameYRot, sneaks)) {
        return true
    }
    if (!keys.sneak) {
        return false
    }
    keys.directionalInput = Input.EMPTY
    return staysOverFloor(body, keys.toInput(sprint = false), frameYRot, sneaks)
}

private fun Step.staysOverFloor(body: Body, input: Input, frameYRot: Float, sneaks: Boolean): Boolean {
    val simulated = body.simulateTick(input)
    repeat(4) {
        // A box 0.6 wide stands on the floor while less than 0.3 past its edge.
        if (pastEdge(simulated.player.position()) >= 0.3) {
            return false
        }
        if (outward(simulated.player.deltaMovement) <= 0.0) {
            return true
        }
        val brake = holdBack(simulated.player.position(), simulated.player.deltaMovement, frameYRot)
            .withKeys(jump = false, sneak = sneaks, sprint = false)
        simulated.input = SimulatedInput(brake, ignoresEdgeBackOff = true)
        simulated.tick()
    }
    return pastEdge(simulated.player.position()) < 0.3
}

private fun Step.outward(velocity: Vec3) = velocity.x * (to.x - from.x) + velocity.z * (to.z - from.z)

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
 * The point among the first ones closest to [position], which the player moved to while the route was searched; null
 * when none is near. A point past a step that places or breaks blocks is never joined: the player near it got there
 * another way, and those blocks are not done.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L481-L575)
 *
 * @agent claude-opus-5-5
 */
internal fun Route.joinIndex(position: Vec3): Int? {
    var best: Int? = null
    var bestDistance = Double.MAX_VALUE
    for (index in 0 until min(points.size, JOIN_POINTS)) {
        if (index > 0 && steps[index - 1].builds) {
            break
        }
        val point = points[index]
        val horizontal = position.horizontalDistanceTo(point)
        val near = horizontal <= JOIN_DISTANCE && abs(position.y - point.y) <= JOIN_DISTANCE
        if (near && horizontal < bestDistance) {
            best = index
            bestDistance = horizontal
        }
    }
    return best
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
