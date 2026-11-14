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

private const val GOTO_TICKS = 900
private const val ARRIVAL = 1.1
private const val FLYING_SPEED = 0.05f
private const val FAST_FLYING_SPEED = 0.1f

/**
 * `.goto` flies where walking cannot go: a survival player with an elytra from the top of a tower down to the ground
 * far away, unhurt, and a creative player across a canyon into the void and over a wall wider than the searched
 * area, and back over the wall at twice the flying speed.
 *
 * From LiquidBounce: [FlightNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/FlightNavigationGameTest.kt#L45-L58)
 *
 * @agent claude-opus-5-5
 */
internal val flightNavigation: GameTestScope.() -> Unit = {
    try {
        val y = origin.y
        buildCourse(y)

        checkElytra(y)
        // Last, as a creative player who stops flying in mid-air falls in a way not every anticheat follows
        checkCreative(y)
        checkFastCreative(y)
    } finally {
        context.onClient { AutoWalk.stop() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class FlightNavigationGameTest : ServerGameTest(flightNavigation)

/**
 * A canyon down to the void at x 10..19, a wall twelve blocks high at x 40, a pillar and a tower.
 *
 * From LiquidBounce: [FlightNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/FlightNavigationGameTest.kt#L61-L67)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourse(y: Int) {
    fill(BlockPos(10, y - 4, -30), BlockPos(19, y - 1, 30), "minecraft:air")
    fill(BlockPos(40, y, -30), BlockPos(40, y + 11, 30), "minecraft:stone")
    fill(BlockPos(60, y, 10), BlockPos(62, y + 8, 12), "minecraft:stone")
    fill(BlockPos(0, y, -52), BlockPos(2, y + 39, -50), "minecraft:stone")
    awaitServer("block 2 ${y + 39} -50 minecraft:stone")
}

/**
 * From LiquidBounce: [FlightNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/FlightNavigationGameTest.kt#L69-L77)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkCreative(y: Int) {
    run("gamemode creative $playerName")
    awaitClient("creative mode") { checkNotNull(it.player).abilities.mayfly }
    moveTo(Vec3(4.5, y.toDouble(), 0.5), yRot = -90f)

    check(goto(Vec3(26.5, y.toDouble(), 0.5), "across the canyon")) { "Crossed the canyon without flying" }
    check(goto(Vec3(52.5, y.toDouble(), 0.5), "over the wall")) { "Crossed the wall without flying" }
    check(context.fromClient { checkNotNull(it.player).y } > y - 1) { "Fell into the canyon" }
}

/**
 * A creative player flying twice as fast as by default goes back over the wall and stops where it should.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkFastCreative(y: Int) {
    context.onClient { checkNotNull(it.player).abilities.flyingSpeed = FAST_FLYING_SPEED }
    try {
        check(goto(Vec3(26.5, y.toDouble(), 0.5), "back over the wall fast")) { "Crossed the wall without flying" }
    } finally {
        context.onClient { checkNotNull(it.player).abilities.flyingSpeed = FLYING_SPEED }
    }
}

/**
 * From LiquidBounce: [FlightNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/FlightNavigationGameTest.kt#L102-L122)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkElytra(y: Int) {
    run("item replace entity $playerName armor.chest with minecraft:elytra")
    moveTo(Vec3(1.5, y + 40.0, -50.5), yRot = -90f)

    val destination = Vec3(71.5, y.toDouble(), -50.5)
    unhurt("Gliding down") {
        context.onClient { AutoWalk.goTo(destination) }
        var glided = false
        val took = await(context, GOTO_TICKS, "Did not glide to $destination") {
            val (arrived, gliding) = context.fromClient { mc ->
                !AutoWalk.running to checkNotNull(mc.player).isFallFlying
            }
            glided = glided || gliding
            arrived
        }
        logTicks("Gliding down", took)
        val position = context.playerPosition()
        check(position.distanceTo(destination) <= ARRIVAL) { "The navigation stopped at $position" }
        check(glided) { "Got down from the tower without gliding" }
    }
    run("item replace entity $playerName armor.chest with minecraft:air")
}

/**
 * From LiquidBounce: [FlightNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/FlightNavigationGameTest.kt#L124-L125)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.goto(destination: Vec3, what: String): Boolean =
    goto(context, destination, what, GOTO_TICKS, ARRIVAL) { it.abilities.flying || !it.onGround() }
