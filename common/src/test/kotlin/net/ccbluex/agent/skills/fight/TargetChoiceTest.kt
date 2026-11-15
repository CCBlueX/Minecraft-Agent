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

package net.ccbluex.agent.skills.fight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Who the built-in fight goes for, and that the rules' defaults keep the old base rates.
 *
 * @agent claude-opus-5-5
 */
class TargetChoiceTest {

    private val distances = mapOf("attacker" to 6.0, "current" to 6.5, "near" to 5.0, "nearest" to 3.0)

    private fun choose(
        engaged: String? = null,
        attacker: String? = null,
        sinceHit: Long = 1000,
        current: String? = null,
        nearest: String? = null,
        hunts: Boolean = true,
        outside: Set<String> = emptySet(),
    ): String? = chooseTarget(
        engaged, attacker, sinceHit, current, nearest, hunts, { it !in outside }, { distances.getValue(it) },
    )

    @Test
    fun `a recent attacker wins over everyone else`() {
        assertEquals("attacker", choose(attacker = "attacker", sinceHit = TURN_TICKS.toLong(),
            current = "current", nearest = "nearest"))
        assertEquals("current", choose(attacker = "attacker", sinceHit = TURN_TICKS + 1L,
            current = "current", nearest = "near"))
    }

    @Test
    fun `without hunting an older attacker is still fought`() {
        assertEquals("attacker", choose(attacker = "attacker", hunts = false, nearest = "nearest"))
        assertNull(choose(hunts = false, current = "current", nearest = "nearest"))
    }

    @Test
    fun `the current target stays unless another is more than two blocks closer`() {
        assertEquals("current", choose(current = "current", nearest = "near"))
        assertEquals("nearest", choose(current = "current", nearest = "nearest"))
        assertEquals("nearest", choose(nearest = "nearest"))
    }

    @Test
    fun `candidates outside the area are skipped`() {
        assertEquals("nearest", choose(attacker = "attacker", sinceHit = 0, nearest = "nearest",
            outside = setOf("attacker")))
        assertEquals("near", choose(current = "current", nearest = "near", outside = setOf("current")))
        assertEquals("attacker", choose(attacker = "attacker", nearest = "nearest", outside = setOf("nearest")))
        assertNull(choose(attacker = "attacker", hunts = false, outside = setOf("attacker")))
    }

    @Test
    fun `an engaged target is fought wherever it is`() {
        assertEquals("current", choose(engaged = "current", attacker = "attacker", sinceHit = 0,
            nearest = "nearest", outside = setOf("current")))
    }

    @Test
    fun `default rules keep the old base rates and limits`() {
        val rules = Rules.DEFAULT
        assertEquals(8.0, rules.cps)
        assertEquals(4.0, rules.reaction)
        assertEquals(0.5, rules.critChance)
        assertEquals(0.5, rules.wtapChance)
        assertEquals(0.0, rules.reach)
        assertEquals(Rules.Strafe.FREE, rules.strafe)
        assertNull(rules.area)
    }
}
