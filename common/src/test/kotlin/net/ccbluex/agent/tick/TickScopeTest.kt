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

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.lang.reflect.Proxy
import java.util.function.IntPredicate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
internal class TickScopeTest {

    private val failures = ArrayList<Throwable>()

    /**
     * Collects what the scope logs as failed.
     */
    private val logger = Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
        if (method.name == "warn" && args?.lastOrNull() is Throwable) failures += args.last() as Throwable
        if (method.returnType == Boolean::class.javaPrimitiveType) false else null
    } as Logger

    private val scope = TickScope(logger)

    @Test
    fun `launched code starts with the next tick`() {
        var started = false
        scope.launch { started = true }
        assertFalse(started)
        scope.tick()
        assertTrue(started)
    }

    @Test
    fun `waitTicks resumes on the tick it counts to`() {
        var resumed = 0L
        scope.launch {
            waitTicks(3)
            resumed = scope.ticks
        }
        repeat(3) { scope.tick() }
        assertEquals(0L, resumed)
        scope.tick()
        assertEquals(4L, resumed, "Started on tick 1, three ticks waited")
    }

    @Test
    fun `waitTicks of 0 does not wait`() {
        var done = false
        scope.launch {
            waitTicks(0)
            done = true
        }
        scope.tick()
        assertTrue(done)
    }

    @Test
    fun `tickConditional tells time running out from the condition breaking`() {
        var broken = false
        val results = ArrayList<Boolean>()
        scope.launch { results += tickConditional(5) { false } }
        scope.launch { results += tickConditional(5) { broken } }
        scope.tick()
        scope.tick()
        broken = true
        scope.tick()
        assertEquals(listOf(false), results)
        repeat(3) { scope.tick() }
        assertEquals(listOf(false, true), results)
    }

    @Test
    fun `cancel runs finally blocks before it returns`() {
        var cleanedUp = false
        val job = scope.launch {
            try {
                waitTicks(100)
            } finally {
                cleanedUp = true
            }
        }
        scope.tick()
        scope.cancel()
        assertTrue(cleanedUp)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a failing run is logged and the others go on`() {
        var other = 0
        scope.launch {
            waitTicks(1)
            error("boom")
        }
        scope.launch {
            repeat(3) {
                waitTicks(1)
                other++
            }
        }
        repeat(4) { scope.tick() }
        assertEquals("boom", failures.single().message)
        assertEquals(3, other)
    }

    @Test
    fun `waiting without a ticker fails with a message`() {
        var failure: Throwable? = null
        val block: suspend () -> Unit = { waitTicks(1) }
        block.startCoroutine(Continuation(EmptyCoroutineContext) { failure = it.exceptionOrNull() })
        val message = assertNotNull(failure).message.orEmpty()
        assertTrue("Ticker" in message, message)
    }

    @Test
    fun `a ticker put into the context is the one that waits`() {
        val host = HostTicker()
        var done = false
        scope.launch {
            withContext(host) { waitTicks(2) }
            done = true
        }
        scope.tick()
        repeat(5) { scope.tick() }
        assertFalse(done, "The scope's own ticks went on, the host's did not")
        host.tick()
        host.tick()
        scope.tick()
        assertTrue(done)
    }

    @Test
    fun `a cancelled wait is dropped`() {
        val job = scope.launch { waitTicks(10) }
        scope.tick()
        job.cancel()
        scope.tick()
        assertTrue(job.isCompleted)
    }

    /**
     * A host loop shaped like LiquidBounce's adapter: it resumes on its own ticks.
     */
    private class HostTicker : Ticker {
        private val waits = ArrayList<Pair<IntPredicate, (Int) -> Unit>>()
        private var counts = HashMap<IntPredicate, Int>()

        override suspend fun tickUntil(stopAt: IntPredicate): Int =
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                waits += stopAt to { ticks: Int -> continuation.resumeWith(Result.success(ticks)) }
            }

        fun tick() {
            val due = waits.filter { (stopAt, _) ->
                val ticks = counts.merge(stopAt, 1, Int::plus) ?: 1
                stopAt.test(ticks)
            }
            waits.removeAll(due)
            due.forEach { (stopAt, resume) -> resume(counts.getValue(stopAt)) }
        }
    }
}
