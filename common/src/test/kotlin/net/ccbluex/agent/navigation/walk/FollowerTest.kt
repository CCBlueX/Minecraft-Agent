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
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.FollowState
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.TestBody
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.advanceToEnd
import net.ccbluex.agent.pathfinder.completePath
import net.ccbluex.agent.pathfinder.route.BlockDestination
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import net.ccbluex.agent.pathfinder.walkAbilities
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Random
import kotlin.test.assertTrue

/**
 * A walk with jumps over gaps followed by a player who walks faster or slower than vanilla, as a speed feature of
 * the host makes it, or who turns slower than the bodies of the library do: it lands every jump and gets to the end.
 *
 * @agent claude-opus-5-5
 */
internal class FollowerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val MAX_TICKS = 600
        private const val ARRIVAL = 0.8
        private val LIMITS = Limits(maxLength = 64.0, maxVisitedNodes = 50_000, maxComputeNanos = Long.MAX_VALUE)
    }

    /**
     * A host that moves [player] as the game does, but walking [speedFactor] times as fast on the ground, and turns it
     * at most [turn] degrees a tick towards where the walk heads.
     *
     * @agent claude-opus-5-5
     */
    private class Host(val player: Player, world: TestWorld, speedFactor: Double, val turn: Float) {
        private val default = MotionTraits.DEFAULT
        val motion = PlayerMotion(
            world.terrain(),
            MotionTraits((default.walkingSpeed * speedFactor).toFloat(), default.jumpStrength, default.jumpBoostPower),
        )

        val position: Vec3 get() = player.position().toAgent()

        init {
            motion.reset(position, PlayerMotion.RESTING, onGround = true)
        }

        fun turnTo(target: Vec3?) {
            val wanted = target?.takeIf { it.horizontalDistanceTo(position) > 0.75 }
                ?.subtract(position)?.yRot ?: return
            player.yRot += Mth.wrapDegrees(wanted - player.yRot).coerceIn(-turn, turn)
        }

        fun move(keys: MovementKeys, sprint: Boolean) {
            val input = keys.directionalInput
            val forward = (if (input.forward) 1 else 0) - (if (input.backward) 1 else 0)
            val strafe = (if (input.left) 1 else 0) - (if (input.right) 1 else 0)
            motion.tick(player.yRot, forward, strafe, keys.jump, sprint, keys.sneak)
            player.setPos(motion.position.toMinecraft())
            player.deltaMovement = motion.deltaMovement.toMinecraft()
            player.setOnGround(motion.onGround)
            player.horizontalCollision = motion.horizontalCollision
            player.isSprinting = motion.isSprinting
            player.pose = if (motion.crouching) Pose.CROUCHING else Pose.STANDING
            player.tickCount++
        }
    }

    /**
     * Follows the route from [start] to [goal] in [world] with [host]; the ticks it took, or fails when the player fell
     * below [floor] or did not get there.
     */
    private fun follow(world: TestWorld, host: Host, start: Vec3, goal: BlockPos, floor: Double): Int {
        val search = RouteSearch(
            world.terrain(), start, BlockDestination(listOf(goal)), walkAbilities(canParkour = true, riskyJumps = true), LIMITS,
        )
        val path = completePath(search.advanceToEnd())
        val body = TestBody(host.player)
        val context = body.context()
        val follower = Follower(context, BlockActions(context))
        follower.follow(path, 0)

        repeat(MAX_TICKS) { tick ->
            val keys = MovementKeys()
            val state = follower.tick(keys, host.player.yRot)
            if (state == FollowState.FINISHED) {
                val end = Vec3.atBottomCenterOf(goal)
                val at = host.position
                assertTrue(at.horizontalDistanceTo(end) <= ARRIVAL, "Ended at $at, not at $end")
                return tick
            }
            assertTrue(state != FollowState.STUCK, "Gave up a step at ${host.position}")
            host.move(keys, follower.sprint)
            host.turnTo(follower.target)
            assertTrue(host.player.y > floor, "Fell at ${host.position}")
        }
        error("Did not get to $goal in $MAX_TICKS ticks, at ${host.position}")
    }

    @Test
    fun `jumps down single blocks take off again right after landing`() {
        // Single blocks two to four apart, each a block lower. Running on to the edge after a landing overshoots the next
        // block and brakes in the air, landing too slow for the jump after it: 59 ticks against 55.
        val world = TestWorld()
        for ((index, x) in listOf(0, 4, 8, 12, 14).withIndex()) {
            world.set(x, 10 - index, 0, TestWorld.STONE)
        }
        val start = Vec3(0.5, 11.0, 0.5)
        val host = Host(TestLevel(world).player(start, -90f), world, 1.0, turn = 180f)

        val ticks = follow(world, host, start, BlockPos(14, 7, 0), floor = 5.0)
        assertTrue(ticks <= 56, "Took $ticks ticks")
    }

    @ParameterizedTest(name = "walking {0} times as fast")
    @ValueSource(doubles = [0.75, 2.5, 3.0])
    fun `a player faster or slower than vanilla lands every jump`(speedFactor: Double) {
        // A platform, two gaps of three blocks with a single block between them, and a platform.
        val world = TestWorld().fill(-4, 0, -1, 0, 0, 1).set(4, 0, 0, TestWorld.STONE).fill(8, 0, -1, 11, 0, 1)
        val start = Vec3(-3.5, 1.0, 0.5)
        val level = TestLevel(world)
        val host = Host(level.player(start, -90f), world, speedFactor, turn = 180f)

        follow(world, host, start, BlockPos(9, 1, 0), floor = 0.5)
    }

    @Test
    fun `a host turning slower than the library's turning still gets its walk followed`() {
        val world = TestWorld.flat()
        val start = Vec3(0.5, 1.0, 0.5)
        val level = TestLevel(world)
        // Facing away from the goal, turning three degrees a tick.
        val host = Host(level.player(start, 180f), world, 1.0, turn = 3f)

        follow(world, host, start, BlockPos(8, 1, 12), floor = 0.5)
    }

    @Test
    fun `a chain of small islands at changing heights is jumped across`() {
        // Fourteen 2x2 islands, gaps of one to three blocks, each up to a block higher or lower than the one before. The
        // run-ups are short, so the walk brakes by sneaking before most jumps.
        val world = TestWorld()
        val random = Random(5)
        var x = 0
        var y = 10
        var z = 0
        repeat(14) { index ->
            val gap = 1 + random.nextInt(3)
            if (index > 0) {
                x += 2 + gap
                z = (z + random.nextInt(3) - 1).coerceIn(-8, 8)
                y = (y + random.nextInt(3) - 1).coerceIn(9, 12)
            }
            world.fill(x, y, z, x + 1, y, z + 1)
        }
        val start = Vec3(0.5, 11.0, 0.5)
        val host = Host(TestLevel(world).player(start, -90f), world, 1.0, turn = 180f)

        follow(world, host, start, BlockPos(x, y + 1, z), floor = 8.0)
    }
}
