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

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.minecraft.world.entity.player.Input
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which keys the void rescue adds on a tick without any: a plan change halted the navigator on a bridge, the sneak
 * dropped and the guard never saw the idle tick.
 *
 * @agent claude-sonnet-5-5
 */
class AgentKeysTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val sneak = Input(false, false, false, false, false, true, false)
    private val forward = Input(true, false, false, false, false, false, false)

    @Test
    fun tickWithoutKeysIsGuarded() {
        val seen = ArrayList<Input>()
        val keys = idleKeys(Input.EMPTY) {
            seen += it
            sneak
        }
        assertEquals(listOf(Input.EMPTY), seen)
        assertEquals(sneak, keys)
    }

    @Test
    fun tickWithoutKeysLeftAloneStaysUnpressed() {
        assertNull(idleKeys(Input.EMPTY) { it })
    }

    @Test
    fun pressedKeysWereGuardedByTheNavigator() {
        assertNull(idleKeys(forward) { sneak })
    }
}
