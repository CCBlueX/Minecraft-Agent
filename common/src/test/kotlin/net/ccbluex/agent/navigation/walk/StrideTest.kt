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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.v26_3.toAgent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Landing from a parkour jump, the walk let go of sprint for one tick and the host started it again before the
 * next measure: the move made walking was taken for a slow sprint, and the next jump was given up as too far.
 *
 * @agent claude-opus-5-5
 */
internal class StrideTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `a move across a change of speed is not measured`() {
        val player = TestLevel(TestWorld.flat()).player(Vec3(0.5, 1.0, 0.5))
        val stride = Stride()
        player.setOnGround(true)
        player.deltaMovement = net.minecraft.world.phys.Vec3(0.2, 0.0, 0.0)
        player.tickCount = 1
        stride.tick(player.toAgent())

        // A walking push on top of the motion carried, then sprinting starts before the next measure.
        player.setPos(player.x + 0.2 + 0.1 * 0.98, player.y, player.z)
        player.isSprinting = true
        player.tickCount = 2
        stride.tick(player.toAgent())

        assertEquals(1.0, stride.factor)
    }
}
