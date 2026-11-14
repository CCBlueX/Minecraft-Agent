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
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.phys.Vec3

private const val BOAT_TICKS = 1200
private const val HORSE_TICKS = 900
private const val SHORE_X = 30.0
private const val ARRIVAL = 1.1

/**
 * `.goto` rides an oak boat around an island across a lake, gets off at the shore and walks onto the destination,
 * and rides a saddled horse up a step, around a fenced wall and up a ledge it jumps, staying on the horse.
 *
 * @agent claude-opus-5-5
 */
internal val rideNavigation: GameTestScope.() -> Unit = {
    try {
        val y = origin.y
        buildLake(y)
        buildField(y)

        checkBoat(y)
        checkHorse(y)
    } finally {
        context.onClient { AutoWalk.stop() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class RideNavigationGameTest : ServerGameTest(rideNavigation)

/**
 * A lake one block deep from x 4 to 34 with a stone island at x 16..22, in grass ground at y - 1.
 *
 * From LiquidBounce: [RideNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/RideNavigationGameTest.kt#L56-L62)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildLake(y: Int) {
    fill(BlockPos(0, y - 3, -14), BlockPos(50, y - 2, 14), "minecraft:stone")
    fill(BlockPos(0, y - 1, -14), BlockPos(50, y - 1, 14), "minecraft:grass_block")
    fill(BlockPos(0, y, -14), BlockPos(50, y + 6, 14), "minecraft:air")
    fill(BlockPos(4, y - 1, -10), BlockPos(34, y - 1, 10), "minecraft:water")
    fill(BlockPos(16, y - 1, -4), BlockPos(22, y + 1, 4), "minecraft:stone")
}

/**
 * Grass ground with a stone floor one block higher from x 18 on, a fenced wall across it at x 28 from z 36 on,
 * and a ledge two blocks higher from x 38 on.
 *
 * From LiquidBounce: [RideNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/RideNavigationGameTest.kt#L68-L76)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildField(y: Int) {
    fill(BlockPos(0, y - 1, 26), BlockPos(50, y - 1, 64), "minecraft:grass_block")
    fill(BlockPos(0, y, 26), BlockPos(50, y + 8, 64), "minecraft:air")
    fill(BlockPos(18, y, 30), BlockPos(50, y, 60), "minecraft:stone")
    fill(BlockPos(28, y + 1, 36), BlockPos(28, y + 1, 60), "minecraft:stone")
    fill(BlockPos(28, y + 2, 36), BlockPos(28, y + 2, 60), "minecraft:oak_fence")
    fill(BlockPos(38, y + 1, 30), BlockPos(50, y + 2, 60), "minecraft:stone")
    awaitServer("block 50 ${y + 2} 60 minecraft:stone")
}

/**
 * From LiquidBounce: [RideNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/RideNavigationGameTest.kt#L78-L106)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkBoat(y: Int) {
    moveTo(Vec3(8.5, y.toDouble(), 0.5), yRot = -90f)
    run("summon minecraft:oak_boat 8.5 ${y - 0.4} 0.5 {Rotation:[-90f,0f]}")
    mount("minecraft:oak_boat")

    val destination = Vec3(42.5, y.toDouble(), 0.5)
    var farthestAfloat = Double.NEGATIVE_INFINITY
    unhurt("Riding the boat") {
        context.onClient { AutoWalk.goTo(Vec3(42.0, y.toDouble(), 0.0)) }
        check(context.fromClient { AutoWalk.running }) { "goTo did not start the navigation" }

        val took = awaitPlayer(context, BOAT_TICKS, "Did not arrive across the lake") { player ->
            val boat = player.vehicle as? AbstractBoat
            if (boat != null) {
                farthestAfloat = maxOf(farthestAfloat, boat.x)
            }
            !AutoWalk.running
        }
        logTicks("Riding the boat", took)
    }

    val (position, riding) = client { mc ->
        val player = checkNotNull(mc.player)
        player.position() to player.isPassenger
    }
    check(farthestAfloat >= SHORE_X) { "The boat only got to x $farthestAfloat" }
    check(!riding) { "The player stayed in the boat" }
    check(position.distanceTo(destination) <= ARRIVAL) { "The navigation stopped at $position, not at $destination" }
    run("kill @e[type=minecraft:oak_boat]")
}

/**
 * From LiquidBounce: [RideNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/RideNavigationGameTest.kt#L78-L133)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkHorse(y: Int) {
    moveTo(Vec3(6.5, y.toDouble(), 50.5), yRot = -90f)
    run(
        "summon minecraft:horse 6.5 $y 50.5 {Tame:1b,PersistenceRequired:1b,Rotation:[-90f,0f]," +
            "equipment:{saddle:{id:\"minecraft:saddle\",count:1}}," +
            "attributes:[{id:\"minecraft:jump_strength\",base:0.6d},{id:\"minecraft:movement_speed\",base:0.25d}]}"
    )
    mount("minecraft:horse")
    check(client { checkNotNull(it.player).controlledVehicle is Horse }) { "The player does not control the horse" }

    val destination = Vec3(44.5, y + 3.0, 50.5)
    unhurt("Riding the horse") {
        context.onClient { AutoWalk.goTo(Vec3(44.0, y + 3.0, 50.0)) }
        check(context.fromClient { AutoWalk.running }) { "goTo did not start the navigation" }
        val took = awaitPlayer(context, HORSE_TICKS, "Did not ride up to the ledge") { !AutoWalk.running }
        logTicks("Riding the horse", took)
    }

    val (position, riding) = client { mc ->
        val player = checkNotNull(mc.player)
        player.rootVehicle.position() to (player.vehicle is Horse)
    }
    check(riding) { "The player got off the horse" }
    check(position.distanceTo(destination) <= ARRIVAL) { "The navigation stopped at $position, not at $destination" }
    run("ride $playerName dismount")
    run("kill @e[type=minecraft:horse]")
}

/**
 * From LiquidBounce: [RideNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/RideNavigationGameTest.kt#L135-L139)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.mount(type: String) {
    run("ride $playerName mount @e[type=$type,limit=1,sort=nearest]")
    awaitClient("the player on the $type") { it.player?.isPassenger == true }
    ticks(10)
}
