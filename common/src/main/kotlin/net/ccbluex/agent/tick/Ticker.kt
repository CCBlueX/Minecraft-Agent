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
@file:JvmName("Ticks")

package net.ccbluex.agent.tick

import kotlinx.coroutines.currentCoroutineContext
import java.util.function.BooleanSupplier
import java.util.function.IntPredicate
import kotlin.coroutines.CoroutineContext

/**
 * Where suspended code waits for game ticks, found in the coroutine context. [TickScope] is one for hosts that tick
 * themselves; a host with its own tick loop puts its own into the context, so library waits run on that loop.
 *
 * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L38-L58)
 *
 * @agent claude-opus-5-5
 */
public interface Ticker : CoroutineContext.Element {

    /**
     * Suspends until [stopAt] holds, tested once per tick with the ticks waited so far (from 1); returns them. An
     * exception [stopAt] throws reaches the waiting code.
     */
    public suspend fun tickUntil(stopAt: IntPredicate): Int

    override val key: CoroutineContext.Key<*>
        get() = Key

    public companion object Key : CoroutineContext.Key<Ticker>
}

/**
 * Waits on the [Ticker] of the coroutine until [stopAt] holds, see [Ticker.tickUntil]. What the run [requires] is
 * checked each tick before [stopAt] and once more before returning; once it fails, the run ends here quietly.
 *
 * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L124-L186)
 *
 * @agent claude-opus-5-5
 */
public suspend fun tickUntil(stopAt: IntPredicate): Int {
    val context = currentCoroutineContext()
    val ticker = checkNotNull(context[Ticker]) {
        "Ticks are waited for on a Ticker: launch on a TickScope or put the host's Ticker into the context"
    }
    val guards = context.activeGuards() ?: return ticker.tickUntil(stopAt)
    val ticks = ticker.tickUntil { ticks ->
        guards.broken()?.let { throw it }
        stopAt.test(ticks)
    }
    guards.ensure()
    return ticks
}

/**
 * Waits [ticks] ticks; 0 returns at once.
 *
 * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L202-L213)
 *
 * @agent claude-opus-5-5
 */
public suspend fun waitTicks(ticks: Int) {
    if (ticks == 0) {
        return
    }
    tickUntil { it >= ticks }
}

/**
 * Waits [ticks] ticks or until [breakLoop] holds; true when the time ran out without it.
 *
 * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L188-L200)
 *
 * @agent claude-opus-5-5
 */
public suspend fun tickConditional(ticks: Int, breakLoop: BooleanSupplier): Boolean {
    if (ticks == 0) {
        return !breakLoop.asBoolean
    }
    return tickUntil { breakLoop.asBoolean || it >= ticks } >= ticks
}
