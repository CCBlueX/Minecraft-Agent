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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.util.Mth
import net.minecraft.world.level.block.FarmlandBlock
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Sprint jumps along straight runs, which are faster than running.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L429-L460), [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L780-L839)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class Hops(private val context: NavigationContext, private val stride: Stride) {

    private val player get() = context.body.player

    /**
     * Where the sprint hop in the air was aimed; the steps it passes over lie behind the player.
     */
    var aim: Vec3? = null
        private set

    fun clear() {
        aim = null
    }

    /**
     * Whether to jump now towards [aim], which then stays the aim until landing.
     */
    fun starts(position: Vec3, aim: Vec3): Boolean {
        if (!canHop(position, aim)) {
            return false
        }
        this.aim = aim
        return true
    }

    /**
     * Whether to jump while sprinting straight on to [aim]. The jump pushes the way the player moves along, so it
     * waits until the player turned to the aim; the way is checked as far as the hop goes at the measured speed.
     *
     * @agent claude-opus-5-5
     */
    private fun canHop(position: Vec3, aim: Vec3): Boolean {
        val distance = position.horizontalDistanceTo(aim)
        val able = player.onGround() && player.isSprinting && player.foodData.foodLevel > HOP_FOOD
        if (!able || distance < HOP_DISTANCE) {
            return false
        }
        if (abs(Mth.wrapDegrees(aim.subtract(position).yRot - context.body.movementYRot)) > HOP_FACING) {
            return false
        }

        val terrain = Terrain.live(context.body.level)
        val dx = (aim.x - position.x) / distance
        val dz = (aim.z - position.z) / distance
        val length = HOP_LENGTH * maxOf(1.0, stride.factor)
        var travelled = 0.0
        while (travelled <= length) {
            val x = position.x + dx * travelled
            val z = position.z + dz * travelled
            // The landing would trample farmland.
            val floor = terrain.state(Mth.floor(x), Mth.floor(position.y - FLOOR_PROBE), Mth.floor(z))
            if (!terrain.isClear(terrain.box(x, position.y + HOP_HEIGHT, z)) || floor.block is FarmlandBlock) {
                return false
            }
            travelled += HOP_SAMPLE
        }
        return true
    }

    private companion object {
        const val HOP_DISTANCE = 4.0

        /**
         * Degrees the facing may be off the aim for the push of the jump to carry the player there.
         */
        const val HOP_FACING = 15f
        const val HOP_FOOD = 10
        const val HOP_LENGTH = 3.5
        const val HOP_HEIGHT = 1.25
        const val HOP_SAMPLE = 0.5
        const val FLOOR_PROBE = 0.5
    }
}
