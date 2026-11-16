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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.GameTestScope.Companion.DEFAULT_TIMEOUT
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.query
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.abs

/**
 * From LiquidBounce: [ServerQueries.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/ServerQueries.kt#L30-L34)
 */
internal val logger: Logger = LoggerFactory.getLogger("Agent/GameTest")

private val HEALTH = Regex("""(-?[\d.]+)f$""")

// The session's max_block_modifications
private const val FILL_LIMIT = 32768
private const val NEAR_SQR = 1.0E-4

/**
 * @agent claude-opus-5-5
 */
internal fun ClientGameTestContext.onClient(block: (Minecraft) -> Unit) = runOnClient<RuntimeException> { block(it) }

/**
 * @agent claude-opus-5-5
 */
internal fun <T> ClientGameTestContext.fromClient(block: (Minecraft) -> T): T =
    computeOnClient<T, RuntimeException> { block(it) }

/**
 * @agent claude-opus-5-5
 */
internal fun ClientGameTestContext.playerPosition(): Vec3 = fromClient { checkNotNull(it.player).position() }

/**
 * The player's health as the server has it.
 *
 * From LiquidBounce: [ServerQueries.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/ServerQueries.kt#L37-L37)
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.health() = query("data get entity $playerName Health", HEALTH).groupValues[1].toFloat()

/**
 * Keeps the chunks from [from] to [to] loaded, which a fill far from the player needs, and waits for them.
 *
 * From LiquidBounce: [ServerQueries.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/ServerQueries.kt#L48-L52)
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.forceLoadArea(from: BlockPos, to: BlockPos) {
    run("forceload add ${from.x} ${from.z} ${to.x} ${to.z}")
    awaitServer("loaded ${from.x} ${from.y} ${from.z}")
    awaitServer("loaded ${to.x} ${to.y} ${to.z}")
}

/**
 * Fills like `fill`, in slices along x small enough for the session's block limit.
 *
 * From LiquidBounce: [ServerQueries.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/ServerQueries.kt#L55-L60)
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.fillArea(from: BlockPos, to: BlockPos, block: String) {
    val minX = minOf(from.x, to.x)
    val maxX = maxOf(from.x, to.x)
    val slice = maxOf(1, FILL_LIMIT / ((abs(to.y - from.y) + 1) * (abs(to.z - from.z) + 1)))
    for (x in minX..maxX step slice) {
        fill(BlockPos(x, from.y, from.z), BlockPos(minOf(x + slice - 1, maxX), to.y, to.z), block)
    }
}

/**
 * How many blocks between [from] and [to] the server has the same as [reference], a region of the same size.
 *
 * From LiquidBounce: [ServerQueries.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/ServerQueries.kt#L65-L74)
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.matchingBlocks(from: BlockPos, to: BlockPos, reference: BlockPos): Int {
    val command = "execute if blocks ${from.x} ${from.y} ${from.z} ${to.x} ${to.y} ${to.z} " +
        "${reference.x} ${reference.y} ${reference.z} all"
    val id = run(command)
    repeat(DEFAULT_TIMEOUT) {
        server.result(id)?.let { return it }
        ticks(1)
    }
    throw AssertionError("The server never finished '$command', see ${server.log}")
}

/**
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L145-L146)
 *
 * @agent claude-fable-5-2
 */
internal fun Vec3.arrivedAt(goal: Vec3, radius: Double) =
    toAgent().horizontalDistanceTo(goal.toAgent()) <= radius + 0.1 && abs(y - goal.y) < 1.0

/**
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L148-L148)
 *
 * @agent claude-fable-5-2
 */
internal fun Vec3.near(other: Vec3, toleranceSqr: Double = NEAR_SQR) = distanceToSqr(other) < toleranceSqr

/**
 * Teleports the player to [position], facing [yRot], and waits until it stands there.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L151-L151)
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.moveTo(position: Vec3, yRot: Float = 0f) = travel(BlockPos.containing(position), yRot)

/**
 * Runs [block] and fails if it cost the player health on the server.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L154-L159)
 *
 * @agent claude-opus-5-5
 */
internal inline fun GameTestScope.unhurt(what: String, block: () -> Unit) {
    val before = health()
    block()
    val after = health()
    check(after >= before) { "$what: hurt the player from $before to $after" }
}

/**
 * Waits up to [ticks] for [condition] and fails with [failure] and where the player stopped; the ticks it waited.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L165-L173)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal inline fun await(context: ClientGameTestContext, ticks: Int, failure: String, condition: () -> Boolean): Int {
    repeat(ticks) { tick ->
        if (condition()) {
            return tick
        }
        context.waitTick()
    }
    error("$failure within $ticks ticks, stopped at ${context.playerPosition()}")
}

/**
 * Logs how many ticks [what] took to reach its goal, the number the speed of the walks is compared by.
 *
 * @agent claude-opus-5-5
 */
internal fun logTicks(what: String, ticks: Int) = logger.info("TICKS {}: {}", what, ticks)

/**
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L175-L180)
 *
 * @agent claude-fable-5-2
 */
internal fun awaitPlayer(
    context: ClientGameTestContext,
    ticks: Int,
    failure: String,
    condition: (LocalPlayer) -> Boolean,
) = await(context, ticks, failure) { context.fromClient { condition(checkNotNull(it.player)) } }

/**
 * Watches a run for [timeout] ticks until [arrived] or a fall below [lowest]; why it failed, or null.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L183-L204)
 *
 * @agent claude-fable-5-2
 */
internal fun runCourse(
    context: ClientGameTestContext,
    title: String,
    tag: String,
    lowest: Int,
    timeout: Int,
    arrived: (Vec3) -> Boolean,
): String? = TurnWatch.watch(context, title) {
    for (tick in 0 until timeout) {
        val position = context.playerPosition()
        if (position.y < lowest) {
            return@watch "fell at $position after $tick ticks"
        }
        if (arrived(position)) {
            logger.info("PARKOUR $tag: done in $tick ticks")
            return@watch null
        }
        context.waitTick()
    }

    "not done within $timeout ticks, stopped at ${context.playerPosition()}"
}
