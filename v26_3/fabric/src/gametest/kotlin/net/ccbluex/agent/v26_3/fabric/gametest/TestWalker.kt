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

import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.navigation.DestinationSource
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.v26_3.toAgent
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

/**
 * Walks to [goal] while started, standing still without one; make it on the client thread.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L96-L142)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class TestWalker(
    private val radius: Double,
    build: Boolean = false,
    riskyJumps: Boolean = false,
    listener: Listener = Listener.NONE,
) {

    private val navigation = ClientNavigation(
        body = KeyTiming.body(),
        options = KeyTiming.options(Options(canBreak = build, canPlace = build, riskyJumps = riskyJumps)),
        listener = listener,
    )

    @Volatile
    var goal: Vec3? = null

    val isActing: Boolean
        get() = navigation.navigator.isActing

    /**
     * Whether the walk holds the player at ledges now, as sneaking does.
     */
    val isSafeWalking: Boolean
        get() = navigation.body.safeWalk

    private val source = object : DestinationSource {
        override fun goal() = this@TestWalker.goal?.toAgent()

        override fun radius() = this@TestWalker.radius
    }

    fun start(context: ClientGameTestContext) = context.onClient { navigation.navigator.follow(source) }

    fun stop(context: ClientGameTestContext) = context.onClient {
        navigation.navigator.stop()
        goal = null
    }

    fun <T> walking(context: ClientGameTestContext, goal: Vec3, block: () -> T): T {
        context.onClient { this.goal = goal }
        try {
            return block()
        } finally {
            context.onClient { this.goal = null }
        }
    }

    /**
     * Walks to [goal] under [TurnWatch] and fails with [failure] after [ticks]; [observe] sees every tick.
     */
    fun walk(
        context: ClientGameTestContext,
        goal: Vec3,
        ticks: Int,
        title: String,
        failure: String,
        acting: () -> Boolean = { false },
        observe: (LocalPlayer) -> Unit = {},
    ) = walking(context, goal) {
        TurnWatch.watch(context, title, acting) {
            val took = await(context, ticks, "$failure to $goal") {
                context.fromClient { mc ->
                    val player = checkNotNull(mc.player)
                    observe(player)
                    player.position().arrivedAt(goal, radius)
                }
            }
            logTicks(title, took)
        }
    }
}
