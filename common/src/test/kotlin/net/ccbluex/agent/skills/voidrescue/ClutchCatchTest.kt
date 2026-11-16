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

package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sign

/**
 * Falls the clutch has to catch, played tick by tick without keys from the tick the void rescue took over: the look
 * turns as a client body turns it, and a tick clicks the planned block when the rotation sent the tick before points
 * at its face.
 *
 * @agent claude-opus-5-5
 */
class ClutchCatchTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val WIDTH = 0.6
        private const val EYES = 1.62
    }

    /**
     * Whether the fall from [start] with [startMotion], looking along [look], comes to rest on a block in 60 ticks.
     */
    private fun catches(world: TestWorld, start: Vec3, startMotion: Vec3, look: ViewRotation): Boolean {
        val isOpen = { pos: BlockPos -> world.getBlockState(pos).canBeReplaced() }
        val isSolid = { pos: BlockPos -> !isOpen(pos) }
        val turning = Turning.humanPaced(Turning.eased())
        var position = start
        var motion = startMotion
        var sent = look
        repeat(60) {
            val eyes = position.add(0.0, EYES, 0.0).add(motion)
            val landing = Clutch.landingCells(position, motion, WIDTH)
            val placement = if (landing.all(isOpen)) {
                Clutch.nextPlacement(landing, eyes, sent.viewVector, 1L, isOpen, isSolid)
            } else {
                null
            }
            if (placement != null) {
                val cell = placement.cells.filter(isOpen).first { onFace(it, placement.aim) }
                if (crossesFace(position.add(0.0, EYES, 0.0), sent.viewVector, cell, placement.aim)) {
                    world.set(cell.x, cell.y, cell.z, Blocks.STONE.defaultBlockState())
                }
                sent = turning.turn(sent, ViewRotation.fromVector(placement.aim.subtract(eyes)), Clutch.LOOK)
            }

            val top = Mth.floor(position.y)
            if (position.y + motion.y <= top && under(position, top - 1).any(isSolid)) {
                // The ground move comes after the drop, so a box that only clips an edge slides off
                return under(position.add(motion.x, 0.0, motion.z), top - 1).any(isSolid)
            }
            position = position.add(motion)
            motion = Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91)
        }
        return false
    }

    /**
     * The side of [cell] that [aim] lies on, as a unit step.
     */
    private fun side(cell: BlockPos, aim: Vec3): Vec3 {
        val off = aim.subtract(Vec3.atCenterOf(cell))
        val most = maxOf(abs(off.x), abs(off.y), abs(off.z))
        return Vec3(
            if (abs(off.x) == most) sign(off.x) else 0.0,
            if (abs(off.y) == most) sign(off.y) else 0.0,
            if (abs(off.z) == most) sign(off.z) else 0.0,
        )
    }

    private fun onFace(cell: BlockPos, aim: Vec3): Boolean {
        val off = aim.subtract(Vec3.atCenterOf(cell))
        return abs(maxOf(abs(off.x), abs(off.y), abs(off.z)) - 0.5) < 1.0E-6
    }

    /**
     * Whether the ray from [eyes] along [look] crosses the face of [cell] that [aim] lies on.
     */
    private fun crossesFace(eyes: Vec3, look: Vec3, cell: BlockPos, aim: Vec3): Boolean {
        val normal = side(cell, aim)
        val towards = look.dot(normal)
        if (towards <= 0.0) return false
        val hit = eyes.add(look.scale(aim.subtract(eyes).dot(normal) / towards))
        val off = hit.subtract(Vec3.atCenterOf(cell).add(normal.scale(0.5)))
        return abs(off.x) <= 0.5 && abs(off.y) <= 0.5 && abs(off.z) <= 0.5
    }

    private fun under(feet: Vec3, level: Int): List<BlockPos> {
        val half = WIDTH / 2.0
        return (Mth.floor(feet.x - half)..Mth.floor(feet.x + half - 1.0E-7)).flatMap { x ->
            (Mth.floor(feet.z - half)..Mth.floor(feet.z + half - 1.0E-7)).map { z -> BlockPos(x, level, z) }
        }
    }

    /**
     * Rows of `#` from x [west] eastwards, north to south from z [north], one per level from [level] down.
     */
    private fun TestWorld.layers(west: Int, north: Int, level: Int, vararg layers: List<String>) = apply {
        layers.forEachIndexed { depth, rows ->
            rows.forEachIndexed { dz, row ->
                row.forEachIndexed { dx, c ->
                    if (c == '#') fill(west + dx, level - depth, north + dz, west + dx, level - depth, north + dz)
                }
            }
        }
    }

    /**
     * A bot knocked off a BedWars island: the rescue took over, but the eased look clicked first 5 ticks later,
     * when the body was past the cell; each later block went in a level lower and further behind (13 ticks after the
     * rescue at y 62, 15 at y 61), and it fell into the void. The island edge as recorded, x -20..-10.
     */
    @Test
    fun aKnockOffAnIslandIsCaught() {
        val top = listOf(
            "...........", ".........##", ".......####", "......#####", "....#######", "...########",
            "..#########", ".##########", "###########",
        )
        val world = TestWorld().layers(
            -20, -25, 64,
            top,
            top,
            listOf(
                "...........", ".........##", ".......####", "......#####", "....#######", "...########",
                "...########", ".##########", "###########",
            ),
            listOf(
                "...........", "..........#", "........###", ".......####", ".....######", "....#######",
                "...########", "..#########", ".##########",
            ),
        )
        assertTrue(catches(world, Vec3(-13.628, 65.406, -21.256), Vec3(-0.360, 0.319, -0.221), ViewRotation(-81.1f, 0.2f)))
    }

    /**
     * A bot sprinted off a planned drop onto the last block of an island: the rescue took over, the eased look got its
     * pitch to 75 degrees only 3 ticks later and missed the ledge's face after 5, so the box clipped the ledge's corner
     * after 6 and slid off into the void.
     */
    @Test
    fun aSprintedDropPastALedgeIsCaught() {
        val world = TestWorld()
            .fill(0, 75, -47, 2, 75, -44)
            .fill(1, 76, -46, 1, 76, -45)
            .fill(1, 77, -45, 1, 77, -45)
        assertTrue(catches(world, Vec3(1.449, 77.922, -43.401), Vec3(0.007, -0.156, 0.151), ViewRotation(-3.1f, 25.5f)))
    }
}
