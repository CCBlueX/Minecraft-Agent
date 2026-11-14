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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * From LiquidBounce: [GuardsTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/test/kotlin/net/ccbluex/liquidbounce/event/GuardsTest.kt#L38-L250)
 *
 * @agent claude-opus-5-5
 */
internal class GuardsTest {

    private val scope = TickScope(NOPLogger.NOP_LOGGER)

    private class Flag(var value: Boolean = true) {
        var checks = 0

        fun holds(): Boolean {
            checks++
            return value
        }
    }

    /**
     * How the last run started with [run] ended: null when it finished.
     */
    private var ended: Throwable? = null

    /**
     * Launches [block] and runs it to its first wait.
     */
    private fun run(block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(block = block).also { job ->
            job.invokeOnCompletion { ended = it }
            scope.tick()
        }

    private fun assertEndedByGuard() = assertTrue(ended is GuardInterruption, "The run ended with $ended")

    @Test
    fun `a requirement failing mid-wait ends the run before the code after the wait`() {
        val alive = Flag()
        var resumed = false
        var cleanedUp = false
        run {
            requires { alive.holds() }
            try {
                waitTicks(5)
                resumed = true
            } finally {
                cleanedUp = true
            }
        }

        scope.tick()
        scope.tick()
        alive.value = false
        scope.tick()

        assertEndedByGuard()
        assertFalse(resumed)
        assertTrue(cleanedUp)
    }

    @Test
    fun `a required value gone mid-wait ends the run`() {
        var current: String? = "first"
        val seen = mutableListOf<String>()
        run {
            val target by requires { current }
            while (true) {
                seen += target
                waitTicks(1)
            }
        }

        current = "second"
        scope.tick()
        current = null
        scope.tick()

        assertEndedByGuard()
        assertEquals(listOf("first", "second"), seen)
    }

    @Test
    fun `a wait condition never sees a failed requirement`() {
        var target: String? = "target"
        run {
            requires { target != null }
            tickUntil { checkNotNull(target).isEmpty() }
        }

        scope.tick()
        target = null
        scope.tick()

        assertEndedByGuard()
    }

    @Test
    fun `a requirement added after a wait only applies from then on`() {
        val late = Flag()
        run {
            waitTicks(1)
            requires { late.holds() }
            waitTicks(5)
        }

        scope.tick()
        assertEquals(1, late.checks)
        late.value = false
        scope.tick()

        assertEndedByGuard()
    }

    @Test
    fun `a requirement that is false when added ends the run right there`() {
        var ran = false
        run {
            requires { false }
            ran = true
        }

        assertEndedByGuard()
        assertFalse(ran)
    }

    @Test
    fun `unchanged holds only while the instance stays the same`() {
        var level = Any()
        run {
            unchanged { level }
            waitTicks(5)
        }

        scope.tick()
        level = Any()
        scope.tick()

        assertEndedByGuard()
    }

    @Test
    fun `withContext stays in the run, a launched coroutine is a run of its own`() {
        val inner = Flag()
        val child = Flag()
        var childEnded: Throwable? = null
        var resumed = false
        run {
            withContext(scope as Ticker) { requires { inner.holds() } }
            launch {
                requires { child.holds() }
                waitTicks(5)
            }.invokeOnCompletion { childEnded = it }
            waitTicks(2)
            resumed = true
        }

        scope.tick()
        child.value = false
        scope.tick()
        assertTrue(childEnded is GuardInterruption, "The child ended with $childEnded")

        scope.tick()
        assertNull(ended)
        assertTrue(resumed)
        assertTrue(inner.checks > 1, "The requirement added in withContext was not checked by the run")
    }

    @Test
    fun `two launches have their own requirements`() {
        val first = Flag()
        var secondDone = false
        run {
            requires { first.holds() }
            waitTicks(3)
        }
        scope.launch {
            waitTicks(3)
            secondDone = true
        }

        first.value = false
        repeat(5) { scope.tick() }

        assertEndedByGuard()
        assertTrue(secondDone)
    }

    @Test
    fun `exceptions from a requirement reach the waiting code`() {
        var fail = false
        var failure: Throwable? = null
        run {
            requires {
                check(!fail) { "requirement failed" }
                true
            }
            failure = runCatching { waitTicks(5) }.exceptionOrNull()
        }

        fail = true
        scope.tick()

        assertTrue(failure is IllegalStateException && failure?.message == "requirement failed", "Got $failure")
    }

    @Test
    fun `waits that require nothing behave as before`() {
        var waited = 0
        run { waited = tickUntil { it >= 2 } }

        scope.tick()
        scope.tick()

        assertEquals(2, waited)
    }
}
