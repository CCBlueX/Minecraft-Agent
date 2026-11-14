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

private const val MIN_PRESS = 3
private const val MIN_RELEASE = 2

/**
 * The parkour, sneak and climb walks with human key timing: each still gets through, and no plain walk's key is
 * pressed or let go of for less than [MIN_PRESS] or [MIN_RELEASE] ticks, while jumps, climbs and edge sneaking
 * press their keys exactly.
 *
 * @agent claude-opus-5-5
 */
internal val keyTiming: GameTestScope.() -> Unit = {
    val scenarios = listOf("parkour" to walkParkour, "sneak" to walkSneak, "climb" to walkClimb)
    var plain = 0
    try {
        for ((name, scenario) in scenarios) {
            context.onClient {
                KeyTiming.reset()
                KeyTiming.minPressTicks = MIN_PRESS
                KeyTiming.minReleaseTicks = MIN_RELEASE
            }
            scenario()
            context.fromClient {
                val presses = KeyTiming.presses
                check(presses.isNotEmpty()) { "The $name walk pressed no keys" }
                val shaped = presses.count { !it.exact }
                logger.info("KEY TIMING {}: {} of {} ticks plain", name, shaped, presses.size)
                plain += shaped
                val violations = KeyTiming.violations()
                check(violations.isEmpty()) { "The $name walk broke the key timing: ${violations.take(5)}" }
            }
        }
        check(plain > 0) { "None of the walks pressed plain keys" }
    } finally {
        context.onClient { KeyTiming.reset() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class KeyTimingGameTest : ServerGameTest(keyTiming)
