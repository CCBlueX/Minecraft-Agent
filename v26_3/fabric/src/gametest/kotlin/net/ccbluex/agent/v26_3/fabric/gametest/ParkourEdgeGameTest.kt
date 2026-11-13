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

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

private const val HEIGHT = 24
private const val TIMEOUT = 400

/**
 * Along +X: a run-up, gaps of one, two and three blocks and one down; each landing fits a sprint jump off its edge.
 */
private val COURSE = listOf(
    BlockPos(0, 0, 0), BlockPos(1, 0, 0), BlockPos(3, 0, 0), BlockPos(4, 0, 0), BlockPos(5, 0, 0),
    BlockPos(8, 0, 0), BlockPos(9, 0, 0), BlockPos(13, 0, 0), BlockPos(14, 0, 0), BlockPos(17, -1, 0),
    BlockPos(18, -1, 0), BlockPos(19, -1, 0),
)

/**
 * With the assist off, [Parkour] still jumps at the very edge: holding forward and sprint gets across gaps over a
 * lake.
 *
 * From LiquidBounce: [ParkourEdgeGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ParkourEdgeGameTest.kt#L44-L74), [ParkourAssistGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ParkourAssistGameTest.kt#L91-L147), [ParkourAssistGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ParkourAssistGameTest.kt#L153-L175)
 *
 * @agent claude-opus-5-5
 */
internal val parkourEdge: GameTestScope.() -> Unit = {
    try {
        val ground = origin.y
        val start = BlockPos(0, ground + HEIGHT, 0)
        run("effect give $playerName minecraft:saturation infinite 0 true")
        fill(BlockPos(-10, ground - 2, -10), BlockPos(30, ground - 1, 10), "minecraft:water")
        for (block in COURSE) {
            setBlock(start.offset(block), "minecraft:stone")
        }
        val last = start.offset(COURSE.last())
        awaitServer("block ${last.x} ${last.y} ${last.z} minecraft:stone")

        val goal = last.above()
        moveTo(Vec3.atBottomCenterOf(start.above()), yRot = -90f)
        Parkour.enable(context, assist = false)
        input.holdKey { it.keyUp }
        input.holdKey { it.keySprint }
        val failure = try {
            runCourse(context, "Edge jumps", "EDGE", start.y + COURSE.minOf { it.y }, TIMEOUT) {
                BlockPos.containing(it) == goal
            }
        } finally {
            input.releaseKey { it.keyUp }
            input.releaseKey { it.keySprint }
        }
        check(failure == null) { "With Assist off the run failed: $failure" }
    } finally {
        Parkour.disable(context)
    }
}

/**
 * @agent claude-opus-5-5
 */
public class ParkourEdgeGameTest : ServerGameTest(parkourEdge)
