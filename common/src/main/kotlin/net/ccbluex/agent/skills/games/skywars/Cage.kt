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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.Player

/**
 * Finds the cage by the blocks around the player, for hosts that do not know it: standing walled in at the feet and
 * the head for [CAGE_TICKS] is a cage, and once out of it on the ground, below where it stood or free for
 * [FREE_TICKS], the cage dropped.
 *
 * From LiquidBounce: [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L904-L929), [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L299-L329), [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L140-L142)
 *
 * @agent claude-opus-5-5
 */
internal class Cage {

    /**
     * Where the player stood caged; null until a cage was seen.
     */
    var position: Vec3? = null
        private set

    var dropped = false
        private set

    private var enclosedTicks = 0
    private var cageY = 0.0
    private var freeTicks = 0

    fun tick(player: Player) {
        if (dropped) return
        val enclosed = isEnclosed(player)
        if (position == null) {
            enclosedTicks = if (enclosed) enclosedTicks + 1 else 0
            if (enclosedTicks >= CAGE_TICKS) {
                position = player.position()
                cageY = player.y
            }
            return
        }
        if (enclosed) {
            cageY = player.y
            freeTicks = 0
            return
        }
        if (!player.onGround()) return
        freeTicks++
        if (player.y < cageY - DROP_HEIGHT || freeTicks >= FREE_TICKS) {
            dropped = true
        }
    }

    private fun isEnclosed(player: Player): Boolean {
        if (!player.onGround()) return false
        val feet = player.blockPosition()
        return Direction.Plane.HORIZONTAL.all { isSolid(player, feet.relative(it)) && isSolid(player, feet.above().relative(it)) }
    }

    private fun isSolid(player: Player, pos: BlockPos): Boolean {
        val level = player.level()
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
    }

    private companion object {
        const val CAGE_TICKS = 10
        const val FREE_TICKS = 20
        const val DROP_HEIGHT = 0.9
    }
}
