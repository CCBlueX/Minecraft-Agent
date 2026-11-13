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
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

private const val PLATFORM_A = 300
private const val PLATFORM_B = 650
private const val PILLAR = 475
private const val LOW = 100
private const val RISE = 10
private const val CRUISE_TICKS = 1600
private const val ARRIVAL = 1.5

/**
 * `.goto` with an elytra over a long distance, unhurt: with rockets between platforms over the void of the End and a
 * pillar in the way.
 *
 * From LiquidBounce: [ElytraTravelGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ElytraTravelGameTest.kt#L52-L62)
 *
 * @agent claude-opus-5-5
 */
internal val elytraTravel: GameTestScope.() -> Unit = {
    try {
        run("item replace entity $playerName armor.chest with minecraft:elytra")
        checkEndCruise()
    } finally {
        context.onClient { AutoWalk.stop() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class ElytraTravelGameTest : ServerGameTest(elytraTravel)

/**
 * Platforms at x 300 and x 650, the second ten blocks higher, with a pillar reaching above both halfway.
 *
 * From LiquidBounce: [ElytraTravelGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ElytraTravelGameTest.kt#L65-L81)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkEndCruise() {
    run("item replace entity $playerName hotbar.0 with minecraft:firework_rocket 64")
    for (x in listOf(PLATFORM_A, PLATFORM_B, PILLAR)) {
        inEnd("forceload add ${x - 4} 296 ${x + 4} 304")
        awaitAnswer("execute in minecraft:the_end if loaded $x $LOW 300", "Test passed")
    }
    inEnd("fill ${PLATFORM_A - 4} $LOW 296 ${PLATFORM_A + 4} $LOW 304 minecraft:end_stone")
    inEnd("fill ${PLATFORM_B - 4} ${LOW + RISE} 296 ${PLATFORM_B + 4} ${LOW + RISE} 304 minecraft:end_stone")
    inEnd("fill $PILLAR ${LOW - 30} 298 ${PILLAR + 2} ${LOW + 40} 302 minecraft:obsidian")
    inEnd("kill @e[type=minecraft:ender_dragon]")
    awaitAnswer("execute in minecraft:the_end if block $PILLAR ${LOW + 40} 300 minecraft:obsidian", "Test passed")

    travel(BlockPos(PLATFORM_A, LOW + 1, 300), yaw = -90f, dimension = Level.END)
    val goal = Vec3(PLATFORM_B + 0.5, LOW + RISE + 1.0, 300.5)
    val glided = elytraGoto(goal, "across the void", CRUISE_TICKS)
    check(glided) { "Crossed the void without flying" }
}

/**
 * From LiquidBounce: [ElytraTravelGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ElytraTravelGameTest.kt#L100-L100)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.inEnd(command: String) = run("execute in minecraft:the_end run $command")

/**
 * From LiquidBounce: [ElytraTravelGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/ElytraTravelGameTest.kt#L102-L105)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.elytraGoto(goal: Vec3, what: String, ticks: Int): Boolean {
    var flew = false
    unhurt("Going $what") {
        flew = goto(context, goal, what, ticks, ARRIVAL) { it.isFallFlying }
    }
    return flew
}
