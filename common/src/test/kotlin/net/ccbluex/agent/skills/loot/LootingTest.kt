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

package net.ccbluex.agent.skills.loot

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A chest is looted only while the game mode asks for it. A Blitz agent evaded and retreated while the navigator's
 * goal stayed on the chest at (-2437.5, 43.5, 757.5) until it died: the loot ability walked to the chest it was given
 * last, after the game mode's walk away, and its walk wins.
 *
 * @agent claude-opus-5-5
 */
class LootingTest {

    private val chest = BlockPos(-2438, 43, 757)

    @Test
    fun `a chest asked for this tick is looted on`() {
        val looting = Looting(Chests(), Wanted.DEFAULT)
        assertTrue(looting.open(chest))
        assertTrue(looting.stillWanted())
        assertTrue(looting.open(chest))
        assertTrue(looting.stillWanted())
        assertTrue(looting.isLooting)
    }

    @Test
    fun `a tick without asking lets go of the chest`() {
        val looting = Looting(Chests(), Wanted.DEFAULT)
        looting.open(chest)
        looting.stillWanted()
        assertFalse(looting.stillWanted())
        assertFalse(looting.isLooting)
        assertTrue(looting.open(chest), "it can be asked for again")
    }
}
