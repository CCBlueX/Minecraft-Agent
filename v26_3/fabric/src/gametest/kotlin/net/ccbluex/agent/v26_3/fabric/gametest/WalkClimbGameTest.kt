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
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

private const val COURSE_HEIGHT = 10
private const val TOWER_HEIGHT = 5
private const val CLIMB_TICKS = 400
private const val GOAL_RADIUS = 0.8

/**
 * A survival player climbs a ladder onto a tower too high to drop from, and back down it unhurt. Then it drops into
 * a shaft from the side across from its ladder, as a SkyWars island's chest pit, and climbs down to the room at its
 * foot.
 *
 * From LiquidBounce: [WalkClimbGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkClimbGameTest.kt#L37-L53)
 *
 * @agent claude-opus-5-5
 */
internal val walkClimb: GameTestScope.() -> Unit = {
    lateinit var walker: TestWalker
    context.onClient {
        walker = TestWalker(GOAL_RADIUS)
    }

    try {
        val y = origin.y + COURSE_HEIGHT
        buildCourse(y)
        run("gamerule minecraft:natural_health_regeneration false")
        walker.start(context)

        val ground = Vec3(0.5, y.toDouble(), 0.5)
        val top = Vec3(6.5, (y + TOWER_HEIGHT).toDouble(), 0.5)
        moveTo(ground, yRot = 90f)
        walker.walk(context, top, CLIMB_TICKS, "Climbing the ladder", "Did not climb the ladder")
        unhurt("Climbing down the ladder") {
            walker.walk(context, ground, CLIMB_TICKS, "Climbing down the ladder", "Did not climb down the ladder")
        }

        moveTo(Vec3(14.5, (y + TOWER_HEIGHT).toDouble(), 0.5), yRot = -90f)
        unhurt("Climbing down the shaft") {
            val foot = Vec3(16.5, y.toDouble(), -0.5)
            walker.walk(context, foot, CLIMB_TICKS, "Climbing down the shaft", "Did not climb down the shaft")
        }
    } finally {
        walker.stop(context)
    }
}

/**
 * @agent claude-opus-5-5
 */
public class WalkClimbGameTest : ServerGameTest(walkClimb)

/**
 * A stone tower five blocks high with a ladder up its west side, on a floor in the air. Beside it, a stone block as
 * high with a shaft at x 16 down to a room north of its foot, its ladder on the east wall.
 *
 * From LiquidBounce: [WalkClimbGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkClimbGameTest.kt#L61-L67)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourse(y: Int) {
    fill(BlockPos(-3, y - 1, -4), BlockPos(10, y - 1, 4), "minecraft:stone")
    fill(BlockPos(-3, y, -4), BlockPos(10, y + TOWER_HEIGHT + 3, 4), "minecraft:air")
    fill(BlockPos(4, y, -2), BlockPos(8, y + TOWER_HEIGHT - 1, 2), "minecraft:stone")
    fill(BlockPos(3, y, 0), BlockPos(3, y + TOWER_HEIGHT - 1, 0), "minecraft:ladder[facing=west]")
    fill(BlockPos(12, y - 1, -4), BlockPos(20, y + TOWER_HEIGHT + 3, 4), "minecraft:air")
    fill(BlockPos(14, y - 1, -2), BlockPos(18, y + TOWER_HEIGHT - 1, 2), "minecraft:stone")
    fill(BlockPos(16, y, 0), BlockPos(16, y + TOWER_HEIGHT - 1, 0), "minecraft:ladder[facing=west]")
    fill(BlockPos(16, y, -1), BlockPos(16, y + 1, -1), "minecraft:air")
    awaitServer("block 16 ${y + 1} -1 minecraft:air")
}
