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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs

/**
 * Makes one parkour step by simulating the player every tick: jumps where it works, else walks back to the run-up.
 *
 * From LiquidBounce: [Leap.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Leap.kt#L36-L188)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
public class Leap {

    public var target: Vec3? = null
        private set

    public var sprint: Boolean = false
        private set

    public var hopeless: Boolean = false
        private set

    private var hopsMade = 0
    private var stillTicks = 0

    /**
     * How far behind the run-up the player stops, after no jump worked from it.
     */
    private var retreat = 0.0

    /**
     * Whether the player runs at a jump found on the spot.
     */
    private var running = false
    private var refined: Jump? = null

    /**
     * The keys for [player] this tick, moving with [movementYRot] through [terrain]; the caller presses them relative
     * to its own frame yaw.
     *
     * @agent claude-opus-5-5
     */
    @Suppress("LongParameterList")
    public fun tick(
        player: Player,
        terrain: Terrain,
        movementYRot: Float,
        step: Step,
        jump: Jump,
        abilities: WalkAbilities,
    ): LeapInput {
        val state = PlayerMotion(terrain, abilities.motion)
        state.reset(player)
        val input = decide(state, movementYRot, step, jump, Jumps(terrain, abilities))
        sprint = input.sprint
        return input
    }

    public fun decide(state: PlayerMotion, yRot: Float, step: Step, planned: Jump, jumps: Jumps): LeapInput {
        target = step.feet
        if (!state.onGround) {
            stillTicks = 0
            running = false
            val keys = jumps.airKeys(state, yRot, step.to, step.feet.y)
            val forward = keys?.forward ?: 1
            return LeapInput(forward, keys?.strafe ?: 0, jump = false, sprint = forward > 0)
        }

        val jump = refined ?: planned
        val ready = isReady(state, stop(jump))

        // Away from the run-up, only a jump with room to spare beats walking back to it; a run once started goes on.
        val now = jumps.fromHere(state, yRot, step.to, step.feet.y, jump.aim)
            ?.takeIf { ready || running || it.margin >= LIVE_MARGIN }
        // The run is simulated facing the aim from its second tick on; a body turning at its own pace turns there first.
        if (now != null && !running && !faces(state, yRot, now.aim)) {
            target = now.aim
            return approach(state, yRot, stop(jump), now.aim)
        }
        running = now != null
        if (now != null) {
            target = now.aim
            return LeapInput(1, 0, jump = now.jumps && now.takeoff == 0, sprint = jumps.canSprint)
        }

        // A momentum jump hops from its run-up, each hop right after the last one landed.
        if (hopsMade < jump.hops && (hopsMade > 0 || ready)) {
            hopsMade++
            return LeapInput(1, 0, jump = true, sprint = jumps.canSprint)
        }

        hopsMade = 0
        if (ready && jump.hops == 0) {
            moveRunUp(step, jump, jumps)
        }
        return approach(state, yRot, stop(refined ?: planned), step.feet)
    }

    /**
     * Standing still for [STILL_TICKS] counts where the approach cannot get closer, as at the edge of a block.
     */
    private fun isReady(state: PlayerMotion, stop: Vec3): Boolean {
        stillTicks = if (state.horizontalSpeed < READY_SPEED) stillTicks + 1 else 0
        val atStop = stop.horizontalDistanceToSqr(state.x, state.z) < Mth.square(Jump.RUN_UP_TOLERANCE)
        return stillTicks > 0 && (atStop || stillTicks >= STILL_TICKS)
    }

    private fun faces(state: PlayerMotion, yRot: Float, aim: Vec3) =
        abs(Mth.wrapDegrees(aim.subtract(state.position).yRot - yRot)) <= MAX_FACING_OFF

    /**
     * After no jump worked from the run-up of [jump]: once a run-up with more room, then a stop further back.
     */
    private fun moveRunUp(step: Step, jump: Jump, jumps: Jumps) {
        if (refined == null) {
            refined = jumps.refine(step.from, jump.runUp.y, step.to, step.feet.y)
                ?.takeIf { it.runUp.distanceToSqr(jump.runUp) > Mth.square(Jump.RUN_UP_TOLERANCE) }
            if (refined != null) {
                return
            }
        }

        hopeless = retreat >= MAX_RETREAT
        retreat = minOf(retreat + RETREAT_STEP, MAX_RETREAT)
    }

    /**
     * Where to stand before the run: behind the run-up, as a stop short of it costs a jump less than one past it.
     */
    private fun stop(jump: Jump): Vec3 {
        val runUp = jump.runUp
        val distance = RUN_UP_BIAS + retreat
        val dx = jump.aim.x - runUp.x
        val dz = jump.aim.z - runUp.z
        val length = Mth.length(dx, dz)
        if (length < MIN_LENGTH) {
            return runUp
        }
        return Vec3(runUp.x - dx / length * distance, runUp.y, runUp.z - dz / length * distance)
    }

    /**
     * Keys that bring the player to rest at [stop] on the ground: the best of every choice held for a few ticks.
     */
    private fun approach(state: PlayerMotion, yRot: Float, stop: Vec3, look: Vec3): LeapInput {
        val probe = state.copy()
        var best = LeapInput(0, 0, jump = false, sprint = false)
        var bestScore = Double.MAX_VALUE
        for (sneak in SNEAKS) {
            for (forward in -1..1) {
                for (strafe in -1..1) {
                    probe.copyFrom(state)
                    val input = LeapInput(forward, strafe, jump = false, sprint = false, shift = sneak)
                    val score = restDistanceSqr(probe, yRot, input, stop, look)
                    if (score < bestScore - SCORE_EPSILON) {
                        bestScore = score
                        best = input
                    }
                }
            }
        }
        return best
    }

    private fun restDistanceSqr(probe: PlayerMotion, yRot: Float, input: LeapInput, stop: Vec3, look: Vec3): Double {
        for (tick in 0 until HOLD_TICKS + SETTLE_TICKS) {
            val held = tick < HOLD_TICKS
            val tickYRot = if (tick == 0) yRot else look.subtract(probe.x, probe.y, probe.z).yRot
            probe.tick(
                tickYRot, if (held) input.forward else 0, if (held) input.strafe else 0, jump = false, sprint = false,
                sneak = held && input.shift,
            )
            if (!probe.onGround) {
                // Leaving the ground later is the lesser evil.
                return FALL_PENALTY - tick
            }
        }
        return stop.horizontalDistanceToSqr(probe.x, probe.z)
    }

    private companion object {
        const val HOLD_TICKS = 2
        const val SETTLE_TICKS = 6
        const val FALL_PENALTY = 100.0
        const val SCORE_EPSILON = 1.0E-4

        const val READY_SPEED = 0.03
        const val STILL_TICKS = 5
        const val LIVE_MARGIN = 0.35

        /**
         * Degrees the facing may be off the aim for a run at a jump to start.
         */
        const val MAX_FACING_OFF = 15f

        val SNEAKS = booleanArrayOf(false, true)

        const val RUN_UP_BIAS = 0.05
        const val RETREAT_STEP = 0.1
        const val MAX_RETREAT = 0.5
        const val MIN_LENGTH = 1.0E-6
    }

}

/**
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
public class LeapInput(
    public val forward: Int,
    public val strafe: Int,
    public val jump: Boolean,
    public val sprint: Boolean,
    public val shift: Boolean = false,
)
