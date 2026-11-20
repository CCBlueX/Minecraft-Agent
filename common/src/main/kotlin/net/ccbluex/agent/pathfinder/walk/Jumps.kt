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
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Course.Companion.HALF_BLOCK
import net.ccbluex.agent.pathfinder.walk.Course.Companion.RUN_UP_STEP
import net.ccbluex.agent.simulation.PlayerMotion
import kotlin.math.min

/**
 * Finds out by simulation whether and how a jump between two feet blocks works. Not thread-safe. A player that
 * [holdsYRot] keeps facing on in the air once past the middle of the target, as the Parkour assist does, so it pulls
 * back without sprinting; otherwise it turns to the target and sprints back.
 *
 * From LiquidBounce: [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L121-L274), [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L417-L479)
 *
 * @agent claude-opus-5-5
 */
@Suppress("TooManyFunctions")
public class Jumps(
    terrain: Terrain,
    private val abilities: WalkAbilities,
    holdsYRot: Boolean = false,
) {

    public val canSprint: Boolean
        get() = abilities.canSprint

    private val minMargin = if (abilities.riskyJumps) MIN_MARGIN else CAREFUL_MARGIN

    private val course = Course(terrain, abilities)
    private val motion = PlayerMotion(terrain, abilities.motion)
    private val flight = Airtime(course, motion, abilities, holdsYRot)
    private val run = RunUp(course, motion, flight, abilities)
    private val rest = motion.copy()
    private val ground = ArrayList<PlayerMotion>()
    private val pool = ArrayList<PlayerMotion>()
    private val estimates = ArrayList<Estimate>()

    /**
     * The best way to jump from [from], standing at [fromHeight], to [to] with the feet at [toHeight], or null. With
     * [around], a jump that runs into something on the straight way is tried from all over [from] and past its sides.
     */
    public fun plan(from: BlockPos, fromHeight: Double, to: BlockPos, toHeight: Double, around: Boolean = false): Jump? {
        course.set(from.x + 0.5, fromHeight, from.z + 0.5, to, toHeight)
        if (!abilities.riskyJumps && !course.isSafe()) {
            return null
        }

        val center = course.behind(0.0)
        var best = fromRest(center)
        if (!best.has(GOOD_MARGIN)) {
            // Longer run-ups are faster, but the right take-off spot can be anywhere on the way.
            val longest = course.runUpLength(MAX_RUN_UP)
            var back = RUN_UP_SPACING
            while (back < longest + RUN_UP_SPACING && !best.has(GOOD_MARGIN)) {
                best = better(best, fromRest(course.behind(minOf(back, longest))))
                back += RUN_UP_SPACING
            }
        }
        if (!best.has(minMargin) && course.distance <= HOP_REACH) {
            best = better(best, hopped())
        }
        for (offset in SIDE_AIMS) {
            if (best.has(minMargin)) {
                break
            }
            course.aimBeside(offset)
            best = better(best, fromRest(center))
        }
        if (around && course.obstructed && !best.has(minMargin)) {
            best = better(best, around(from))
        }

        return best?.takeIf { it.margin >= minMargin }
    }

    /**
     * Like [plan], but from the run-up that lands best, tried in small steps back from the middle of [from].
     */
    public fun refine(from: BlockPos, fromHeight: Double, to: BlockPos, toHeight: Double): Jump? {
        course.set(from.x + 0.5, fromHeight, from.z + 0.5, to, toHeight)
        val longest = course.runUpLength(MAX_RUN_UP)
        var best: Jump? = null
        var back = 0.0
        while (back <= longest) {
            best = better(best, fromRest(course.behind(back)))
            back += RUN_UP_STEP
        }
        return best?.takeIf { it.margin >= minMargin }
    }

    /**
     * The jump onto [to] from where [state], a player on the ground, is now, moving this tick with [yRot]; also run
     * towards [aim], the one a jump was planned with, when given.
     */
    public fun fromHere(state: PlayerMotion, yRot: Float, to: BlockPos, toHeight: Double, aim: Vec3? = null): Jump? {
        course.set(state.x, state.y, state.z, to, toHeight)
        val here = state.position
        var best = attempt(state, here, yRot, hops = 0, validations = Int.MAX_VALUE)
        for (offset in SIDE_AIMS) {
            course.aimBeside(offset)
            best = better(best, attempt(state, here, yRot, hops = 0, validations = Int.MAX_VALUE))
        }
        if (aim != null) {
            course.aimX = aim.x
            course.aimZ = aim.z
            best = better(best, attempt(state, here, yRot, hops = 0, validations = Int.MAX_VALUE))
        }
        return best
    }

    /**
     * The jump onto [to] that takes off this tick from where [state], a player on the ground, is, moving with [yRot];
     * null unless it lands well. It saves the run to the take-off [fromHere] lands best from, and lands with speed.
     *
     * @agent claude-opus-5-5
     */
    public fun takingOffNow(state: PlayerMotion, yRot: Float, to: BlockPos, toHeight: Double): Jump? {
        course.set(state.x, state.y, state.z, to, toHeight)
        motion.copyFrom(state)
        run.tick(yRot, jump = true)
        if (motion.onGround) {
            return null
        }
        return flight.jump(state.position, 0, jumps = true, hops = 0)?.takeIf { it.margin >= GOOD_MARGIN }
    }

    /**
     * Like [fromHere], but the jump that takes off last of those that land on [to], which gets the furthest onto it:
     * for a landing that is the next take-off.
     */
    public fun lastFromHere(state: PlayerMotion, yRot: Float, to: BlockPos, toHeight: Double): Jump? {
        course.set(state.x, state.y, state.z, to, toHeight)
        pool += ground
        ground.clear()
        motion.copyFrom(state)
        runToEdge(yRot)
        for (takeoff in ground.indices.reversed()) {
            motion.copyFrom(ground[takeoff])
            run.tick(run.yRotAt(takeoff, yRot), jump = true)
            val jump = if (motion.onGround) null else flight.jump(state.position, takeoff, jumps = true, hops = 0)
            if (jump != null && jump.margin >= minMargin) {
                return jump
            }
        }
        return null
    }

    /**
     * The keys for this tick, moving with [yRot], that let [state], a player in the air, land best on [to]. With
     * [keepSpeed], the first keys in the order tried, forward first, that land well enough, as the landing is a
     * take-off.
     */
    public fun airKeys(
        state: PlayerMotion,
        yRot: Float,
        to: BlockPos,
        toHeight: Double,
        keepSpeed: Boolean = false,
    ): AirKeys? {
        course.set(state.x, state.y, state.z, to, toHeight)
        var best: AirKeys? = null
        for (forward in 1 downTo -1) {
            for (strafe in STRAFES) {
                motion.copyFrom(state)
                motion.tick(yRot, forward, strafe, jump = false, sprint = abilities.canSprint)
                val margin = flight.land()
                if (keepSpeed && margin >= minMargin) {
                    return AirKeys(forward, strafe, margin)
                }
                if (!margin.isNaN() && (best == null || margin > best.margin + KEY_PREFERENCE)) {
                    best = AirKeys(forward, strafe, margin)
                }
            }
        }
        return best
    }

    /**
     * Runs [state], a player on the ground, through the run and the take-off of [jump] onto [to], with the feet at
     * [toHeight]: the first tick with [yRot], or towards the aim when null, and the positions into [path]. False when
     * a hop of it fails.
     */
    @Suppress("LongParameterList")
    public fun takeOff(
        state: PlayerMotion,
        yRot: Float?,
        to: BlockPos,
        toHeight: Double,
        jump: Jump,
        path: MutableList<Vec3>,
    ): Boolean {
        course.set(state.x, state.y, state.z, to, toHeight)
        course.aimX = jump.aim.x
        course.aimZ = jump.aim.z
        motion.copyFrom(state)
        run.path = path
        try {
            val hopTicks = run.hop(jump.hops)
            if (hopTicks < 0) {
                return false
            }
            // Running off the edge, the last tick on the ground is the one that leaves it.
            val groundTicks = jump.takeoff - hopTicks - if (jump.jumps) 0 else 1
            for (tick in 0 until groundTicks) {
                run.tick(run.yRotAt(tick, yRot))
            }
            run.tick(run.yRotAt(groundTicks, yRot), jump = jump.jumps)
            state.copyFrom(motion)
            return true
        } finally {
            run.path = null
        }
    }

    /**
     * @agent claude-opus-5-5
     */
    public class AirKeys(public val forward: Int, public val strafe: Int, public val margin: Double)

    /**
     * A jump past something in the way: from rest at spots all over [from], nearest to its middle first, run towards
     * points beside the target and steered back onto it in the air. Only ways clear of the body are simulated.
     */
    private fun around(from: BlockPos): Jump? {
        var best: Jump? = null
        for (spot in AROUND_SPOTS) {
            val runUp = Vec3(from.x + spot.x, course.fromY, from.z + spot.z)
            if (!course.canStandAt(runUp)) {
                continue
            }
            for (offset in AROUND_AIMS) {
                course.aimBeside(offset)
                if (course.isWayClear(runUp)) {
                    best = better(best, fromRest(runUp))
                    if (best.has(GOOD_MARGIN)) {
                        return best
                    }
                }
            }
        }
        return best
    }

    /**
     * A jump out of reach of a run: hops along a runway first, the last landing right before the edge.
     */
    private fun hopped(): Jump? {
        val runway = course.runUpLength(MAX_HOP_RUN_UP)
        course.edge = course.edgeLength()
        for (hops in 1..MAX_HOPS) {
            val travel = run.hopTravel(runway, hops)
            val back = travel + HOP_LEAD - course.edge
            if (travel.isNaN() || back > runway) {
                return null
            }

            val jump = fromRest(course.behind(back), hops)
            if (jump.has(minMargin)) {
                return jump
            }
        }
        return null
    }

    private fun fromRest(runUp: Vec3, hops: Int = 0): Jump? {
        rest.reset(runUp, PlayerMotion.RESTING, onGround = true)
        return attempt(rest, runUp, null, hops, VALIDATED_TAKEOFFS)
    }

    /**
     * Runs on from [state] after [hops] and simulates running off the edge and the [validations] best take-offs.
     */
    private fun attempt(state: PlayerMotion, runUp: Vec3, firstYRot: Float?, hops: Int, validations: Int): Jump? {
        pool += ground
        ground.clear()
        motion.copyFrom(state)
        val hopTicks = run.hop(hops)
        if (hopTicks < 0) {
            return null
        }

        var best: Jump? = null
        // Off a bouncing slime block, leaving the ground without a jump goes up as well.
        if (runToEdge(firstYRot) && (course.targetHeight < motion.y || motion.deltaY > 0.0)) {
            best = flight.jump(runUp, hopTicks + ground.size, jumps = false, hops)
        }

        estimateTakeoffs(firstYRot)
        for (i in 0 until min(estimates.size, validations)) {
            val estimate = estimates[i]
            motion.copyFrom(estimate.state)
            val jump = flight.jump(runUp, hopTicks + estimate.takeoff, jumps = true, hops) ?: continue
            best = better(best, jump)
            if (jump.margin >= GOOD_MARGIN) {
                break
            }
        }
        estimates.forEach { pool += it.state }
        return best
    }

    /**
     * Runs [motion] on, with a snapshot before every tick in [ground]; whether it ran off an edge.
     */
    private fun runToEdge(firstYRot: Float?): Boolean {
        for (tick in 0 until MAX_GROUND_TICKS) {
            ground += snapshot()
            run.tick(run.yRotAt(tick, firstYRot))
            // Grazing a wall, the run slides on along it; only a wall it stops at ends it.
            if (motion.horizontalCollision && motion.horizontalSpeed < STOPPED_SPEED) {
                return false
            }
            if (!motion.onGround) {
                return true
            }
        }
        return false
    }

    private fun estimateTakeoffs(firstYRot: Float?) {
        estimates.clear()
        for (takeoff in ground.indices) {
            motion.copyFrom(ground[takeoff])
            run.tick(run.yRotAt(takeoff, firstYRot), jump = true)
            val margin = if (motion.onGround) Double.NaN else course.estimate(motion)
            if (!margin.isNaN()) {
                estimates += Estimate(takeoff, margin, snapshot())
            }
        }
        estimates.sortByDescending(Estimate::margin)
    }

    private fun snapshot(): PlayerMotion =
        if (pool.isEmpty()) motion.copy() else pool.removeLast().also { it.copyFrom(motion) }

    private fun better(a: Jump?, b: Jump?) = if (a == null || b != null && b.margin > a.margin) b else a

    private fun Jump?.has(margin: Double) = this != null && this.margin >= margin

    private class Estimate(val takeoff: Int, val margin: Double, val state: PlayerMotion)

    private companion object {
        const val MAX_RUN_UP = 1.6
        const val RUN_UP_SPACING = 0.3

        const val MAX_HOPS = 2
        const val MAX_HOP_RUN_UP = 8.0
        const val HOP_REACH = 6.2

        /**
         * How far before the edge the last hop lands.
         */
        const val HOP_LEAD = 0.3
        const val MAX_GROUND_TICKS = 16
        const val STOPPED_SPEED = 0.01
        const val VALIDATED_TAKEOFFS = 4

        val SIDE_AIMS = doubleArrayOf(0.3, -0.3)

        /**
         * Run-up spots tried on the block for a jump [around] something, up to the edges a player still stands on.
         */
        val AROUND_SPOTS: List<Vec3> = doubleArrayOf(-0.25, 0.1, 0.5, 0.9, 1.25).let { along ->
            along.flatMap { x -> along.map { z -> Vec3(x, 0.0, z) } }
                .filter { it.x != HALF_BLOCK || it.z != HALF_BLOCK }
                .sortedBy { Mth.lengthSquared(it.x - HALF_BLOCK, it.z - HALF_BLOCK) }
        }
        val AROUND_AIMS = doubleArrayOf(0.0, 0.3, -0.3, 0.7, -0.7, 1.2, -1.2)

        /**
         * How much better a margin must be for other keys than the ones tried first: forward, no strafe.
         */
        const val KEY_PREFERENCE = 0.02
        val STRAFES = intArrayOf(0, 1, -1)

        const val MIN_MARGIN = 0.15
        const val CAREFUL_MARGIN = 0.35

        /**
         * A margin that makes a run-up from further back not worth it.
         */
        const val GOOD_MARGIN = 0.45
    }

}
