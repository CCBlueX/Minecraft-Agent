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
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package net.ccbluex.agent.tick

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Requires what [read] returns for the rest of this run: `false` or `null` ends it. Every wait ([tickUntil],
 * [waitTicks], [tickConditional]) reads it again on each tick it waits and once more right before the code after it
 * runs. Once it fails, the run ends there quietly: nothing after that wait runs, `finally` blocks still do. If it
 * fails right now, the run ends here.
 *
 * A condition and a value are written the same way; a delegated value is always the one read last:
 * ```kotlin
 * requires { senses.state == RoundState.PLAYING }
 * val target by requires { fight.target }
 * ```
 *
 * Works in every coroutine launched on a [TickScope], each launch a run of its own. Unlike LiquidBounce's runs, a
 * run is not bound to the world it started in, as the library hears of no world change; write
 * `unchanged { body.level }` for that.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L34-L55)
 *
 * @agent claude-opus-5-5
 */
public suspend fun <T : Any> requires(read: () -> T?): Required<T> {
    val guards = checkNotNull(currentCoroutineContext()[Guards]) {
        "requires only works in coroutines launched on a TickScope"
    }
    return Required(read).also { guards.add(it.check) }
}

/**
 * Requires that [read] returns the same instance it returns now, such as the level.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L57-L63)
 *
 * @agent claude-opus-5-5
 */
public suspend fun unchanged(read: () -> Any?) {
    val initial = read()
    requires { read() === initial }
}

/**
 * A value a run [requires], read as a delegate.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L65-L76)
 *
 * @agent claude-opus-5-5
 */
public class Required<T : Any> internal constructor(read: () -> T?) : ReadOnlyProperty<Any?, T> {

    private var current: T? = null

    internal val check = { read().also { current = it }.let { it != null && it != false } }

    public val value: T
        get() = checkNotNull(current) { "Required value read before it was required" }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

/**
 * What one run [requires], carried in its coroutine context so that waits find it. Every launch copies it empty;
 * `withContext` keeps it, as the code it runs is still part of the run.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L78-L152)
 *
 * @agent claude-opus-5-5
 */
internal class Guards : CopyableThreadContextElement<Unit> {

    companion object Key : CoroutineContext.Key<Guards>

    override val key: CoroutineContext.Key<*>
        get() = Key

    private var conditions: ArrayList<() -> Boolean>? = null

    val isIdle: Boolean
        get() = conditions.isNullOrEmpty()

    fun add(condition: () -> Boolean) {
        (conditions ?: ArrayList<() -> Boolean>(4).also { conditions = it }).add(condition)
        if (!condition()) {
            throw GuardInterruption()
        }
    }

    /**
     * The interruption for the first condition that no longer holds.
     */
    fun broken(): GuardInterruption? {
        val conditions = conditions ?: return null
        for (i in conditions.indices) {
            if (!conditions[i]()) {
                return GuardInterruption()
            }
        }
        return null
    }

    fun ensure() {
        broken()?.let { throw it }
    }

    override fun copyForChild(): Guards = Guards()

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = Guards()

    override fun updateThreadContext(context: CoroutineContext) = Unit

    override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) = Unit
}

/**
 * The guards of the coroutine, or null when it requires nothing, so waits skip the checks.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L180-L184)
 *
 * @agent claude-opus-5-5
 */
internal fun CoroutineContext.activeGuards(): Guards? = this[Guards]?.takeUnless { it.isIdle }

/**
 * Ends a run at the wait where a requirement failed.
 *
 * From LiquidBounce: [Guards.kt](https://github.com/CCBlueX/LiquidBounce/blob/83d2935ef815a239b25e334f8758f0057adbb019/src/main/kotlin/net/ccbluex/liquidbounce/event/Guards.kt#L208-L215)
 *
 * @agent claude-opus-5-5
 */
internal class GuardInterruption : CancellationException("A requirement of the run no longer holds") {
    override fun fillInStackTrace(): Throwable = this
}
