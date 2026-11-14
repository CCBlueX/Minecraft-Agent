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
package net.ccbluex.agent.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Who hit the agent on a 1.8 server, which names no source: a player hit the agent at three ticks and it never swung
 * back; the fight engaged him only between them.
 *
 * @agent claude-opus-5-5
 */
class AttackerTest {

    private class Other(val name: String, val distance: Double, val swing: Float?)

    private fun attacker(vararg players: Other): String? =
        likelyAttacker(players.toList(), Other::distance, Other::swing)?.name

    @Test
    fun theOneThatJustSwungHit() {
        assertEquals(
            "hitter",
            attacker(Other("bystander", 2.0, null), Other("hitter", 3.1, 0.1f), Other("far", 9.0, 0.0f)),
        )
    }

    @Test
    fun theNearestOfTwoSwingingHit() {
        assertEquals("near", attacker(Other("near", 2.5, 0.3f), Other("other", 4.0, 0.0f)))
    }

    @Test
    fun anArmLateInItsSwingHitEarlier() {
        assertNull(attacker(Other("late", 2.0, 0.8f)))
    }

    @Test
    fun withoutASwingNobodyHit() {
        assertNull(attacker(Other("still", 1.5, null)))
    }
}
