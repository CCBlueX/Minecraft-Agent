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
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.world.Input
import net.minecraft.client.Minecraft

/**
 * The key timing the walkers of the running scenario use, and the keys their bodies pressed meanwhile. Only on the
 * client thread.
 *
 * @agent claude-opus-5-5
 */
internal object KeyTiming {

    var minPressTicks = 0
    var minReleaseTicks = 0

    /**
     * One tick's keys: the player tick they were pressed on, and whether they were exact.
     */
    class Press(val tick: Int, val input: Input, val exact: Boolean)

    val presses = ArrayList<Press>()

    fun options(options: Options): Options =
        options.copy(minPressTicks = minPressTicks, minReleaseTicks = minReleaseTicks)

    /**
     * A body that notes what it presses while a timing is set.
     */
    fun body(): ClientBody = object : ClientBody() {
        override fun press(input: Input, exact: Boolean) {
            super.press(input, exact)
            if (minPressTicks > 0 || minReleaseTicks > 0) {
                presses += Press(checkNotNull(Minecraft.getInstance().player).tickCount, input, exact)
            }
        }
    }

    /**
     * The key runs that broke the timing: a press or release that began and ended on plain ticks in a row of pressed
     * ticks but lasted fewer ticks than the minimum. Jumps go at once, so their releases are not checked.
     */
    fun violations(): List<String> {
        val found = ArrayList<String>()
        for ((name, key) in KEYS) {
            var start = 0
            for (i in 1 until presses.size) {
                if (presses[i].tick != presses[i - 1].tick + 1) {
                    // The navigator halted in between, which lets go of the keys
                    start = i
                    continue
                }
                val down = key(presses[i].input)
                if (down == key(presses[i - 1].input)) continue
                val wasDown = !down
                val length = presses[i].tick - presses[start].tick
                val least = if (wasDown) minPressTicks else minReleaseTicks
                val checked = start > 0 && presses[start].tick == presses[start - 1].tick + 1 &&
                    !presses[start].exact && !presses[i].exact && !(name == "jump" && !wasDown)
                if (checked && length < least) {
                    found += "$name ${if (wasDown) "down" else "up"} $length ticks from tick ${presses[start].tick}"
                }
                start = i
            }
        }
        return found
    }

    fun reset() {
        minPressTicks = 0
        minReleaseTicks = 0
        presses.clear()
    }

    private val KEYS = listOf<Pair<String, (Input) -> Boolean>>(
        "forward" to Input::forward,
        "backward" to Input::backward,
        "left" to Input::left,
        "right" to Input::right,
        "jump" to Input::jump,
        "sneak" to Input::shift,
    )
}
