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
import net.ccbluex.agent.math.ifNaN
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.pathfinder.walk.Jump
import net.ccbluex.agent.pathfinder.walk.JumpTargets
import net.ccbluex.agent.pathfinder.walk.Jumps
import net.ccbluex.agent.pathfinder.walk.Leap
import net.ccbluex.agent.pathfinder.walk.LeapInput
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.simulation.PlayerMotion
import kotlin.math.abs

/**
 * The jump across a gap a player running with some heading goes for, worked out by simulation every tick: the block
 * it lands best on, the tick to jump at and the keys in the air. The player only holds the keys towards it.
 *
 * From LiquidBounce: [AssistedJump.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/AssistedJump.kt#L42-L294)
 *
 * @agent claude-opus-5-5
 */
public class AssistedJump {

    /**
     * The block jumped to, with the feet at [feet], and the jump onto it. A jump that does not work from where the
     * player is has a [step], which [Leap] makes from its run-up. A landing that [bounces] off slime [takesOff] again
     * when the bounce gets higher than a jump or a jump on it reaches a block further on, so the speed is kept onto
     * it. One it [settles] on is landed on sneaking, which stops a bounce.
     */
    public class Landing(
        public val pos: BlockPos,
        public val feet: Double,
        public val jump: Jump,
        public val step: Step? = null,
        public val bounces: Boolean = false,
        public val takesOff: Boolean = false,
        public val settles: Boolean = false,
    ) {
        /**
         * Where to turn to: the jump was simulated running there, which can be beside the middle of the block.
         */
        public val aim: Vec3
            get() = jump.aim

        public fun isLanded(position: Vec3, onGround: Boolean): Boolean =
            onGround && abs(position.y - feet) < FEET_PROBE && isOver(position)

        private fun isOver(position: Vec3) =
            abs(position.x - pos.x - HALF_BLOCK) < HALF_BLOCK + PlayerMotion.HALF_WIDTH &&
                abs(position.z - pos.z - HALF_BLOCK) < HALF_BLOCK + PlayerMotion.HALF_WIDTH

        /**
         * The yaw a player at [position], moving by [motion], turns to for the jump towards [look], or NaN to hold the
         * one it has: close to it, where the way there turns around once it is passed, past it in the air, and down on
         * the block until the next tick picks the jump after it. Over a landing that [takesOff], it keeps facing on,
         * as the jump off the bounce goes the way it faces.
         */
        public fun aimYRot(position: Vec3, motion: Vec3, onGround: Boolean, look: Vec3 = aim): Float {
            val delta = look.subtract(position)
            val passed = !onGround && delta.x * motion.x + delta.z * motion.z < 0.0
            val near = delta.horizontalDistanceSqr() < MIN_AIM_DISTANCE * MIN_AIM_DISTANCE
            val bouncesOn = takesOff && isOver(position)
            return if (near || passed || bouncesOn || isLanded(position, onGround)) Float.NaN else delta.yRot
        }

        /**
         * The keys for [state], a player in the air moving with [yRot], that land the furthest inside the block, or
         * with the most speed when the landing takes off again; sneaking on the tick it lands when it settles.
         */
        public fun steer(state: PlayerMotion, yRot: Float, jumps: Jumps): LeapInput? {
            val air = jumps.airKeys(state, yRot, pos, feet, keepSpeed = takesOff) ?: return null
            val sprint = air.forward > 0
            val lands = settles && state.copy().apply { tick(yRot, air.forward, air.strafe, false, sprint) }.onGround
            return LeapInput(air.forward, air.strafe, jump = false, sprint = sprint && !lands, shift = lands)
        }
    }

    /**
     * A jump of the way [trace] predicts: onto [target], the positions tick for tick in [path], and the index in it of
     * the last one on the ground before the jump, or -1 when it starts in the air or off a bounce.
     */
    public class Arc(public val target: Landing) {
        public val path: ArrayList<Vec3> = ArrayList()
        public var takeoff: Int = -1
            private set

        /**
         * Moves [state], moving with [yRot] or towards the aim when null, the way the assist takes it onto [target]:
         * through the take-off of its jump, then steered and turned as in the air until it comes down. The yaw it
         * comes down with.
         */
        public fun follow(state: PlayerMotion, yRot: Float?, jumps: Jumps): Float {
            path += state.position
            // Off a bounce the player leaves the ground anyway and steers as in the air.
            val bounces = state.deltaY > 0.0 && !target.jump.jumps
            if (state.onGround && !bounces) {
                if (!jumps.takeOff(state, yRot, target.pos, target.feet, target.jump, path)) {
                    return Float.NaN
                }
                takeoff = path.lastIndex - 1
            }

            var turn = yRot ?: target.aim.subtract(state.position).yRot
            var ticks = 0
            do {
                turn = target.aimYRot(state.position, state.deltaMovement, state.onGround).ifNaN { turn }
                val keys = target.steer(state, turn, jumps) ?: break
                state.tick(turn, keys.forward, keys.strafe, keys.jump, keys.sprint, keys.shift)
                path += state.position
            } while (!state.onGround && ++ticks < MAX_AIR_TICKS)
            return turn
        }
    }

    public var target: Landing? = null
        private set

    /**
     * The way to [target] as [trace] predicted it, jump by jump: from its run-up when it has one, and on to the next
     * block when the landing takes off again.
     */
    public val arcs: ArrayList<Arc> = ArrayList()

    private var leap = Leap()

    /**
     * The run-up targets planned while standing on [plannedFrom], by the block jumped to; null where none works.
     */
    private val planned = HashMap<BlockPos, Landing?>()
    private var plannedFrom: BlockPos? = null

    /**
     * Whether an assisted jump is in the air.
     */
    private var airborne = false
    private var ticksSinceLanding = BRAKE_TICKS

    /**
     * [Landing.aimYRot] of [target], or NaN without one: towards where [Leap] looks on the way to a run-up, which it
     * simulated walking there with.
     */
    public fun aimYRot(position: Vec3, motion: Vec3, onGround: Boolean): Float {
        val target = target ?: return Float.NaN
        val look = leap.target.takeIf { target.step != null } ?: target.aim
        return target.aimYRot(position, motion, onGround, look)
    }

    /**
     * The keys for [state], a player moving with [yRot] who holds [keys] towards [heading], that make the jump it
     * goes for; null when there is nothing to help with.
     */
    public fun tick(
        state: PlayerMotion,
        yRot: Float,
        heading: Float,
        keys: LeapInput,
        maxAngle: Float,
        jumps: Jumps,
        targets: JumpTargets,
    ): LeapInput? {
        if (!state.onGround) {
            return target?.steer(state, yRot, jumps)
        }

        if (airborne) {
            airborne = false
            ticksSinceLanding = 0
        } else if (ticksSinceLanding < BRAKE_TICKS) {
            ticksSinceLanding++
        }

        val target = runUpKept(state, heading, maxAngle) ?: pick(targets, jumps, state, yRot, heading, maxAngle)
        if (target?.step != null && target !== this.target) {
            leap = Leap()
        }
        this.target = target
        if (target == null) {
            return untargeted(state, yRot, keys)
        }

        val step = target.step
        if (step != null) {
            return leap.decide(state, yRot, step, target.jump, jumps).also { airborne = it.jump }
        }
        if (state.deltaY > 0.0 && !target.jump.jumps) {
            // Off a bounce the player leaves the ground anyway, so this tick steers as in the air.
            airborne = true
            return target.steer(state, yRot, jumps)
        }
        val jumpNow = target.jump.jumps && target.jump.takeoff == 0
        airborne = jumpNow
        return LeapInput(1, 0, jump = jumpNow, sprint = true)
    }

    /**
     * Predicts the way to [target] into [arcs] for [state], a player moving with [yRot] who heads for [heading], and
     * from a landing that takes off again on to the block within [maxAngle] of it that the bounce goes for.
     */
    public fun trace(
        state: PlayerMotion,
        yRot: Float,
        heading: Float,
        maxAngle: Float,
        jumps: Jumps,
        targets: JumpTargets,
    ) {
        arcs.clear()
        val target = target ?: return
        val probe = state.copy()
        val runUp = target.step != null
        if (runUp) {
            probe.reset(target.jump.runUp, PlayerMotion.RESTING, onGround = true)
        }
        val first = Arc(target)
        arcs += first
        val turn = first.follow(probe, yRot.takeUnless { runUp }, jumps)
        if (!target.takesOff || probe.deltaY <= 0.0 || !target.isLanded(probe.position, probe.onGround)) {
            return
        }

        val next = pick(targets, jumps, probe, turn, heading, maxAngle) ?: return
        arcs += Arc(next).also { it.follow(probe, turn, jumps) }
    }

    public fun release() {
        target = null
        arcs.clear()
    }

    public fun reset() {
        release()
        airborne = false
        ticksSinceLanding = BRAKE_TICKS
        planned.clear()
        plannedFrom = null
    }

    /**
     * The target being run up to, while the player is still on the block it jumps from and heads for it.
     */
    private fun runUpKept(state: PlayerMotion, heading: Float, maxAngle: Float): Landing? {
        val target = target ?: return null
        val step = target.step ?: return null
        if (leap.hopeless) {
            planned[step.to] = null
            return null
        }
        val stays = state.blockPosition == step.from
        return target.takeIf { stays && angle(state.position, target.aim, heading) <= maxAngle }
    }

    /**
     * The block across a gap within [maxAngle] of [heading] that a jump from here lands on best. A block the player
     * clearly heads for more than that one is jumped to from a run-up when no jump from here works. Slime that a jump
     * on its bounce goes on from is jumped to as late as it still lands, to come down as far on it as it can.
     */
    private fun pick(
        targets: JumpTargets,
        jumps: Jumps,
        state: PlayerMotion,
        yRot: Float,
        heading: Float,
        maxAngle: Float,
    ): Landing? {
        val position = state.position
        val feet = state.blockPosition
        val standing = targets.terrain.standHeight(feet.x, feet.y, feet.z)
        val height = if (standing.isNaN()) position.y else standing
        val found = Landings.find(targets, jumps, state, yRot, heading, maxAngle, feet, height)
        fun best(): Landing? {
            val best = found.best ?: return null
            if (!best.bounces || best.takesOff) {
                return best
            }
            val jump = jumps.lastFromHere(state, yRot, best.pos, best.feet) ?: return best
            val chained = Landing(best.pos, best.feet, jump, bounces = true, takesOff = true)
            val landing = state.copy()
            val turn = Arc(chained).follow(landing, yRot, jumps)
            val leadsOn = chained.isLanded(landing.position, landing.onGround) && landing.deltaY > 0.0 &&
                Landings.find(targets, jumps, landing, turn, heading, maxAngle, best.pos, best.feet).best != null
            return if (leadsOn) chained else best
        }
        return when {
            // Bouncing off slime leaves no run-up.
            state.deltaY > 0.0 -> best() ?: settle(state, yRot, jumps, feet.takeUnless { standing.isNaN() }, height)
            standing.isNaN() -> best()
            else -> found.runUp { runUpTo(it, jumps, feet, height) } ?: best()
        }
    }

    /**
     * The jump to [missed] from a run-up on [feet], standing at [height]: planned once while the player stays on the
     * block, as a jump around something takes a while to find.
     */
    private fun runUpTo(missed: Landings.Missed, jumps: Jumps, feet: BlockPos, height: Double): Landing? {
        if (feet != plannedFrom) {
            planned.clear()
            plannedFrom = feet
        }
        if (missed.pos in planned) {
            return planned[missed.pos]
        }

        val plan = jumps.plan(feet, height, missed.pos, missed.top, around = true)
        val target = plan?.let {
            val landing = Vec3(missed.pos.x + HALF_BLOCK, missed.top, missed.pos.z + HALF_BLOCK)
            val step = Step(
                feet, missed.pos, Move.PARKOUR, landing, emptyList(), emptyList(), emptyList(), cost = 0.0, jump = it,
            )
            Landing(missed.pos, missed.top, it, step)
        }
        planned[missed.pos] = target
        return target
    }

    /**
     * With nowhere to go off a bounce, coming down where it bounced off, or on [feet] at [height], without a jump that
     * would keep it bouncing.
     */
    private fun settle(state: PlayerMotion, yRot: Float, jumps: Jumps, feet: BlockPos?, height: Double): Landing? {
        val from = target?.takeIf { it.isLanded(state.position, state.onGround) }
        val pos = from?.pos ?: feet ?: return null
        val top = from?.feet ?: height
        val keys = jumps.airKeys(state, yRot, pos, top) ?: return null
        val middle = Vec3(pos.x + HALF_BLOCK, top, pos.z + HALF_BLOCK)
        val jump = Jump(state.position, takeoff = 0, jumps = false, airTicks = 0, keys.margin, hops = 0, middle)
        return Landing(pos, top, jump, settles = true)
    }

    /**
     * The keys without a target, or null to leave [keys] to the jump at the edge. After a landing, holds back when
     * they would run off a block too small to stop on later. Where they only step down onto ground a little lower,
     * like off a bed onto the floor beside it, goes on without a jump, as the one at the edge would take off there,
     * short of the edge after.
     */
    private fun untargeted(state: PlayerMotion, yRot: Float, keys: LeapInput): LeapInput? {
        val probe = state.copy()
        var leaves = -1
        var lands = false
        for (tick in 0 until STEP_DOWN_TICKS) {
            probe.tick(yRot, keys.forward, keys.strafe, jump = false, sprint = state.isSprinting)
            if (!probe.onGround && leaves < 0) {
                leaves = tick
            } else if (probe.onGround && leaves >= 0) {
                lands = true
                break
            }
        }

        val brakes = ticksSinceLanding < BRAKE_TICKS && leaves in 0 until BRAKE_LOOKAHEAD &&
            state.horizontalSpeed > BRAKE_SPEED
        // Off a bed or slime, the probe bounces and comes down where it was, which is no step.
        val drop = state.y - probe.y
        return when {
            brakes -> LeapInput(-1, 0, jump = keys.jump, sprint = false)
            leaves == 0 && lands && state.deltaY <= 0.0 && drop > 0.0 && drop <= MAX_STEP_DOWN ->
                LeapInput(keys.forward, keys.strafe, jump = false, sprint = keys.sprint)
            else -> null
        }
    }

    private companion object {
        const val FEET_PROBE = 1.0E-3
        const val HALF_BLOCK = 0.5
        const val MIN_AIM_DISTANCE = 0.75
        const val MAX_AIR_TICKS = 60

        const val BRAKE_TICKS = 10
        const val BRAKE_LOOKAHEAD = 3
        const val BRAKE_SPEED = 0.1

        /**
         * How far and for how many ticks a step down falls, which is walked rather than jumped off.
         */
        const val MAX_STEP_DOWN = 0.6
        const val STEP_DOWN_TICKS = 5
    }

}
