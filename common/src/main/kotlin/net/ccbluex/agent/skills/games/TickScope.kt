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

package net.ccbluex.agent.skills.games

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * Runs a game mode's suspend code on the game tick, on the thread that calls [tick]. Code waits for later ticks
 * with [nextTick], [ticks] and [until]; everything resumed in a tick runs before [tick] returns.
 *
 * @agent claude-opus-5-5
 */
internal class TickScope(logger: Logger) : CoroutineScope {

    private val queue = ArrayDeque<Runnable>()
    private val waiting = ArrayList<CancellableContinuation<Unit>>()

    private val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
    }

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext = job + dispatcher +
        CoroutineExceptionHandler { _, error -> logger.warn("Game code failed", error) }

    /**
     * Ticks since this scope was made.
     */
    var ticks: Long = 0
        private set

    /**
     * Starts [block]; it runs from the next [tick] on.
     */
    fun start(block: suspend CoroutineScope.() -> Unit): Job = launch(block = block)

    /**
     * Resumes everything waiting for this tick and runs it until it waits again or ends.
     */
    fun tick() {
        ticks++
        if (waiting.isNotEmpty()) {
            val due = waiting.toList()
            waiting.clear()
            for (continuation in due) {
                continuation.resume(Unit)
            }
        }
        drain()
    }

    /**
     * Cancels everything started, running their cleanup now.
     */
    fun cancel() {
        job.cancelChildren()
        drain()
    }

    suspend fun nextTick() {
        suspendCancellableCoroutine { continuation ->
            waiting += continuation
            continuation.invokeOnCancellation { waiting -= continuation }
        }
    }

    suspend fun ticks(count: Int) {
        repeat(count) { nextTick() }
    }

    /**
     * Waits until [condition] holds, checked once per tick, at most [limit] ticks; whether it came to hold.
     */
    suspend fun until(limit: Int, condition: () -> Boolean): Boolean {
        repeat(limit) {
            if (condition()) return true
            nextTick()
        }
        return condition()
    }

    private fun drain() {
        while (true) {
            val next = queue.removeFirstOrNull() ?: return
            next.run()
        }
    }
}
