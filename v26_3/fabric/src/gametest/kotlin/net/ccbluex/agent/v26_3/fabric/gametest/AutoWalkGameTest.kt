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
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

private const val DIRECTION_TICKS = 600
private const val DIRECTION_END = 38.0
private const val STILL = 0.02
private const val MAX_STOPS = 3
private const val SPRINT_TICKS = 200
private const val FOLLOW_TICKS = 300
private const val FOLLOW_DISTANCE = 3.5

/**
 * LiquidBounce's AutoWalk on the library: it walks the way the player faces around a wall, to a `.goto` destination
 * around a closed room and up to a `.follow` player.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L65-L80)
 *
 * @agent claude-opus-5-5
 */
internal val autoWalk: GameTestScope.() -> Unit = {
    try {
        val y = origin.y
        buildCourse(y)
        give("minecraft:diamond_pickaxe")
        give("minecraft:cobblestone", 64)

        checkDirection(y)
        checkGoto(y)
        checkSprint(y)
        checkFollow(y)
    } finally {
        context.onClient { AutoWalk.stop() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class AutoWalkGameTest : ServerGameTest(autoWalk)

/**
 * A wall at x 10, a closed stone room at x 22..28 and three floating islands at x 32..34, 40..42 and 48..50 with
 * a bed on the second and a dragon egg on the third. Along z -18..-12 runs an uneven lane: a hill four blocks high
 * with a pillar on top, a ditch, a wall with a copy far above to compare it with, and slabs. The ground floor is
 * stone at y - 1.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L88-L109), [WalkNavigationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/WalkNavigationGameTest.kt#L77-L87)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourse(y: Int) {
    fillArea(BlockPos(0, y, -20), BlockPos(60, y + 14, 20), "minecraft:air")
    fill(BlockPos(0, y - 1, -20), BlockPos(60, y - 1, 20), "minecraft:stone")
    fill(BlockPos(10, y, -6), BlockPos(10, y + 3, 6), "minecraft:stone")
    fill(BlockPos(22, y, -3), BlockPos(28, y + 3, 3), "minecraft:stone hollow")
    fill(BlockPos(32, y + 8, -1), BlockPos(34, y + 8, 1), "minecraft:stone")
    fill(BlockPos(40, y + 8, -1), BlockPos(42, y + 8, 1), "minecraft:stone")
    setBlock(BlockPos(41, y + 9, 0), "minecraft:red_bed[part=foot,facing=east]")
    setBlock(BlockPos(42, y + 9, 0), "minecraft:red_bed[part=head,facing=east]")
    fill(BlockPos(48, y + 8, -1), BlockPos(50, y + 8, 1), "minecraft:stone")
    setBlock(BlockPos(49, y + 9, 0), "minecraft:dragon_egg")

    for (step in 0..3) {
        fill(BlockPos(6 + step, y + step, -18), BlockPos(20 - step, y + step, -12), "minecraft:stone")
    }
    fill(BlockPos(13, y + 4, -16), BlockPos(13, y + 6, -14), "minecraft:stone")
    fill(BlockPos(23, y - 1, -18), BlockPos(24, y - 1, -12), "minecraft:air")
    fill(BlockPos(27, y, -20), BlockPos(27, y + 2, -11), "minecraft:stone")
    fill(BlockPos(27, y + 40, -20), BlockPos(27, y + 42, -11), "minecraft:stone")
    fill(BlockPos(31, y, -18), BlockPos(33, y, -12), "minecraft:stone_slab")
    awaitServer("block 33 $y -12 minecraft:stone_slab")
}

/**
 * Bumping into a stair while jumping up the next is how the game moves; only standing on ground is a stop.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L112-L141), [AutoWalkStagesGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkStagesGameTest.kt#L68-L86)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkDirection(y: Int) {
    moveTo(Vec3(2.5, y.toDouble(), -15.5), yRot = -90f)
    context.onClient { AutoWalk.walkFacing() }

    var stops = 0
    watched("walking the way it faces") {
        var last = context.playerPosition()
        repeat(DIRECTION_TICKS) { tick ->
            ticks(1)
            val (position, onGround) = context.fromClient { mc ->
                val player = checkNotNull(mc.player)
                player.position() to player.onGround()
            }
            if (onGround && position.subtract(last).horizontalDistance() < STILL) {
                stops++
            }
            last = position
            if (position.x > DIRECTION_END) {
                logTicks("AutoWalk walking the way it faces", tick)
                return@watched
            }
        }
        error("Did not walk the lane within $DIRECTION_TICKS ticks, stopped at $last")
    }
    context.onClient { AutoWalk.stop() }
    check(stops <= MAX_STOPS) { "Stood still on the ground for $stops ticks" }
    check(matchingBlocks(BlockPos(27, y, -20), BlockPos(27, y + 2, -11), BlockPos(27, y + 40, -20)) == 10 * 3) {
        "The wall was changed"
    }
}

/**
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L119-L165)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkGoto(y: Int) {
    moveTo(Vec3(18.5, y.toDouble(), 0.5))
    val destination = Vec3(31.5, y.toDouble(), 0.5)
    context.onClient { AutoWalk.goTo(destination) }
    check(context.fromClient { AutoWalk.running }) { "goTo did not start the navigation" }

    watched("going around the room") {
        val took = awaitPlayer(context, 600, "Did not arrive behind the closed room") { !AutoWalk.running }
        logTicks("AutoWalk going around the room", took)
    }
    val position = context.playerPosition()
    check(position.distanceTo(destination) <= 1.1) { "The navigation stopped at $position, not at $destination" }
}

/**
 * Walking away from where the player looks still sprints, as the walk turns the head.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L177-L197)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkSprint(y: Int) {
    moveTo(Vec3(2.5, y.toDouble(), 16.5), yRot = 90f)
    context.onClient { AutoWalk.goTo(Vec3(20.0, y.toDouble(), 16.0)) }

    var elapsed = 0
    var sprinting = 0
    watched("sprinting along") {
        repeat(SPRINT_TICKS) {
            ticks(1)
            elapsed++
            if (context.fromClient { checkNotNull(it.player).isSprinting }) {
                sprinting++
            }
            if (context.fromClient { !AutoWalk.running }) {
                logTicks("AutoWalk sprinting along", elapsed)
                return@watched
            }
        }
        error("Did not arrive within $SPRINT_TICKS ticks, stopped at ${context.playerPosition()}")
    }
    check(sprinting * 2 >= elapsed) { "Sprinted for only $sprinting of $elapsed ticks" }
}

/**
 * `.follow` walks up to a player the tab list does not show.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L200-L211)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.checkFollow(y: Int) {
    val twin = Vec3(16.5, y.toDouble(), 8.5)
    moveTo(twin)
    val id = client { it.spawnTwin("Twin", twin) }
    moveTo(Vec3(2.5, y.toDouble(), 8.5))

    try {
        context.onClient { AutoWalk.follow("Twin") }
        check(context.fromClient { AutoWalk.running }) { "follow did not start the navigation" }
        watched("following") {
            val took = awaitPlayer(context, FOLLOW_TICKS, "Did not walk up to the followed player") {
                it.position().distanceTo(twin) <= FOLLOW_DISTANCE
            }
            logTicks("AutoWalk following", took)
        }
    } finally {
        context.onClient { mc ->
            AutoWalk.stop()
            mc.removeTwin(id)
        }
    }
}

/**
 * Runs [block] unhurt and without glancing back.
 *
 * From LiquidBounce: [AutoWalkGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/AutoWalkGameTest.kt#L291-L295)
 *
 * @agent claude-opus-5-5
 */
private inline fun GameTestScope.watched(
    what: String,
    noinline acting: () -> Boolean = { false },
    crossinline block: () -> Unit,
) = unhurt("AutoWalk $what") { TurnWatch.watch(context, "AutoWalk $what", acting) { block() } }
