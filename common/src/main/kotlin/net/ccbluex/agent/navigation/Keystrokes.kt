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

package net.ccbluex.agent.navigation

import net.minecraft.world.entity.player.Input

/**
 * The movement keys as a person presses them: every press and every release lasts a least number of ticks. Exact
 * ticks pass unchanged but still count, so the next shaped tick knows what the keys really did.
 *
 * @agent claude-opus-5-5
 */
internal class Keystrokes {

    private val down = BooleanArray(KEYS)

    /**
     * Ticks each key has been in its state, counting this one once [press] returned.
     */
    private val ticks = IntArray(KEYS) { LONG_AGO }

    /**
     * [input] shaped for [minPress] and [minRelease] ticks, unless [exact].
     */
    fun press(input: Input, exact: Boolean, minPress: Int, minRelease: Int): Input {
        val wanted = booleanArrayOf(
            input.forward(), input.backward(), input.left(), input.right(), input.shift(), input.jump(),
        )
        val keys = if (exact || minPress <= 0 && minRelease <= 0) wanted else shape(wanted, minPress, minRelease)
        for (key in 0 until KEYS) {
            if (keys[key] == down[key]) {
                ticks[key] = minOf(ticks[key] + 1, LONG_AGO)
            } else {
                down[key] = keys[key]
                ticks[key] = 1
            }
        }
        return if (keys.contentEquals(wanted)) {
            input
        } else {
            Input(keys[FORWARD], keys[BACKWARD], keys[LEFT], keys[RIGHT], keys[JUMP], keys[SNEAK], input.sprint())
        }
    }

    /**
     * The keys are let go of by someone else, such as the user.
     */
    fun reset() {
        down.fill(false)
        ticks.fill(LONG_AGO)
    }

    private fun shape(wanted: BooleanArray, minPress: Int, minRelease: Int): BooleanArray {
        val keys = BooleanArray(KEYS) { key ->
            when {
                down[key] && !wanted[key] -> ticks[key] < minPress
                // A late jump lands elsewhere; only its press is lengthened
                !down[key] && wanted[key] -> key == JUMP || ticks[key] >= minRelease
                else -> wanted[key]
            }
        }
        holdApart(keys, wanted, FORWARD, BACKWARD)
        holdApart(keys, wanted, LEFT, RIGHT)
        return keys
    }

    /**
     * A key held for its least press keeps its opposite up until it is let go.
     */
    private fun holdApart(keys: BooleanArray, wanted: BooleanArray, a: Int, b: Int) {
        if (!keys[a] || !keys[b]) {
            return
        }
        when {
            down[a] && !wanted[a] -> keys[b] = false
            down[b] && !wanted[b] -> keys[a] = false
        }
    }

    private companion object {
        const val FORWARD = 0
        const val BACKWARD = 1
        const val LEFT = 2
        const val RIGHT = 3
        const val SNEAK = 4
        const val JUMP = 5
        const val KEYS = 6
        const val LONG_AGO = 1_000
    }
}
