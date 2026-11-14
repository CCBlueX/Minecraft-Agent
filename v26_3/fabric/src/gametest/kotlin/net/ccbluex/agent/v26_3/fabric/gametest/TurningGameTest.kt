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

import net.ccbluex.agent.v26_3.fabric.ClientBody
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

private const val TURN = 10f
private const val WALK_TICKS = 400

/**
 * A body that turns at most [TURN] degrees a tick walks to a goal behind it and on round a corner: every rotation the
 * server gets is at most that far, give or take one mouse step, from the one before.
 *
 * @agent claude-opus-5-5
 */
internal val turning: GameTestScope.() -> Unit = {
    lateinit var navigation: ClientNavigation
    val sent = ArrayList<ViewRotation>()
    val recorder = Runnable { sent += navigation.body.sentRotation }
    context.onClient {
        navigation = ClientNavigation(ClientBody(Turning.linear(TURN)))
        GameTicks.clientTick.addIfAbsent(recorder)
    }

    try {
        val y = origin.y
        fillArea(BlockPos(-15, y, -25), BlockPos(25, y + 5, 10), "minecraft:air")
        fill(BlockPos(-15, y - 1, -25), BlockPos(25, y - 1, 10), "minecraft:stone")
        awaitServer("block 25 ${y - 1} 10 minecraft:stone")
        moveTo(Vec3(0.5, y.toDouble(), 0.5), yRot = 0f)
        context.onClient { sent.clear() }

        for (goal in listOf(Vec3(0.5, y.toDouble(), -15.5), Vec3(15.5, y.toDouble(), -15.5))) {
            var ending: Ending? = null
            context.onClient { navigation.navigator.moveTo(goal, 1.0) { ending = it } }
            await(context, WALK_TICKS, "Did not walk to $goal") { context.fromClient { ending != null } }
            check(ending == Ending.ARRIVED) { "The walk to $goal ended $ending" }
        }

        context.fromClient {
            val step = navigation.body.viewStep
            val turns = sent.zipWithNext { a, b -> abs(Mth.wrapDegrees(b.yRot - a.yRot)) }
            check(turns.sum() > 150f) { "The walk barely turned: ${turns.sum()} degrees" }
            val fastest = turns.max()
            check(fastest <= TURN + step + 0.01f) { "Turned $fastest degrees in a tick, more than $TURN and a step" }
        }
    } finally {
        context.onClient {
            GameTicks.clientTick.remove(recorder)
            navigation.navigator.stop()
        }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class TurningGameTest : ServerGameTest(turning)
