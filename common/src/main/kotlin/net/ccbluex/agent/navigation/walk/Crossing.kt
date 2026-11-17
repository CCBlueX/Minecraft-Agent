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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.navigation.Bridging
import net.ccbluex.agent.navigation.MIN_SPRINT_FOOD
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.simulateTick
import net.ccbluex.agent.navigation.withKeys
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.world.entity.Attributes
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.math.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Crosses the [Bridge] a walk places as a player does, with the [Bridging] method picked for it: which keys, where
 * the view holds and when to click, every tick. A block the server refuses ends a fast method on that bridge for
 * Ninja, and Ninja for placing each floor standing at the edge, which the [Follower] does itself.
 *
 * @agent claude-opus-5-5
 */
internal class Crossing(
    private val context: NavigationContext,
    private val actions: BlockActions,
    private val check: StepCheck,
    private val stride: Stride,
) {

    private val body get() = context.body
    private val player get() = body.player
    private val targets = BlockTargets(context)
    private val telly = Telly(context, actions, check)

    private var bridge: Bridge? = null

    /**
     * The method crossing the bridge, null once it fell back to placing each floor standing at the edge.
     */
    var method: Bridging? = null
        private set

    private var rejections = 0

    /**
     * Floors placed on this bridge, which the cadences of the methods count.
     */
    private var placed = 0
    private var lastPlacement: PlacedBlock? = null
    private var hopTicks = -1
    private var isHopping = false
    private var nudge: ViewRotation? = null
    private var nudgedFloor: BlockPos? = null

    /**
     * Whether the server refused a block on this bridge, after which every block is waited for.
     */
    private var refused = false

    /**
     * The view held while crossing, null while not crossing.
     */
    var view: ViewRotation? = null
        private set

    /**
     * The block placed or waited for this tick, null while only walking.
     */
    var acting: BlockPos? = null
        private set

    /**
     * Whether a method crosses the bridge step [index] of [steps] is part of; picks one for a new bridge.
     */
    fun takes(steps: List<Step>, index: Int): Boolean {
        val found = Bridge.at(steps, index)
        if (found == null) {
            clear()
            return false
        }
        if (found.to != bridge?.to) {
            val sneakingSpeed = player.getAttributeValue(Attributes.SNEAKING_SPEED)
            val canSprint = player.foodData.foodLevel > MIN_SPRINT_FOOD
            method = context.options().bridging.pick(found, context.options().riskyBridges, sneakingSpeed, canSprint)
            placed = 0
            lastPlacement = actions.placement
            rejections = actions.rejections
            refused = false
            hopTicks = -1
            context.logger.debug("Crossing a bridge of {} blocks with {}", found.length, method)
        }
        bridge = found

        if (actions.rejections > rejections && method != null) {
            rejections = actions.rejections
            refused = true
            val fallback = Bridging.NINJA.takeUnless { method == it || found.rises }
            context.logger.info("{} falls back to {} after a refused block", method, fallback ?: "the edge")
            method = fallback
        }
        if (method == null) {
            view = null
        }
        return method != null
    }

    fun clear() {
        bridge = null
        method = null
        view = null
        acting = null
    }

    /**
     * One tick across step [index] of [steps]; [takes] said a method crosses it.
     */
    fun tick(keys: MovementKeys, frameYRot: Float, steps: List<Step>, index: Int): Movement {
        val bridge = checkNotNull(bridge)
        val method = checkNotNull(method)
        countPlacement(method)
        acting = null
        if (method == Bridging.TELLY) {
            return telly.tick(keys, frameYRot, steps, bridge, method.xRot).also {
                view = telly.view
                acting = telly.acting
            }
        }
        val step = steps[index]
        return if (step.isRisingBridge) {
            rise(keys, frameYRot, step, method, bridge)
        } else {
            level(keys, frameYRot, steps, index, method, bridge)
        }
    }

    private fun level(
        keys: MovementKeys,
        frameYRot: Float,
        steps: List<Step>,
        index: Int,
        method: Bridging,
        bridge: Bridge,
    ): Movement {
        val view = backView(method, bridge, method.xRot).also { view = it }
        val step = steps[index]
        var target = step
        var floor = step.places.firstOrNull { !check.isPlaced(it) }
        val unconfirmed = step.places.firstOrNull(actions::isUnconfirmed)
        if (floor == null && unconfirmed != null) {
            if (waits(method)) {
                return hold(keys, frameYRot, step, unconfirmed)
            }
            // The server has yet to answer for this floor; the next one goes on meanwhile.
            steps.getOrNull(index + 1)?.takeIf { index + 1 < bridge.end && it.isBridge }?.let { next ->
                target = next
                floor = next.places.firstOrNull { !check.isPlaced(it) }
            }
        }

        val position = player.position()
        keys.directionalInput = getDirectionalInputTowards(tapped(target, method, bridge, position), frameYRot)
        keys.jump = false
        val pending = floor
        keys.sneak = method.sneaksThroughout || sneaksAtEdge(keys, target, pending, method)
        if (pending != null) {
            val ready = !waits(method) || target.canClickFloor(body, keys, frameYRot, method.sneaksThroughout)
            actions.place(pending, ready, held = aim(pending, view))
            acting = pending
        }
        hop(keys)
        return Movement(target.feet)
    }

    /**
     * Up one block: the floor beside first, standing at the edge, then a jump from there with the block under the
     * landing placed at the top of it, and a sneak before landing.
     */
    private fun rise(keys: MovementKeys, frameYRot: Float, step: Step, method: Bridging, bridge: Bridge): Movement {
        val (low, high) = step.places
        val position = player.position()
        keys.directionalInput = getDirectionalInputTowards(step.feet.subtract(position), frameYRot)
        keys.jump = false
        view = backView(method, bridge, if (check.isPlaced(low)) STACK_X_ROT else method.xRot)
        val view = checkNotNull(view)
        when {
            !check.isPlaced(low) -> {
                keys.sneak = sneaksAtEdge(keys, step, low, Bridging.NINJA)
                val ready = !waits(method) || step.canClickFloor(body, keys, frameYRot, sneaks = false)
                actions.place(low, ready, held = aim(low, view))
                acting = low
            }

            waits(method) && actions.isUnconfirmed(low) -> return hold(keys, frameYRot, step, low)
            !check.isPlaced(high) -> {
                actions.place(high, ready = true, held = view)
                acting = high
                if (!player.onGround()) {
                    keys.sneak = player.deltaMovement.y < 0.0
                } else if (step.pastEdge(position) < -EDGE_REACH) {
                    keys.sneak = true
                } else if (player.deltaMovement.horizontalDistance() > STILL) {
                    keys.directionalInput = Input.EMPTY
                    keys.sneak = true
                } else {
                    keys.jump = true
                }
            }

            waits(method) && actions.isUnconfirmed(high) -> return hold(keys, frameYRot, step, high)
            else -> {
                keys.jump = wantsJump(step, player)
                keys.sneak = !player.onGround() && player.deltaMovement.y < 0.0
            }
        }
        return Movement(step.feet)
    }

    /**
     * Holds at the edge until the server keeps the block at [pos]: braking back while still moving out over a floor
     * below, which is not stood on before, then sneaking; a method sneaking all the way brakes sneaking.
     */
    private fun hold(keys: MovementKeys, frameYRot: Float, step: Step, pos: BlockPos): Movement {
        val below = pos.y < step.from.y
        keys.directionalInput =
            if (below) step.holdBack(player.position(), player.deltaMovement, frameYRot) else Input.EMPTY
        keys.jump = false
        // A sneak would brake at a third of the strength.
        keys.sneak = keys.directionalInput == Input.EMPTY || method?.sneaksThroughout == true
        acting = pos
        return Movement(step.feet)
    }

    /**
     * The [view] held for [floor], or, standing at the edge with the view there and missing the face, the nearest
     * look that sees it, as a player nudges the mouse; the nudge stays until the player moves on, or is made again
     * once the turn got there and still misses, as after a slow slide along the edge.
     */
    private fun aim(floor: BlockPos, view: ViewRotation): ViewRotation {
        val standing = player.onGround() && stride.moved.lengthSqr() < STILL * STILL
        if (!standing || nudgedFloor != floor) {
            nudge = null
        }
        val held = nudge
        if (held != null) {
            if (!isSteady(held, STEADY) || targets.clicksFrom(player.eyePosition, body.sentRotation, floor)) {
                return held
            }
        } else if (!standing || !isSteady(view, STEADY) || targets.clicksFrom(player.eyePosition, view, floor)) {
            return view
        }
        nudgedFloor = floor
        nudge = targets.placeableSlot()?.let { targets.planPlacement(floor, it, nearest = true) }?.rotation
        return nudge ?: view
    }

    /**
     * Whether the player sneaks at the edge of [step]: a tick on without sneaking passes it, and the method does not
     * click past the edge, or cannot from where that tick ends, still on the ground, with the rotation aimed this tick.
     */
    private fun sneaksAtEdge(keys: MovementKeys, step: Step, floor: BlockPos?, method: Bridging): Boolean {
        if (!player.onGround()) {
            return false
        }
        val next = body.simulateTick(keys.toInput(sprint = false))
        if (!next.hasBackedOffEdge) {
            // Grim flags a sneak holding the player at the edge right after a walking tick (over 0.1 a tick) ended
            // there, within 0.05 of leaving the floor; without keys for that tick, the sneak stops it short instead.
            val stops = floor == null || !method.clicksPastEdge || method.resets(placed)
            val walking = player.deltaMovement.horizontalDistance() > 0.1
            if (stops && walking && next.player.onGround() && step.pastEdge(next.player.position()) > 0.25) {
                keys.directionalInput = Input.EMPTY
            }
            return false
        }
        val aimed = actions.rotation
        if (floor == null || aimed == null || !method.clicksPastEdge || method.resets(placed)) {
            return true
        }
        return !(isSteady(aimed, STEADY) && next.player.onGround() && targets.clicksFrom(next.player.eyePosition, aimed, floor))
    }

    /**
     * After a placement that reset the timing, a hop in place from a stop, braked to with a sneak: a hop with the
     * keys held would carry the player past the one block placed. Only if the game moves the player to land again.
     */
    private fun hop(keys: MovementKeys) {
        if (isHopping) {
            // Keys up until it lands, as the hop was only checked to land without them.
            isHopping = !player.onGround()
            if (isHopping) {
                keys.directionalInput = Input.EMPTY
                keys.sneak = false
                return
            }
        }
        if (hopTicks < 0 || !player.onGround()) {
            return
        }
        if (player.deltaMovement.horizontalDistance() > STILL && ++hopTicks <= HOP_WAIT) {
            keys.directionalInput = Input.EMPTY
            keys.sneak = true
            return
        }
        val input = Input.EMPTY.withKeys(jump = true, sneak = false, sprint = false)
        val simulated = body.simulateTick(input)
        var lands = false
        for (tick in 0 until HOP_TICKS) {
            if (simulated.player.onGround()) {
                lands = simulated.player.y >= player.y - LANDING_MARGIN
                break
            }
            simulated.tick()
        }
        if (lands) {
            keys.directionalInput = Input.EMPTY
            keys.jump = true
            keys.sneak = false
            isHopping = true
            hopTicks = -1
            context.logger.info("{} hops to reset its timing after block {}", method, placed)
        } else if (++hopTicks > HOP_WAIT) {
            hopTicks = -1
        }
    }

    private fun countPlacement(method: Bridging) {
        val placement = actions.placement
        if (placement == null || placement === lastPlacement) {
            return
        }
        lastPlacement = placement
        if (method.hopsOnReset && method.resets(placed)) {
            hopTicks = 0
        }
        placed++
    }

    /**
     * The way to [target] with the side key of [method] tapped in.
     */
    private fun tapped(target: Step, method: Bridging, bridge: Bridge, position: Vec3): Vec3 {
        val toward = target.feet.subtract(position)
        val dx = target.to.x - target.from.x
        val dz = target.to.z - target.from.z
        val along = position.x * dx + position.z * dz
        val side = method.tap(placed, along - floor(along), bridge.turnAfter)
        if (side == 0 || toward.horizontalDistanceSqr() < MIN_TAP_DISTANCE) {
            return toward
        }
        // To the right of the way (dx, dz) lies (-dz, dx).
        val ahead = toward.normalize()
        return Vec3(ahead.x - dz * side, 0.0, ahead.z + dx * side)
    }

    /**
     * Straight back along [bridge], turned by the method's offset on a straight one.
     */
    private fun backView(method: Bridging, bridge: Bridge, xRot: Float): ViewRotation {
        val offset = if (bridge.isStraight) method.yRotOffset else 0f
        return ViewRotation(Mth.wrapDegrees(bridge.yRot + HALF_TURN + offset), xRot)
    }

    private fun isSteady(view: ViewRotation, tolerance: Float): Boolean {
        val sent = body.sentRotation
        return abs(Mth.wrapDegrees(sent.yRot - view.yRot)) < tolerance && abs(sent.xRot - view.xRot) < tolerance
    }

    /**
     * Whether each block is waited for until the server keeps it: unless risky bridges are allowed or [method] clicks
     * past the edge anyway, and always on a bridge where the server refused one.
     */
    private fun waits(method: Bridging) = refused || !context.options().riskyBridges && !method.clicksPastEdge

    private companion object {
        const val HALF_TURN = 180f

        /**
         * Degrees off the view held within which the rotation sent counts as there.
         */
        const val STEADY = 1f

        /**
         * Pitch for a block under the landing of a one-stack jump, steep enough to see the top it goes on.
         */
        const val STACK_X_ROT = 84f

        /**
         * Horizontal blocks a tick under which the player counts as standing for a jump.
         */
        const val STILL = 0.1
        const val EDGE_REACH = 0.2
        const val MIN_TAP_DISTANCE = 0.01
        const val HOP_TICKS = 20

        /**
         * Ticks a hop is waited for after a reset before it is left out.
         */
        const val HOP_WAIT = 10
        const val LANDING_MARGIN = 0.01
    }
}
