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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.world.Heightmap
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.goTo
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * Walks about 40 blocks off in a random direction, to dry ground the level has loaded, as a player goes looking for
 * what is not in sight; whether the walk got going.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.explore(): Boolean {
    val player = body.player
    val level = body.level
    val random = ThreadLocalRandom.current()
    repeat(8) {
        val angle = random.nextDouble(Math.PI * 2)
        val x = (player.x + cos(angle) * 40).toInt()
        val z = (player.z + sin(angle) * 40).toInt()
        if (!level.isLoaded(BlockPos(x, 0, z))) return@repeat
        val ground = BlockPos(x, level.getHeight(Heightmap.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z)
        if (!level.getFluidState(ground).isEmpty) return@repeat
        listener.step("exploring towards ${ground.above().toShortString()}")
        return goTo(Vec3.atBottomCenterOf(ground.above()), 4.0) != null
    }
    return false
}
