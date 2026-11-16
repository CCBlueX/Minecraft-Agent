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

import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Progress
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.setBlock
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import net.ccbluex.agent.math.BlockPos as AgentBlockPos
import net.ccbluex.agent.math.Vec3 as AgentVec3

private const val GOAL_RADIUS = 0.8

/**
 * A survival player walks through a course with the walking navigation, unhurt: around a wall, into a closed room,
 * across a gap, up a cliff, over soul sand and ice and through a door and a fence gate.
 *
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L45-L69)
 *
 * @agent claude-opus-5-5
 */
internal val walkNavigation: GameTestScope.() -> Unit = {
    lateinit var walker: TestWalker
    lateinit var builder: TestWalker
    val routeEvents = RouteEvents()
    context.onClient {
        walker = TestWalker(GOAL_RADIUS, listener = routeEvents)
        builder = TestWalker(GOAL_RADIUS, build = true)
    }

    try {
        val y = origin.y
        buildCourse(y)
        give("minecraft:diamond_pickaxe")
        give("minecraft:cobblestone", 64)
        walker.start(context)
        builder.start(context)

        checkDetour(walker, routeEvents, y)
        checkTunnel(builder, y)
        checkBridge(builder, y)
        checkPillar(builder, y)
        checkSurfaces(walker, y)
        checkDoors(walker, y)
    } finally {
        walker.stop(context)
        builder.stop(context)
    }
}

/**
 * @agent claude-opus-5-5
 */
public class WalkNavigationGameTest : ServerGameTest(walkNavigation)

/**
 * A wall at x 10 with a copy of it far below the course to compare it with, a closed stone room at x 22..28, two
 * floating islands at x 32..34 and 40..42, and a bedrock cliff at x 46..50. The ground floor is stone at y - 1.
 *
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L77-L87), [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L88-L109)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourse(y: Int) {
    fillArea(BlockPos(0, y, -20), BlockPos(60, y + 14, 20), "minecraft:air")
    fill(BlockPos(0, y - 1, -20), BlockPos(60, y - 1, 20), "minecraft:stone")
    fill(BlockPos(10, y, -6), BlockPos(10, y + 3, 6), "minecraft:stone")
    fill(BlockPos(10, y + 40, -6), BlockPos(10, y + 43, 6), "minecraft:stone")
    fill(BlockPos(22, y, -3), BlockPos(28, y + 3, 3), "minecraft:stone hollow")
    fill(BlockPos(32, y + 8, -1), BlockPos(34, y + 8, 1), "minecraft:stone")
    fill(BlockPos(40, y + 8, -1), BlockPos(42, y + 8, 1), "minecraft:stone")
    fill(BlockPos(46, y, 0), BlockPos(50, y + 2, 4), "minecraft:bedrock")
    awaitServer("block 50 ${y + 2} 4 minecraft:bedrock")
}

/**
 * The walk around the wall also checks what a renderer hears of it: the search, the path found, then the route
 * followed step by step, and the route cleared once the goal is gone.
 *
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L89-L95)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkDetour(walker: TestWalker, routeEvents: RouteEvents, y: Int) {
    moveTo(Vec3(4.5, y.toDouble(), 0.5))
    routeEvents.clear()
    walk(walker, Vec3(16.5, y.toDouble(), 0.5), 400, "around the wall")
    check(matchingBlocks(BlockPos(10, y, -6), BlockPos(10, y + 3, 6), BlockPos(10, y + 40, -6)) == 13 * 4) {
        "The wall was changed"
    }

    await(context, 20, "The route was not cleared") { routeEvents.lastOrNull() == "cleared" }
    var from = 0
    for (event in listOf("searching", "routeFound", "following", "stepReached", "cleared")) {
        val at = routeEvents.subList(from, routeEvents.size).indexOf(event)
        check(at >= 0) { "No $event after ${routeEvents.take(from)} in $routeEvents" }
        from += at + 1
    }
}

/**
 * The names of the events a navigator reported, in order.
 *
 * @agent claude-opus-5-5
 */
private class RouteEvents : Listener, MutableList<String> by CopyOnWriteArrayList<String>() {
    override fun routeSearching(start: AgentVec3, goal: AgentVec3, radius: Double, preview: List<AgentVec3>?) {
        add("searching")
    }

    override fun routeFound(path: Route) {
        add("routeFound")
    }

    override fun routeFollowing(points: List<AgentVec3>, progress: Progress) {
        add("following")
    }

    override fun stepReached(index: Int, pos: AgentBlockPos) {
        add("stepReached")
    }

    override fun routeDone() {
        add("done")
    }

    override fun routeInvalid() {
        add("invalid")
    }

    override fun routeCleared() {
        add("cleared")
    }
}

/**
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L97-L100)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkTunnel(builder: TestWalker, y: Int) {
    moveTo(Vec3(18.5, y.toDouble(), 0.5))
    walk(builder, Vec3(25.5, y + 1.0, 0.5), 600, "into the closed room")
}

/**
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L102-L107)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkBridge(builder: TestWalker, y: Int) {
    moveTo(Vec3(33.5, y + 9.0, 0.5))
    val before = context.cobblestone()
    walk(builder, Vec3(41.5, y + 9.0, 0.5), 800, "across the gap")
    check(context.cobblestone() < before) { "Crossed the gap without placing a block" }
}

/**
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L109-L114)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkPillar(builder: TestWalker, y: Int) {
    moveTo(Vec3(44.5, y.toDouble(), 2.5))
    val before = context.cobblestone()
    walk(builder, Vec3(48.5, y + 3.0, 2.5), 800, "up the cliff")
    check(context.cobblestone() < before) { "Climbed the cliff without pillaring" }
}

/**
 * A roofed corridor in stone: east over soul sand, then north over ice.
 *
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L117-L126)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkSurfaces(walker: TestWalker, y: Int) {
    fill(BlockPos(0, y - 1, 24), BlockPos(16, y + 3, 36), "minecraft:stone")
    fill(BlockPos(1, y, 25), BlockPos(15, y + 2, 27), "minecraft:air")
    fill(BlockPos(13, y, 25), BlockPos(15, y + 2, 35), "minecraft:air")
    fill(BlockPos(3, y - 1, 25), BlockPos(11, y - 1, 27), "minecraft:soul_sand")
    fill(BlockPos(13, y - 1, 25), BlockPos(15, y - 1, 35), "minecraft:ice")

    moveTo(Vec3(1.5, y.toDouble(), 26.5))
    walk(walker, Vec3(14.5, y.toDouble(), 34.5), 600, "over soul sand and ice")
}

/**
 * A roofed corridor in stone, closed by walls with an oak door at x 26 and an oak fence gate at x 32.
 *
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L129-L143)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkDoors(walker: TestWalker, y: Int) {
    fill(BlockPos(20, y - 1, 28), BlockPos(40, y + 3, 32), "minecraft:stone")
    fill(BlockPos(21, y, 29), BlockPos(39, y + 2, 31), "minecraft:air")
    fill(BlockPos(26, y, 29), BlockPos(26, y + 2, 31), "minecraft:stone")
    fill(BlockPos(32, y, 29), BlockPos(32, y + 2, 31), "minecraft:stone")
    setBlock(BlockPos(26, y, 30), "minecraft:oak_door[facing=east,half=lower]")
    setBlock(BlockPos(26, y + 1, 30), "minecraft:oak_door[facing=east,half=upper]")
    setBlock(BlockPos(32, y, 30), "minecraft:oak_fence_gate[facing=east]")
    setBlock(BlockPos(32, y + 1, 30), "minecraft:air")

    moveTo(Vec3(22.5, y.toDouble(), 30.5))
    walk(walker, Vec3(37.5, y.toDouble(), 30.5), 600, "through a door and a fence gate")
    awaitServer("block 26 $y 30 minecraft:oak_door[open=true]")
    awaitServer("block 32 $y 30 minecraft:oak_fence_gate[open=true]")
}

/**
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L145-L147)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.walk(walker: TestWalker, goal: Vec3, ticks: Int, what: String) = unhurt("Walking $what") {
    walker.walk(context, goal, ticks, "Walking $what", "Did not walk $what", acting = { walker.isActing })
}

/**
 * From LiquidBounce: [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L149-L152)
 *
 * @agent claude-opus-5-5
 */
private fun ClientGameTestContext.cobblestone(): Int = fromClient { mc ->
    val inventory = checkNotNull(mc.player).inventory
    (0 until inventory.containerSize).sumOf { slot ->
        inventory.getItem(slot).takeIf { it.`is`(Items.COBBLESTONE) }?.count ?: 0
    }
}
