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

package net.ccbluex.agent.pathfinder

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.pathfinder.TestWorld.Companion.AIR
import net.ccbluex.agent.pathfinder.TestWorld.Companion.STONE
import net.ccbluex.agent.pathfinder.route.BlockDestination
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.walk.Jump
import net.ccbluex.agent.pathfinder.walk.Jumps
import net.ccbluex.agent.pathfinder.walk.Leap
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.v26_3.toMinecraft
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Jumps between single blocks over the void, simulated as the game moves a player.
 *
 * From LiquidBounce: [WalkJumpTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/WalkJumpTest.kt#L48-L263)
 */
class WalkJumpTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val LIMITS = Limits(maxLength = 64.0, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        private const val MAX_LEAP_TICKS = 200
    }

    private fun abilities(riskyJumps: Boolean = true) = walkAbilities(canParkour = true, riskyJumps = riskyJumps)

    /**
     * Whether a jump from a single block at the origin to a single block [dx] [dy] [dz] away works.
     */
    private fun canJump(dx: Int, dy: Int, dz: Int, riskyJumps: Boolean = true): Boolean {
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(dx, dy, dz, dx, dy, dz)
        return Jumps(world.terrain(), abilities(riskyJumps))
            .plan(BlockPos(0, 1, 0), 1.0, BlockPos(dx, dy + 1, dz), dy + 1.0) != null
    }

    private fun route(world: TestWorld, goal: BlockPos): Route {
        val search = RouteSearch(world.terrain(), Vec3(0.5, 1.0, 0.5), BlockDestination(listOf(goal)), abilities(), LIMITS)
        return completePath(search.advanceToEnd(), message = null)
    }

    /**
     * Whether the player, standing at [start], makes the jump from [from] to [to] with [Leap] at the keys and
     * the camera on the target. With [startYRot], it starts facing that way and turns [turnShare] of the way left to
     * the target a tick, as a client body eases into a turn.
     *
     * @agent claude-opus-5-5
     */
    private fun leap(
        world: TestWorld,
        start: Vec3,
        from: BlockPos,
        to: BlockPos,
        startYRot: Float? = null,
        turnShare: Float = 1f,
    ): Boolean {
        val terrain = world.terrain()
        val jumps = Jumps(terrain, abilities())
        val toHeight = terrain.standHeight(to.x, to.y, to.z)
        val jump = jumps.plan(from, terrain.standHeight(from.x, from.y, from.z), to, toHeight) ?: return false
        val feet = Vec3(to.x + 0.5, toHeight, to.z + 0.5)
        val step = Step(
            from, to, Move.PARKOUR, feet, emptyList(), emptyList(), emptyList(), cost = 0.0, jump = jump,
        )
        val state = PlayerMotion(terrain)
        state.reset(start, PlayerMotion.RESTING, onGround = true)
        val leap = Leap()
        var look = feet
        var yRot = startYRot ?: look.subtract(state.x, state.y, state.z).yRot
        repeat(MAX_LEAP_TICKS) {
            val input = leap.decide(state, yRot, step, jump, jumps)
            look = leap.target ?: feet
            state.tick(yRot, input.forward, input.strafe, input.jump, input.sprint, input.shift)
            yRot += Mth.wrapDegrees(look.subtract(state.x, state.y, state.z).yRot - yRot) * turnShare
            if (state.standsOn(to.x, to.y, to.z) || state.standsOn(to.x, to.y - 1, to.z)) {
                return true
            }
            if (state.y < from.y - 1) {
                return false
            }
        }
        return false
    }

    @Test
    fun `a jump rises a block and a quarter and lands after twelve ticks`() {
        val motion = PlayerMotion(TestWorld.flat().terrain())
        motion.reset(Vec3(0.5, 1.0, 0.5), PlayerMotion.RESTING, onGround = true)

        var peak = motion.y
        var ticks = 0
        do {
            motion.tick(0f, 0, 0, jump = ticks == 0, sprint = false)
            peak = maxOf(peak, motion.y)
            ticks++
        } while (!motion.onGround)

        assertEquals(1.2522, peak - 1.0, 1.0E-4)
        assertEquals(12, ticks)
    }

    @Test
    fun `sprinting levels off at 5,612 blocks a second`() {
        val motion = PlayerMotion(TestWorld.flat().terrain())
        motion.reset(Vec3(0.5, 1.0, -30.5), PlayerMotion.RESTING, onGround = true)
        repeat(30) { motion.tick(0f, 1, 0, jump = false, sprint = true) }

        val before = motion.z
        motion.tick(0f, 1, 0, jump = false, sprint = true)
        assertEquals(5.612, (motion.z - before) * 20, 1.0E-3)
    }

    @Test
    fun `gaps of up to four blocks are jumped, five are not`() {
        for (gap in 1..4) {
            assertTrue(canJump(gap + 1, 0, 0), "a gap of $gap")
        }
        assertTrue(!canJump(6, 0, 0), "a gap of five from a single block")
    }

    @Test
    fun `a gap of four is run at from behind the middle of the block`() {
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(5, 0, 0, 5, 0, 0)
        val jump = Jumps(world.terrain(), abilities()).plan(BlockPos(0, 1, 0), 1.0, BlockPos(5, 1, 0), 1.0)

        assertNotNull(jump)
        assertTrue(jump.runUp.x < 0.5, "the run starts at ${jump.runUp}")
    }

    @Test
    fun `jumps reach up one block, never two`() {
        assertTrue(canJump(2, 1, 0))
        assertTrue(canJump(4, 1, 0))
        assertTrue(!canJump(5, 1, 0))
        assertTrue(!canJump(2, 2, 0))
    }

    @Test
    fun `jumps down reach further`() {
        assertTrue(canJump(6, -2, 0))
        assertTrue(!canJump(6, 0, 0))
    }

    @Test
    fun `odd angles and diagonals are jumped`() {
        assertTrue(canJump(3, 0, 1))
        assertTrue(canJump(3, 0, 3))
        assertTrue(canJump(1, 1, 1))
    }

    @Test
    fun `a ceiling over the gap cuts the jump short`() {
        fun plan(gap: Int, ceiling: Boolean): Jump? {
            val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(gap + 1, 0, 0, gap + 1, 0, 0)
            if (ceiling) {
                world.fill(1, 3, 0, gap, 3, 0)
            }
            return Jumps(world.terrain(), abilities()).plan(BlockPos(0, 1, 0), 1.0, BlockPos(gap + 1, 1, 0), 1.0)
        }

        assertNotNull(plan(3, ceiling = false))
        assertNull(plan(3, ceiling = true))
        assertNotNull(plan(1, ceiling = true))
    }

    @Test
    fun `jumps over deadly drops need risky jumps`() {
        assertTrue(!canJump(3, 0, 0, riskyJumps = false))

        // The same gap with ground two blocks down: a miss is survived.
        val world = TestWorld().fill(-2, -2, -2, 5, -2, 2).fill(0, 0, 0, 0, 0, 0).fill(3, 0, 0, 3, 0, 0)
        val jumps = Jumps(world.terrain(), abilities(riskyJumps = false))
        assertNotNull(jumps.plan(BlockPos(0, 1, 0), 1.0, BlockPos(3, 1, 0), 1.0))
    }

    @Test
    fun `a short jump down from a wall holds back in the air`() {
        val wall = Blocks.COBBLESTONE_WALL.defaultBlockState()
        val world = TestWorld().set(0, 0, 0, wall).fill(2, 0, 0, 2, 0, 0)

        // A plain sprint jump from the wall's top overshoots the block two away.
        val jump = Jumps(world.terrain(), abilities()).plan(BlockPos(0, 1, 0), 1.5, BlockPos(2, 1, 0), 1.0)
        assertNotNull(jump)
    }

    @Test
    fun `a jump crosses a dip onto ground walking down into it does not get back up from`() {
        val world = TestWorld().fill(-2, -1, -2, 4, -1, 2).fill(-2, 0, 0, 0, 0, 0).fill(2, 0, 0, 2, 1, 0)
        val path = route(world, BlockPos(2, 2, 0))
        assertEquals(Move.PARKOUR, path.steps.last().move)
    }

    @Test
    fun `a jump started beside its run-up still lands`() {
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(4, 0, 0, 4, 0, 0)
        for (start in listOf(Vec3(0.5, 1.0, 0.5), Vec3(0.37, 1.0, 0.42), Vec3(0.7, 1.0, 0.65))) {
            assertTrue(leap(world, start, BlockPos(0, 1, 0), BlockPos(4, 1, 0)), "no landing from $start")
        }
    }

    @Test
    fun `a diagonal jump started facing away is turned to before the run`() {
        // The first jump of the diagonal parkour game test, facing along +z as the player is put there.
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(4, 0, 3, 4, 0, 3)
        val start = Vec3(0.5, 1.0, 0.5)
        assertTrue(leap(world, start, BlockPos(0, 1, 0), BlockPos(4, 1, 3), startYRot = 0f, turnShare = 0.45f))
    }

    @Test
    fun `a jump into a hole in a wall is run at to the end`() {
        // A wall along z = 3 with a hole one block wide and two high, entered at an angle and a block up.
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(-3, 1, 3, 5, 4, 3)
            .fill(1, 2, 3, 1, 3, 3, AIR).fill(-3, 1, 4, 5, 1, 6)
        assertTrue(leap(world, Vec3(0.5, 1.0, 0.5), BlockPos(0, 1, 0), BlockPos(1, 2, 3)))
    }

    @Test
    fun `a jump lands on top of a lone glass pane`() {
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).set(3, 0, 0, Blocks.GLASS_PANE.defaultBlockState())
        assertTrue(leap(world, Vec3(0.5, 1.0, 0.5), BlockPos(0, 1, 0), BlockPos(3, 1, 0)))
    }

    @Test
    fun `a ladder across a gap is caught and climbed`() {
        val ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST.toMinecraft())
        val world = TestWorld().fill(0, 0, 0, 0, 0, 0).fill(4, 1, 0, 4, 6, 0).fill(4, 6, 0, 6, 6, 0)
            .fill(3, 1, 0, 3, 6, 0, ladder)
        val goal = BlockPos(5, 7, 0)

        val path = route(world, goal)
        val first = path.steps.first()
        assertEquals(Move.PARKOUR, first.move)
        assertEquals(3, first.to.x)
        assertTrue(path.steps.any { it.move == Move.CLIMB_UP })
    }

    @Test
    fun `a course of single blocks is crossed by jumps`() {
        val world = TestWorld()
        val course = listOf(
            BlockPos(0, 0, 0), BlockPos(2, 0, 0), BlockPos(5, 0, 0), BlockPos(7, 1, 1), BlockPos(9, 1, 4),
            BlockPos(11, 0, 6), BlockPos(14, 0, 6),
        )
        course.forEach { world.set(it.x, it.y, it.z, STONE) }
        val goal = course.last().above()

        val path = route(world, goal)
        val landings = path.steps.map { it.to }
        assertTrue(landings.all { it.below() in course }, "the path leaves the course: $landings")
        assertTrue(path.steps.all { it.move == Move.PARKOUR && it.jump != null })
    }

}
