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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.component1
import net.ccbluex.agent.math.component2
import net.ccbluex.agent.math.component3
import net.ccbluex.agent.math.ifNaN
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.terrain.isBowl
import net.ccbluex.agent.pathfinder.terrain.isOpen
import net.ccbluex.agent.pathfinder.walk.Nodes.Companion.DX
import net.ccbluex.agent.pathfinder.walk.Nodes.Companion.DZ
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * The moves out of a feet block. They only look at [terrain], so a node always yields the same moves. A node reached
 * by breaking or placing is not standable in [terrain]; it counts as standing on its floor. Not thread-safe.
 *
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L61-L473)
 *
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
internal class Moves(private val terrain: Terrain, private val abilities: WalkAbilities) {

    val nodes = Nodes(terrain, abilities)

    private val walk = if (abilities.canSprint) Costs.SPRINT else Costs.WALK
    private val changes = Changes(terrain, nodes, abilities)
    private val parkour = Parkour(terrain, abilities) { x, y, z, cost, jump ->
        emit(x, y, z, Move.PARKOUR, cost, jump)
    }

    private val opens = ArrayList<BlockPos>(1)

    // The node the moves start from.
    private var from = BlockPos.ZERO
    private var fromHeight = 0.0
    private var fromStands = true
    private var fromFloorFactor = 1.0
    private var canJump = true
    private var isJumpWorth = EVERY_JUMP
    private var sink = MoveSink { _, _, _, _, _, _, _ -> }

    /**
     * The cheapest move from [from] to [to], or null when there is none.
     */
    fun step(from: BlockPos, to: BlockPos): Step? {
        var best: Step? = null
        val sink = MoveSink { target, move, cost, breaks, places, opens, jump ->
            val current = best
            if (target == to && (current == null || cost < current.cost)) {
                val feet = nodes.feet(to.x, to.y, to.z)
                best = Step(
                    from, to, move, feet, breaks.toList(), places.toList(), opens.toList(), cost,
                    shift = nodes.crouches(from.x, from.y, from.z) || nodes.crouches(to.x, to.y, to.z),
                    edge = isAtEdge(to, move), isUnderWater = nodes.isUnderwater(to), jump = jump,
                )
            }
        }
        forEach(from, sink) { x, y, z -> x == to.x && y == to.y && z == to.z }
        return best
    }

    /**
     * Passes every move out of [node] to [sink]. Jumps, each simulated, are only worked out to spots [isJumpWorth].
     */
    fun forEach(node: BlockPos, sink: MoveSink, isJumpWorth: JumpFilter = EVERY_JUMP) {
        val (x, y, z) = node
        val water = nodes.swims(x, y, z)
        val climb = nodes.climbs(x, y, z)
        from = node
        changes.from = node
        fromHeight = nodes.height(x, y, z)
        fromStands = nodes.stands(x, y, z)
        val standing = fromStands || !water && !climb
        this.sink = sink
        this.isJumpWorth = isJumpWorth
        if (nodes.crouches(x, y, z)) {
            crouchMoves()
            return
        }

        val floor = nodes.floorBlock(x, z, fromHeight)
        fromFloorFactor = if (water || climb) 1.0 else Costs.floorFactor(floor)
        // Honey does not allow a full jump.
        canJump = water || climb || floor.jumpFactor >= 1f

        for (i in DX.indices) {
            val tx = x + DX[i]
            val tz = z + DZ[i]
            if (standing || climb) {
                traverse(tx, tz, standing)
                ascend(tx, tz)
            }
            if (standing) {
                fall(tx, tz)
                diagonal(x + DIAGONAL_X[i], z + DIAGONAL_Z[i])
            }

            swimInto(tx, y, tz)
            if (water) {
                swimOut(tx, tz)
            }
        }

        parkour()
        swimInto(x, y + 1, z)
        swimInto(x, y - 1, z)
        climb()
        if (standing) {
            pillar()
            downward()
        }
    }

    private fun traverse(tx: Int, tz: Int, standing: Boolean) {
        val y = from.y
        changes.clear()

        val wet = isWet(tx, tz)
        val top = terrain.standHeight(tx, y, tz)
        when {
            !top.isNaN() -> {
                val jump = jumpCost(top - fromHeight)
                val fits = jump == 0.0 || !jump.isNaN() && hasJumpRoom(tx, tz)
                if (fits && canCross(tx, tz, max(fromHeight, top))) {
                    emit(tx, y, tz, Move.TRAVERSE, walkCost(tx, tz, top, wet) + jump)
                }
            }

            nodes.climbs(tx, y, tz) -> emit(tx, y, tz, Move.TRAVERSE, walkCost(tx, tz, y.toDouble(), wet))
            nodes.crouches(tx, y, tz) -> {
                if (terrain.crouchHeight(tx, y, tz) - fromHeight <= Terrain.STEP_HEIGHT) {
                    emit(tx, y, tz, Move.TRAVERSE, Costs.SNEAK)
                }
            }

            else -> traverseChanging(tx, tz, standing, wet)
        }
    }

    /**
     * A traverse that breaks the way free or places the floor.
     */
    private fun traverseChanging(tx: Int, tz: Int, standing: Boolean, wet: Boolean) {
        val y = from.y
        if (!changes.passOrBreak(tx, y + 1, tz) || !changes.passOrBreak(tx, y, tz)) {
            return
        }

        // The walker stands on the floor of the node even if that was placed on the way.
        val onFloor = standing && fromHeight <= y + EPSILON
        val floor = floorOrPlace(tx, y, tz) { changes.canPlaceAgainst(tx, y - 1, tz, onFloor) }
        val jump = jumpCost(floor - fromHeight)
        val breaks = changes.breaks
        val places = changes.places
        if (breaks.isEmpty() && places.isEmpty() || jump.isNaN() || jump > 0.0 && !hasJumpRoom(tx, tz)) {
            return
        }
        // Over a placed floor as over any other: the rim of a composter or cauldron stood in walls the way out
        if (breaks.isEmpty() && !canCross(tx, tz, max(fromHeight, floor))) {
            return
        }
        val placeCost = if (breaks.isEmpty()) abilities.bridgeTicks else Costs.PLACE
        val cost = walkCost(tx, tz, floor, wet) + jump + changes.breakCost + places.size * placeCost
        emit(tx, y, tz, Move.TRAVERSE, cost)
    }

    private fun diagonal(tx: Int, tz: Int) {
        val (x, y, z) = from
        changes.clear()

        val top = terrain.standHeight(tx, y, tz)
        if (top.isNaN() || top - fromHeight > Terrain.STEP_HEIGHT) {
            return
        }
        if (!terrain.isOpen(tx, y, z) || !terrain.isOpen(x, y, tz) || !canCross(tx, tz, max(fromHeight, top))) {
            return
        }

        emit(tx, y, tz, Move.DIAGONAL, DIAGONAL * walkCost(tx, tz, top, isWet(tx, tz)))
    }

    private fun ascend(tx: Int, tz: Int) {
        val y = from.y
        val ty = y + 1
        changes.clear()

        if (!changes.passOrBreak(from.x, y + 2, from.z)) {
            return
        }

        val top = terrain.standHeight(tx, ty, tz)
        if (!top.isNaN()) {
            val jump = jumpCost(top - fromHeight)
            if (!jump.isNaN() && canCross(tx, tz, top)) {
                emit(tx, ty, tz, Move.ASCEND, walkCost(tx, tz, top) + jump + changes.breakCost)
            }
            return
        }

        val headBreaks = changes.breaks.size
        if (!changes.passOrBreak(tx, ty + 1, tz) || !changes.passOrBreak(tx, ty, tz)) {
            return
        }

        // Over a gap, the floor below the new one is placed first against the floor stood on, as a one-stack does.
        var stacked = false
        val floor = floorOrPlace(tx, ty, tz) {
            nodes.isSolid(tx, y - 1, tz) || stacks(tx, y - 1, tz, headBreaks).also { stacked = it }
        }
        val jump = jumpCost(floor - fromHeight)
        if (jump.isNaN() || changes.breaks.size == headBreaks && changes.places.isEmpty()) {
            return
        }
        val placeCost = if (stacked) abilities.bridgeTicks else Costs.PLACE
        val cost = walkCost(tx, tz, floor) + jump + changes.breakCost + changes.places.size * placeCost
        emit(tx, ty, tz, Move.ASCEND, cost)
    }

    /**
     * Plans a block at [x] [y] [z] beside the floor stood on, when nothing is broken for the rise.
     *
     * @agent claude-opus-5-5
     */
    private fun stacks(x: Int, y: Int, z: Int, headBreaks: Int): Boolean {
        // The floor stood on may be one placed on the way, as on a staircase of one-stacks.
        val (fx, fy, fz) = from
        if (headBreaks > 0 || changes.breaks.isNotEmpty() || nodes.swims(fx, fy, fz) || nodes.climbs(fx, fy, fz) ||
            fromHeight > y + 1 + EPSILON || !nodes.isPlaceable(x, y, z)
        ) {
            return false
        }
        changes.places += BlockPos(x, y, z)
        return true
    }

    private fun fall(tx: Int, tz: Int) {
        val y = from.y
        if (!nodes.floorTop(tx, y, tz).isNaN() || terrain.isWater(tx, y, tz)) {
            return
        }

        changes.clear()
        if (!changes.passOrBreak(tx, y + 1, tz) || !changes.passOrBreak(tx, y, tz)) {
            return
        }
        if (changes.breaks.isEmpty() && !canCross(tx, tz, fromHeight)) {
            return
        }

        val landing = nodes.landing(tx, y - 1, tz, fromHeight) ?: return
        val blocks = ceil(landing.drop).toInt()
        val bounce = Costs.bounce(nodes.floorBlock(tx, tz, fromHeight - landing.drop), blocks)
        val cost = walk * fromFloorFactor + Costs.fall(blocks) + bounce + landing.damage * Costs.HURT + changes.breakCost
        emit(tx, landing.y, tz, Move.FALL, cost)
    }

    /**
     * Jumps over a gap or dip to spots out of reach of the other moves, each simulated by [Jumps].
     */
    private fun parkour() {
        val (x, y, z) = from
        if (!abilities.canParkour || !fromStands || terrain.isWater(x, y, z)) {
            return
        }

        changes.clear()
        parkour.forEach(from, fromHeight, isJumpWorth)
    }

    private fun pillar() {
        val (x, y, z) = from
        if (!canJump || !terrain.isPassable(x, y, z) || !nodes.isPlaceable(x, y, z)) {
            return
        }

        changes.clear()
        if (!changes.passOrBreak(x, y + 2, z)) {
            return
        }

        changes.places += BlockPos(x, y, z)
        emit(x, y + 1, z, Move.PILLAR, Costs.JUMP + Costs.PLACE + changes.breakCost)
    }

    private fun downward() {
        val (x, y, z) = from
        if (terrain.isPassable(x, y - 1, z)) {
            return
        }

        changes.clear()
        val ticks = changes.breakTicks(x, y - 1, z, floor = true)
        if (ticks.isNaN() || nodes.floorTop(x, y - 1, z).isNaN()) {
            return
        }

        changes.breaks += BlockPos(x, y - 1, z)
        emit(x, y - 1, z, Move.DOWNWARD, ticks + Costs.BREAK_OVERHEAD + Costs.fall(1))
    }

    /**
     * Swims into the water block; the swimming pose, which needs sprinting, only goes on from water.
     */
    private fun swimInto(tx: Int, ty: Int, tz: Int) {
        if (!nodes.swims(tx, ty, tz) || nodes.water.isLow(tx, ty, tz) && !nodes.swims(from.x, from.y, from.z)) {
            return
        }

        changes.clear()
        val sprints = abilities.canSprint && ty == from.y && nodes.water.isSubmerged(tx, ty, tz)
        emit(tx, ty, tz, Move.SWIM, if (sprints) Costs.SPRINT_SWIM else Costs.SWIM)
    }

    private fun swimOut(tx: Int, tz: Int) {
        val y = from.y
        changes.clear()
        if (nodes.stands(tx, y, tz)) {
            emit(tx, y, tz, Move.SWIM, Costs.SWIM)
        } else if (nodes.stands(tx, y + 1, tz) && terrain.isPassable(from.x, y + 2, from.z)) {
            emit(tx, y + 1, tz, Move.SWIM, Costs.SWIM + Costs.JUMP)
        }
    }

    private fun climb() {
        val (x, y, z) = from
        if (!terrain.isClimbable(x, y, z)) {
            return
        }

        changes.clear()
        val canClimbUp = abilities.jumpClimbs || terrain.climbSide(x, y, z) != null
        if (canClimbUp && (nodes.climbs(x, y + 1, z) || nodes.stands(x, y + 1, z))) {
            emit(x, y + 1, z, Move.CLIMB_UP, Costs.CLIMB_UP)
        }
        if (nodes.climbs(x, y - 1, z)) {
            emit(x, y - 1, z, Move.CLIMB_DOWN, Costs.CLIMB_DOWN)
        } else if (!fromStands && terrain.isPassable(x, y - 1, z)) {
            // Off the bottom of a ladder or vine that ends above the ground.
            val landing = nodes.landing(x, y - 1, z, fromHeight) ?: return
            val blocks = ceil(landing.drop).toInt()
            val bounce = Costs.bounce(nodes.floorBlock(x, z, fromHeight - landing.drop), blocks)
            emit(x, landing.y, z, Move.FALL, Costs.fall(blocks) + bounce + landing.damage * Costs.HURT)
        }
    }

    /**
     * Level moves out of a gap only a crouching player fits, where there is no room to jump.
     */
    private fun crouchMoves() {
        val y = from.y
        changes.clear()
        for (i in DX.indices) {
            val tx = from.x + DX[i]
            val tz = from.z + DZ[i]
            val top = terrain.standHeight(tx, y, tz).ifNaN { terrain.crouchHeight(tx, y, tz) }
            if (!top.isNaN() && top - fromHeight <= Terrain.STEP_HEIGHT) {
                emit(tx, y, tz, Move.TRAVERSE, Costs.SNEAK)
            }
        }
    }

    /**
     * Whether the move to [to] goes level past a deadly drop.
     */
    private fun isAtEdge(to: BlockPos, move: Move): Boolean {
        val (x, y, z) = from
        val level = move == Move.TRAVERSE || move == Move.DIAGONAL
        if (!level || nodes.swims(x, y, z) || nodes.swims(to.x, to.y, to.z)) {
            return false
        }

        val toHeight = nodes.height(to.x, to.y, to.z)
        // Safe walk stops at every ledge higher than a step, the ones the move goes down included.
        if (abs(toHeight - fromHeight) > Terrain.STEP_HEIGHT) {
            return false
        }

        return nodes.isNearDrop(x, y, z, fromHeight) || nodes.isNearDrop(to.x, to.y, to.z, toHeight)
    }

    private fun emit(x: Int, y: Int, z: Int, move: Move, cost: Double, jump: Jump? = null) {
        if (terrain.isBowl(x, y, z)) {
            return
        }
        opens.clear()
        if (terrain.canOpenDoors) {
            val body = terrain.box(from.x + 0.5, fromHeight, from.z + 0.5)
            terrain.closedIn(body.minmax(terrain.box(x + 0.5, nodes.height(x, y, z), z + 0.5)), body, opens)
        }

        val total = cost + opens.size * Costs.OPEN
        sink.accept(BlockPos(x, y, z), move, total, changes.breaks, changes.places, opens, jump)
    }

    /**
     * Ticks to walk one block onto the floor at [x] [z] with the feet at [top], or to swim it when [wet].
     */
    private fun walkCost(x: Int, z: Int, top: Double, wet: Boolean = false) =
        if (wet) Costs.SWIM else walk * (fromFloorFactor + Costs.floorFactor(nodes.floorBlock(x, z, top))) / 2.0

    private fun isWet(tx: Int, tz: Int) = terrain.isWater(from.x, from.y, from.z) || terrain.isWater(tx, from.y, tz)

    /**
     * Extra ticks to rise [rise] blocks: nothing up to a step, a jump up to [MAX_JUMP], NaN beyond.
     */
    private fun jumpCost(rise: Double) = when {
        rise <= Terrain.STEP_HEIGHT -> 0.0
        canJump && rise <= MAX_JUMP -> Costs.JUMP
        else -> Double.NaN
    }

    private fun hasJumpRoom(tx: Int, tz: Int) =
        terrain.isPassable(from.x, from.y + 2, from.z) && terrain.isPassable(tx, from.y + 2, tz)

    /**
     * Whether no thin block on the edge to [tx] [tz], such as a closed door, is in the way of a body at [height].
     */
    private fun canCross(tx: Int, tz: Int, height: Double) = !fromStands ||
        terrain.isClear(terrain.box((from.x + tx) / 2.0 + 0.5, height, (from.z + tz) / 2.0 + 0.5))

    /**
     * Feet height on the floor of [x] [y] [z]; without one the block below is placed if [hasSupport]. NaN if not.
     */
    private inline fun floorOrPlace(x: Int, y: Int, z: Int, hasSupport: () -> Boolean): Double {
        val floor = nodes.floorTop(x, y, z)
        if (!floor.isNaN() || !nodes.isPlaceable(x, y - 1, z) || !hasSupport()) {
            return floor
        }

        changes.places += BlockPos(x, y - 1, z)
        return y.toDouble()
    }

    private companion object {
        const val MAX_JUMP = 1.25
        const val EPSILON = 1.0E-6

        val DIAGONAL = Mth.SQRT_OF_TWO

        val DIAGONAL_X = intArrayOf(1, 1, -1, -1)
        val DIAGONAL_Z = intArrayOf(1, -1, 1, -1)

        val EVERY_JUMP = JumpFilter { _, _, _ -> true }
    }

}
