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

package net.ccbluex.agent.navigation.walk

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Setbacks of a server's anti-cheat: it set the player back from about (55.6, 73, -27.2) three times while it walked
 * the same way each time.
 *
 * @agent claude-opus-5-5
 */
internal class SetbacksTest {

    private val setbacks = Setbacks()

    @Test
    fun `the second setback at one spot is a refusal`() {
        assertFalse(setbacks.refuses(Vec3(55.56, 73.0, -27.28), 757))
        assertTrue(setbacks.refuses(Vec3(55.61, 73.0, -27.16), 761))
        assertTrue(setbacks.refuses(Vec3(55.60, 73.0, -27.16), 765))
    }

    @Test
    fun `setbacks far apart in place or time are not`() {
        assertFalse(setbacks.refuses(Vec3(55.56, 73.0, -27.28), 757))
        assertFalse(setbacks.refuses(Vec3(42.7, 67.0, -28.5), 770))
        assertFalse(setbacks.refuses(Vec3(42.6, 67.4, -28.5), 1401))
    }
}
