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

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import java.util.function.Consumer
import kotlin.math.abs

/**
 * Finds glances back in the yaw the player sends: more than [AWAY] degrees off and back within [BACK] degrees inside
 * [WINDOW] ticks. Mining and placing aim at their blocks, so a glance that spans them does not count.
 *
 * From LiquidBounce: [TurnWatch.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/TurnWatch.kt#L38-L102)
 *
 * @agent claude-opus-5-5
 */
internal object TurnWatch {

    private val yRots = FloatArray(WINDOW)
    private val acted = BooleanArray(WINDOW)
    private var ticks = 0
    private var acting: () -> Boolean = { false }
    private val glances = mutableListOf<String>()

    private val tickHandler = Consumer<LocalPlayer> { player ->
        val yRot = player.yRot
        yRots[ticks % WINDOW] = yRot
        acted[ticks % WINDOW] = acting()
        ticks++

        val back = ticks - 1
        var spanActed = acted[back % WINDOW]
        for (from in back - 1 downTo maxOf(0, back - WINDOW + 1)) {
            spanActed = spanActed || acted[from % WINDOW]
            val start = yRots[from % WINDOW]
            if (spanActed || from == back - 1 || abs(Mth.wrapDegrees(yRot - start)) > BACK) {
                continue
            }
            val away = (from + 1 until back).count { abs(Mth.wrapDegrees(yRots[it % WINDOW] - start)) > AWAY }
            if (away > 0) {
                glances += "$away ticks at ${player.blockPosition().toShortString()}"
                // One glance is reported once.
                yRots.fill(yRot)
                break
            }
        }
    }

    fun <T> watch(
        context: ClientGameTestContext,
        what: String,
        acting: () -> Boolean = { false },
        block: () -> T,
    ): T {
        context.onClient {
            ticks = 0
            glances.clear()
            this.acting = acting
            GameTicks.afterPlayerTick.addIfAbsent(tickHandler)
        }
        val result = try {
            block()
        } finally {
            context.onClient { GameTicks.afterPlayerTick.remove(tickHandler) }
        }

        val found = context.fromClient { glances.toList() }
        check(found.isEmpty()) { "$what: glanced back for ${found.joinToString()}" }
        return result
    }

    const val AWAY = 120f
    const val BACK = 45f
    const val WINDOW = 20

}
