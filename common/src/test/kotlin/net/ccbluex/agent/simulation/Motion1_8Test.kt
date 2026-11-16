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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.EmptyBlockGetter
import net.ccbluex.agent.world.Mechanics
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.VoxelShape
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_JUMP_POWER
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * [PlayerMotion] with [Mechanics.V1_8] replays server ticks a 1.8.8 server recorded of a bot body driven by
 * keys: every position and motion within [TOLERANCE] per axis, tick after tick, from the first recorded state.
 *
 * @agent claude-opus-5-5
 */
class Motion1_8Test {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val AIR = Blocks.AIR.defaultBlockState().toAgent()
        private const val TOLERANCE = 1.0E-6
        private val TRAITS = MotionTraits(0.1f, BASE_JUMP_POWER, 0f, Mechanics.V1_8)
    }

    /**
     * A flat world (grass top at y 3), a row of bottom slabs and one of stone across the walk south from x 57 z 486.
     */
    @Test
    fun flatCourse() {
        val grid = Grid()
            .fill(40, 3, 470, 75, 3, 520, Blocks.GRASS_BLOCK.defaultBlockState().toAgent())
            .fill(54, 4, 496, 60, 4, 496, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).toAgent())
            .fill(54, 4, 504, 60, 4, 504, Blocks.STONE.defaultBlockState().toAgent())
        val worst = replay(grid, "flat-course")
        assertTrue(worst <= TOLERANCE, "largest error $worst")
    }

    /**
     * Replays the trace and returns the largest error of position and motion on any axis.
     */
    private fun replay(grid: Grid, name: String): Double {
        val ticks = checkNotNull(javaClass.getResourceAsStream("/traces/$name.jsonl")).bufferedReader().readLines()
            .map(::parse)
        // From the first tick on the ground, which the spawn fall ends in
        val start = ticks.indexOfFirst { it.onGround }
        val motion = PlayerMotion(grid, TRAITS)
        val first = ticks[start]
        motion.reset(first.position, first.motion, first.onGround, first.sprint)
        var worst = 0.0
        for (tick in ticks.drop(start + 1)) {
            motion.tick(tick.yaw, tick.forward, tick.strafe, tick.jump, tick.sprint, tick.sneak)
            val error = maxOf(
                maxOf(abs(motion.x - tick.position.x), abs(motion.y - tick.position.y), abs(motion.z - tick.position.z)),
                maxOf(abs(motion.deltaX - tick.motion.x), abs(motion.deltaY - tick.motion.y), abs(motion.deltaZ - tick.motion.z)),
            )
            check(error <= TOLERANCE) { "tick ${tick.tick}: error $error, ${motion.position} ${motion.deltaMovement} vs $tick" }
            worst = maxOf(worst, error)
        }
        println("$name: ${ticks.size - start - 1} ticks, largest error $worst")
        return worst
    }

    private data class Tick(
        val tick: Int,
        val forward: Int,
        val strafe: Int,
        val jump: Boolean,
        val sprint: Boolean,
        val sneak: Boolean,
        val yaw: Float,
        val position: Vec3,
        val motion: Vec3,
        val onGround: Boolean,
    )

    private fun parse(line: String): Tick {
        val fields = Regex("\"(\\w+)\":([^,}]+)").findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
        fun number(key: String) = fields.getValue(key).toDouble()
        fun flag(key: String) = fields.getValue(key).toBoolean()
        return Tick(
            number("tick").toInt(), number("forward").toInt(), number("strafe").toInt(), flag("jump"), flag("sprint"),
            flag("sneak"), number("yaw").toFloat(), Vec3(number("x"), number("y"), number("z")),
            Vec3(number("motX"), number("motY"), number("motZ")), flag("onGround"),
        )
    }

    private class Grid : BlockGrid {

        private val blocks = HashMap<BlockPos, BlockState>()

        fun fill(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, state: BlockState) = apply {
            for (pos in BlockPos.betweenClosed(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2))) {
                blocks[pos.immutable()] = state
            }
        }

        override fun state(x: Int, y: Int, z: Int): BlockState = blocks[BlockPos(x, y, z)] ?: AIR

        override fun shape(x: Int, y: Int, z: Int): VoxelShape =
            state(x, y, z).getCollisionShape(EmptyBlockGetter, BlockPos(x, y, z))
    }
}
