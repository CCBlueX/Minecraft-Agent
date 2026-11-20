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

package net.ccbluex.agent.navigation.flight

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.FollowState
import net.ccbluex.agent.navigation.TestBody
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.flight.Route
import net.ccbluex.agent.pathfinder.flight.RouteSearch
import net.ccbluex.agent.pathfinder.found
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.Input
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A fly mode flies a route with a sharp corner at half and at twice the speed a host used to report, stopping at once
 * or coasting on: the player gets to the end and comes to rest there, without flying past it or back and forth. One
 * that hops by itself sinks onto the end below. One pressed against a wall below its top, or set down on its edge,
 * gets on to the platform behind it. One that answers the keys a tick late gets onto a pillar.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class FollowerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val MAX_TICKS = 600
        private const val SETTLE_TICKS = 60
        private const val ARRIVAL = 1.5
        private const val MAX_OVERSHOOT = 1.5
        private const val MAX_REVERSALS = 3
        private const val NEAR_END = 8.0

        /**
         * How close a walk to a goal on the ground stops, as LiquidBounce's `.goto` does.
         */
        private const val GOAL_RADIUS = 1.0

        private val START = Vec3(0.5, 10.0, 0.5)

        // Corners off whole multiples of the speeds, so a fly mode cannot happen to land on them exactly.
        private val POINTS = listOf(START, Vec3(23.3, 10.0, 0.5), Vec3(23.3, 15.3, 21.9), Vec3(4.7, 15.3, 21.9))

        private fun axes(input: Input) = Pair(
            (if (input.forward) 1 else 0) - (if (input.backward) 1 else 0),
            (if (input.left) 1 else 0) - (if (input.right) 1 else 0),
        )
    }

    /**
     * A fly mode that settles at [speed] blocks a tick level and half that vertically, keeping [drag] of its motion a
     * tick: 0 stops as soon as the keys are released.
     *
     * @agent claude-opus-5-5
     */
    private class FlyMode(val speed: Double, val drag: Double) {

        private var velocity = Vec3.ZERO

        fun move(player: Player, input: Input) {
            val x = (if (input.left) 1.0 else 0.0) - (if (input.right) 1.0 else 0.0)
            val z = (if (input.forward) 1.0 else 0.0) - (if (input.backward) 1.0 else 0.0)
            val y = (if (input.jump) 1.0 else 0.0) - (if (input.shift) 1.0 else 0.0)
            val length = sqrt(x * x + z * z).coerceAtLeast(1.0)
            val thrust = speed * (1.0 - drag)
            velocity = velocity.scale(drag).add(x / length * thrust, y * thrust / 2.0, z / length * thrust)
            player.setPos(player.position().add(velocity.toMinecraft()))
            player.deltaMovement = velocity.scale(drag).toMinecraft()
            player.setOnGround(false)
            player.tickCount++
        }
    }

    @ParameterizedTest(name = "speed {0}, drag {1}")
    @CsvSource("0.5, 0.0", "2.0, 0.0", "0.5, 0.8", "2.0, 0.8")
    fun `a fly mode of any speed flies the route to its end and stops there`(speed: Double, drag: Double) {
        val handle = TestLevel(TestWorld()).player(START)
        val mode = FlyMode(speed, drag)
        val body = TestBody(handle, isFlying = true)
        val player = body.player
        val context = body.context()
        val handling = Handling()
        val follower = Follower(context, handling)
        follower.follow(Route(START, POINTS, canReach = true))

        val end = POINTS.last()
        val lastLeg = end.subtract(POINTS[POINTS.size - 2]).normalize()
        var overshoot = 0.0
        var reversals = 0
        var lastMove = Vec3.ZERO
        fun move(input: Input) {
            val before = player.position()
            mode.move(handle, input)
            val moved = player.position().subtract(before)
            if (moved.horizontalDistanceSqr() > 1.0E-6 && moved.x * lastMove.x + moved.z * lastMove.z < 0.0) {
                reversals++
            }
            if (moved.horizontalDistanceSqr() > 1.0E-6) {
                lastMove = moved
            }
            if (player.position().distanceTo(end) < NEAR_END) {
                overshoot = maxOf(overshoot, player.position().subtract(end).dot(lastLeg))
            }
        }

        var ticks = 0
        while (ticks < MAX_TICKS) {
            val abilities = checkNotNull(context.captureFlight(handling))
            if (follower.tick(0f, abilities) == FollowState.FINISHED) {
                break
            }
            move(body.pressed)
            ticks++
        }
        repeat(SETTLE_TICKS) { move(Input.EMPTY) }

        assertTrue(ticks < MAX_TICKS, "Did not finish the route in $MAX_TICKS ticks, at ${player.position()}")
        val rest = player.position()
        assertTrue(rest.distanceTo(end) <= ARRIVAL, "Came to rest at $rest, ${rest.distanceTo(end)} from $end")
        assertTrue(overshoot <= MAX_OVERSHOOT, "Flew $overshoot past the end")
        assertTrue(reversals <= MAX_REVERSALS, "Turned back $reversals times")
        assertEquals(speed, handling.speed, speed * 0.1, "Measured the speed")
    }

    /**
     * A fly mode that hops up every [HOP_TICKS] ticks and falls in between, unless sneak is held in the air, as
     * LiquidBounce's CubeCraft-2024-03-10 mode of Fly does; a hop also sets the level motion the way the keys pointed.
     *
     * @agent claude-opus-5-5
     */
    private class HopMode(world: TestWorld, start: Vec3) {
        private val motion = PlayerMotion(world.terrain()).also { it.reset(start, Vec3.ZERO, onGround = true) }
        private var wait = 0
        private var last = Input.EMPTY

        fun move(player: Player, input: Input, sprint: Boolean) {
            // The mode reads the keys of the tick before
            if (wait > 0) {
                wait--
            } else if (!last.shift || motion.onGround) {
                val (forward, strafe) = axes(last)
                val length = sqrt((forward * forward + strafe * strafe).toDouble()).coerceAtLeast(1.0)
                val hop = Vec3(strafe / length * HOP_STRAFE, HOP_SPEED, forward / length * HOP_STRAFE)
                motion.reset(motion.position, hop, onGround = false, motion.isSprinting)
                wait = HOP_TICKS
            }
            last = input

            val (forward, strafe) = axes(input)
            motion.tick(player.yRot, forward, strafe, jump = false, sprint, sneak = input.shift)
            player.setPos(motion.position.toMinecraft())
            player.deltaMovement = motion.deltaMovement.toMinecraft()
            player.setOnGround(motion.onGround)
            player.horizontalCollision = motion.horizontalCollision
            player.isSprinting = motion.isSprinting
            player.tickCount++
        }

        private companion object {
            const val HOP_TICKS = 11
            const val HOP_SPEED = 0.42
            const val HOP_STRAFE = 0.35
        }
    }

    @Test
    fun `a fly mode that hops lands on the end below`() {
        // Platforms over the void, the far one three blocks lower
        val world = TestWorld().fill(-1, 9, -1, 1, 9, 1).fill(-1, 6, 19, 1, 6, 21)
        val start = TestWorld.feet(0, 10, 0)
        val end = TestWorld.feet(0, 7, 20)
        val handle = TestLevel(world).player(start)
        val mode = HopMode(world, start)
        val body = TestBody(handle, isFlying = true)
        val player = body.player
        val context = body.context()
        val handling = Handling()
        val follower = Follower(context, handling)
        follower.follow(Route(start, listOf(start, end), canReach = true))

        repeat(MAX_TICKS) {
            val abilities = checkNotNull(context.captureFlight(handling))
            if (follower.tick(0f, abilities) == FollowState.FINISHED) {
                val at = player.position()
                assertTrue(at.distanceTo(end) <= GOAL_RADIUS, "Finished at $at, ${at.distanceTo(end)} from $end")
                return
            }
            mode.move(handle, body.pressed, follower.sprint)
            assertTrue(player.position().y > 0.0, "Fell at ${player.position()}")
        }
        fail("Did not land on $end in $MAX_TICKS ticks, at ${player.position()}")
    }

    /**
     * LiquidBounce's Vanilla mode of Fly: the keys set the motion, 0.44 blocks a tick, before the player moves on the
     * game's physics; one that [lags] sets it from the keys of the tick before.
     *
     * @agent claude-opus-5-5
     */
    private class VanillaMode(world: TestWorld, start: Vec3, onGround: Boolean, private val lags: Boolean = false) {
        private val motion = PlayerMotion(world.terrain()).also { it.reset(start, Vec3.ZERO, onGround) }
        private var last = Input.EMPTY

        fun move(player: Player, pressed: Input) {
            val input = if (lags) last else pressed
            last = pressed
            val (forward, strafe) = axes(input)
            val length = sqrt((forward * forward + strafe * strafe).toDouble()).coerceAtLeast(1.0)
            val y = when {
                input.jump && !input.shift -> SPEED
                input.shift && !input.jump -> -SPEED
                else -> 0.0
            }
            motion.reset(motion.position, Vec3(strafe / length * SPEED, y, forward / length * SPEED), motion.onGround)
            motion.tick(player.yRot, forward, strafe, input.jump, sprint = false, sneak = input.shift)
            player.setPos(motion.position.toMinecraft())
            player.deltaMovement = motion.deltaMovement.toMinecraft()
            player.setOnGround(motion.onGround)
            player.horizontalCollision = motion.horizontalCollision
            player.tickCount++
        }

        private companion object {
            const val SPEED = 0.44
        }
    }

    /**
     * Pressed against the wall a hair below its top, where a fly mode that sinks a little now and then stops.
     */
    @Test
    fun `a fly mode pressed against a wall below its top climbs over it`() =
        flyOverWall(Vec3(19.7, 15.96, 0.5), onGround = false)

    /**
     * On the edge of the wall's top, where sinking towards the far side set the player down.
     */
    @Test
    fun `a fly mode that landed on the edge of a wall flies on`() =
        flyOverWall(Vec3(21.29, 16.0, 0.5), onGround = true)

    /**
     * From the ground beside a pillar nine blocks high onto its top with a mode that answers the keys a tick late: the
     * motion it keeps for that tick is not taken for drag, which made the route count as flown a block and more short.
     */
    @Test
    fun `a fly mode that answers a tick late flies onto a pillar`() {
        val world = TestWorld().fill(50, 0, 6, 66, 0, 16).fill(60, 1, 10, 62, 9, 12)
        val start = TestWorld.feet(54, 1, 11)
        val end = TestWorld.feet(61, 10, 11)
        val handle = TestLevel(world).player(start)
        val mode = VanillaMode(world, start, onGround = true, lags = true)
        val body = TestBody(handle, isFlying = true)
        val player = body.player
        val context = body.context()
        val handling = Handling()
        val follower = Follower(context, handling)
        val limits = Limits(64.0, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        val abilities = checkNotNull(context.captureFlight(handling))
        val search = RouteSearch(world.terrain(), start, NearDestination(end, GOAL_RADIUS), abilities, limits)
        val route = found(search.advanceToEnd())
        follower.follow(route)

        repeat(MAX_TICKS) {
            if (follower.tick(0f, checkNotNull(context.captureFlight(handling))) == FollowState.FINISHED) {
                val at = player.position()
                val left = at.distanceTo(route.end)
                assertTrue(left <= GOAL_RADIUS, "Finished at $at, $left from ${route.end}")
                // 0.44 a tick; the measurement blends samples, but a lag taken for drag made it 8 and more
                assertTrue(handling.verticalSpeed < 0.88, "Measured a vertical speed of ${handling.verticalSpeed}")
                return
            }
            mode.move(handle, body.pressed)
        }
        fail("Stopped at ${player.position()} flying ${route.points}")
    }

    private fun flyOverWall(start: Vec3, onGround: Boolean) {
        // A wall too wide to fly around with its top at y 16, and a platform 19 blocks behind it
        val world = TestWorld().fill(20, 3, -30, 20, 15, 30).fill(39, 9, -1, 41, 9, 1)
        val end = TestWorld.feet(40, 10, 0)
        val handle = TestLevel(world).player(start)
        val mode = VanillaMode(world, start, onGround)
        val body = TestBody(handle, isFlying = true)
        val player = body.player
        val context = body.context()
        val handling = Handling()
        val follower = Follower(context, handling)
        val limits = Limits(64.0, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        val abilities = checkNotNull(context.captureFlight(handling))
        val search = RouteSearch(world.terrain(), start, NearDestination(end, GOAL_RADIUS), abilities, limits)
        val route = found(search.advanceToEnd())
        follower.follow(route)

        repeat(MAX_TICKS) {
            if (follower.tick(0f, checkNotNull(context.captureFlight(handling))) == FollowState.FINISHED) {
                val at = player.position()
                val left = at.distanceTo(route.end)
                assertTrue(left <= ARRIVAL, "Finished at $at, $left from ${route.end}")
                return
            }
            mode.move(handle, body.pressed)
            assertTrue(player.position().y > end.y - 8.0, "Fell at ${player.position()} flying ${route.points}")
        }
        fail("Stopped at ${player.position()} flying ${route.points}")
    }
}
