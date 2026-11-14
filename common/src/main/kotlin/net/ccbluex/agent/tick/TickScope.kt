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

package net.ccbluex.agent.tick

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.IntPredicate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs suspend code on the game tick of the thread that calls [tick]: code launched on it starts with the next
 * [tick], and everything resumed in a tick runs before [tick] returns. Each launch is a run with its own [requires].
 * A failure is logged to [logger] and ends only its own run.
 *
 * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L63-L122)
 *
 * @agent claude-opus-5-5
 */
public class TickScope @JvmOverloads constructor(
    logger: Logger = LoggerFactory.getLogger("Agent/Ticks"),
) : CoroutineScope, Ticker {

    private val queue = ArrayDeque<Runnable>()
    private val running = ArrayList<Waiter>()
    private val pending = ArrayList<Waiter>()

    private val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext = job + dispatcher + this + Guards() +
        CoroutineExceptionHandler { _, error -> logger.warn("Tick code failed", error) }

    /**
     * Ticks since this scope was made.
     */
    public var ticks: Long = 0
        private set

    /**
     * Checks the guards and the condition of every wait, resumes those done and runs them until they wait or end.
     */
    public fun tick() {
        ticks++
        running += pending
        pending.clear()
        running.removeIf(Waiter::tick)
        drain()
    }

    /**
     * Cancels every run; their `finally` blocks run before this returns.
     */
    public fun cancel() {
        job.cancelChildren()
        running.clear()
        pending.clear()
        drain()
    }

    override suspend fun tickUntil(stopAt: IntPredicate): Int = suspendCancellableCoroutine { continuation ->
        pending += Waiter(continuation, stopAt)
    }

    private fun drain() {
        while (true) {
            val next = queue.removeFirstOrNull() ?: return
            next.run()
        }
    }

    /**
     * From LiquidBounce: [CoroutineTicker.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/CoroutineTicker.kt#L154-L186)
     *
     * @agent claude-opus-5-5
     */
    private class Waiter(private val continuation: CancellableContinuation<Int>, private val stopAt: IntPredicate) {

        private var ticks = 0

        /**
         * True once done with, resumed or cancelled.
         */
        fun tick(): Boolean {
            if (!continuation.isActive) {
                return true
            }
            val done = try {
                stopAt.test(++ticks)
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
                return true
            }
            if (done) {
                continuation.resume(ticks)
            }
            return done
        }
    }
}
