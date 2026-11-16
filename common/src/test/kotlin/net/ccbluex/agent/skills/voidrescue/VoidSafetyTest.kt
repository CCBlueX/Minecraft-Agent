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

package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.world.Input
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A hit knocked a fighter without blocks from 1.1 blocks inside the edge of a 25-block island over the void. The void
 * rescue took the keys mid-air and held none, so it came down on the rim and slid off.
 *
 * @agent claude-opus-5-5
 */
class VoidSafetyTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private const val Y_ROT = 167.1f
    }

    private val terrain = TestWorld().fill(-12, 99, -12, 12, 99, 12).terrain()

    /**
     * Where the fighter stood when the hit landed.
     */
    private val safeGround = Vec3(11.873, 100.0, 5.940)

    /**
     * The feet 40 ticks after the knockback, with the void rescue's keys in the air and none once on the ground.
     */
    private fun landing(steers: Boolean): Vec3 {
        val motion = PlayerMotion(terrain)
        motion.reset(Vec3(12.191, 100.4, 6.578), Vec3(0.174, 0.314, 0.348), onGround = false)
        repeat(40) {
            val keys = if (motion.onGround || !steers) Input.EMPTY else VoidSafety.steering(motion.position, safeGround, Y_ROT)
            motion.tick(Y_ROT, axis(keys.forward, keys.backward), axis(keys.left, keys.right), false, false)
        }
        return motion.position
    }

    private fun axis(positive: Boolean, negative: Boolean) = if (positive == negative) 0 else if (positive) 1 else -1

    @Test
    fun withoutKeysTheKnockbackCarriesItOff() {
        assertTrue(landing(steers = false).y < 99.0)
    }

    @Test
    fun steeringBackInTheAirLandsItOnTheIsland() {
        assertEquals(100.0, landing(steers = true).y, 1.0E-6)
    }
}
