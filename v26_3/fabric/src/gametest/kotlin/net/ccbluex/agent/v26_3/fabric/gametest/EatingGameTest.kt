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
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

private const val EAT_TICKS = 32
private const val WATCH_TICKS = 60
private const val HEAL_TICKS = 100

/**
 * A body eats a golden apple at half health with the use key up: the use starts once and lasts until the apple is
 * eaten, as vanilla would otherwise let go of it the next tick, and the health rises.
 *
 * @agent claude-opus-5-5
 */
internal val eating: GameTestScope.() -> Unit = {
    run("gamerule minecraft:natural_health_regeneration false")
    run("effect clear $playerName")
    run("item replace entity $playerName hotbar.0 with minecraft:golden_apple 1")
    run("damage $playerName 10")
    await(context, 40, "The golden apple did not reach the hotbar") {
        context.fromClient { checkNotNull(it.player).inventory.getItem(0).`is`(Items.GOLDEN_APPLE) }
    }
    context.waitTicks(5)
    val before = health()

    val body = ClientBody()
    val using = ArrayList<Boolean>()
    context.onClient {
        val player = checkNotNull(it.player)
        player.inventory.selectedSlot = 0
        check(body.useItem(InteractionHand.MAIN_HAND)) { "The golden apple was not used" }
    }
    repeat(WATCH_TICKS) {
        context.waitTick()
        using += context.fromClient { checkNotNull(it.player).isUsingItem }
    }

    val starts = using.zipWithNext().count { (was, now) -> !was && now } + if (using.first()) 1 else 0
    val longest = using.joinToString("") { if (it) "1" else "0" }.split("0").maxOf { it.length }
    check(starts == 1) { "The use started $starts times: $using" }
    check(longest >= EAT_TICKS - 2) { "The use lasted $longest ticks, not the eat" }
    // Regeneration heals every 25 ticks at level II
    var after = health()
    repeat(HEAL_TICKS / 10) {
        if (after > before) return@repeat
        context.waitTicks(10)
        after = health()
    }
    check(after > before) { "Eating did not heal: $before to $after" }
    // The hooks hold the body weakly; it has to live through the eat
    java.lang.ref.Reference.reachabilityFence(body)
}

/**
 * @agent claude-opus-5-5
 */
public class EatingGameTest : ServerGameTest(eating)
