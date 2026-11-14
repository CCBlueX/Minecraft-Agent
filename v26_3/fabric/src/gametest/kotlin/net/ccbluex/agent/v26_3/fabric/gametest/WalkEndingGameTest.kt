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

import kotlinx.coroutines.launch
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.navigation.walkTo
import net.ccbluex.agent.tick.TickScope
import net.ccbluex.agent.tick.requires
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

private const val WALK_TICKS = 600

/**
 * Suspend walks on a [TickScope] ticked in the player's tick: one arrives on open ground, one into a sealed box ends
 * unreachable, one replaced by another walk ends halted, and one whose requirement fails ends there and lets go of
 * the keys.
 *
 * @agent claude-opus-5-5
 */
internal val walkEnding: GameTestScope.() -> Unit = {
    val scope = TickScope()
    val ticker = Consumer<LocalPlayer> { scope.tick() }
    lateinit var navigation: ClientNavigation
    context.onClient {
        navigation = ClientNavigation(KeyTiming.body(), KeyTiming.options(Options()))
        GameTicks.afterInput.addIfAbsent(ticker)
    }

    try {
        val y = origin.y.toDouble()
        buildCourse(origin.y)
        moveTo(Vec3(0.5, y, 0.5), yRot = -90f)

        val arrived = walk(scope, "to open ground") { navigation.navigator.walkTo(Vec3(12.5, y, 0.5), 1.0) }
        check(arrived == Ending.ARRIVED) { "The walk to open ground ended $arrived" }

        val sealed = walk(scope, "into the sealed box") { navigation.navigator.walkTo(Vec3(28.5, y, 0.5), 1.0) }
        check(sealed == Ending.UNREACHABLE) { "The walk into the sealed box ended $sealed" }

        moveTo(Vec3(0.5, y, 0.5), yRot = -90f)
        var replaced: Ending? = null
        context.onClient { scope.launch { replaced = navigation.navigator.walkTo(Vec3(20.5, y, 8.5), 1.0) } }
        context.waitTicks(20)
        context.onClient { navigation.navigator.moveTo(Vec3(0.5, y, 0.5)) }
        await(context, 5, "The replaced walk did not end") { context.fromClient { replaced != null } }
        check(replaced == Ending.STOPPED) { "The replaced walk ended $replaced" }
        await(context, WALK_TICKS, "The walk that replaced it did not arrive") {
            context.fromClient { !navigation.navigator.isInProgress }
        }

        var allowed = true
        var after = false
        var cleanedUp = false
        context.onClient {
            scope.launch {
                requires { allowed }
                try {
                    navigation.navigator.walkTo(Vec3(20.5, y, -8.5), 1.0)
                    after = true
                } finally {
                    cleanedUp = true
                }
            }
        }
        context.waitTicks(20)
        check(context.fromClient { navigation.navigator.isInProgress }) { "The guarded walk did not start" }
        context.onClient { allowed = false }
        context.waitTicks(2)
        context.fromClient {
            check(cleanedUp && !after) { "The run went on after its requirement failed" }
            check(!navigation.navigator.isInProgress) { "The navigator still holds the dropped walk" }
        }
        repeat(5) {
            val keys = context.fromClient { checkNotNull(it.player).input.keyPresses }
            check(!keys.forward() && !keys.backward() && !keys.left() && !keys.right() && !keys.jump()) {
                "Keys still pressed after the run ended: $keys"
            }
            context.waitTick()
        }
    } finally {
        context.onClient {
            scope.cancel()
            GameTicks.afterInput.remove(ticker)
            navigation.navigator.stop()
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class WalkEndingGameTest : ServerGameTest(walkEnding)

/**
 * Launches [walk] on [scope] and waits for its ending.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.walk(scope: TickScope, what: String, walk: suspend () -> Ending): Ending {
    var ending: Ending? = null
    context.onClient { scope.launch { ending = walk() } }
    await(context, WALK_TICKS, "Did not end the walk $what") { context.fromClient { ending != null } }
    return checkNotNull(ending)
}

/**
 * Open ground at x -5..40, and a stone box at x 26..30 sealed all round with air inside.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourse(y: Int) {
    fillArea(BlockPos(-5, y, -12), BlockPos(40, y + 6, 12), "minecraft:air")
    fill(BlockPos(-5, y - 1, -12), BlockPos(40, y - 1, 12), "minecraft:stone")
    fill(BlockPos(26, y, -2), BlockPos(30, y + 3, 2), "minecraft:stone")
    fill(BlockPos(27, y, -1), BlockPos(29, y + 1, 1), "minecraft:air")
    awaitServer("block 30 ${y + 3} 2 minecraft:stone")
}
