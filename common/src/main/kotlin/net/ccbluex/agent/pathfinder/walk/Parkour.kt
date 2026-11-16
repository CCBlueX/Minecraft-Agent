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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.terrain.Terrain

/**
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L52-L54)
 *
 * @agent claude-opus-5-5
 */
internal fun interface JumpFilter {
    fun accept(x: Int, y: Int, z: Int): Boolean
}

/**
 * The parkour moves out of a feet block: jumps over a gap or dip to spots out of reach of the other moves, each
 * simulated by [Jumps], passed to [sink]. Not thread-safe.
 *
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L288-L311)
 *
 * @agent claude-opus-5-5
 */
internal class Parkour(terrain: Terrain, abilities: WalkAbilities, private val sink: Sink) {

    private val maxJumpDrop = abilities.safeFall + if (abilities.riskyJumps) RISKY_JUMP_DROP else 1
    private val jumpPenalty = if (abilities.riskyJumps) 0.0 else Costs.JUMP_PENALTY
    private val jumps = Jumps(terrain, abilities)
    private val jumpTargets = JumpTargets(terrain, abilities.motion)

    /**
     * Passes the jumps from [from], standing at [fromHeight], to spots [isJumpWorth] to [sink].
     */
    fun forEach(from: BlockPos, fromHeight: Double, isJumpWorth: JumpFilter) {
        val feet = Vec3(from.x + 0.5, fromHeight, from.z + 0.5)
        // Drops to the ground below a course would use up the jumps worked out before the next block of it.
        var planned = 0
        var drops = 0
        jumpTargets.forEach(from.x, from.y, from.z, fromHeight, maxJumpDrop) { tx, ty, tz, top ->
            val drop = top < fromHeight - DROP_HEIGHT
            if (drop && drops >= MAX_JUMPS || !isJumpWorth.accept(tx, ty, tz)) {
                return@forEach planned < MAX_JUMPS
            }
            val plan = jumps.plan(from, fromHeight, BlockPos(tx, ty, tz), top)
            if (plan != null) {
                sink.accept(tx, ty, tz, plan.ticks(feet) + jumpPenalty, plan)
                if (drop) drops++ else planned++
            }
            planned < MAX_JUMPS
        }
    }

    /**
     * @agent claude-opus-5-5
     */
    fun interface Sink {
        fun accept(x: Int, y: Int, z: Int, cost: Double, jump: Jump)
    }

    private companion object {
        /**
         * Jumps tried from one spot, nearest first.
         */
        const val MAX_JUMPS = 24

        /**
         * How much lower a jump has to land to count as a drop, the floor beside a slab included.
         */
        const val DROP_HEIGHT = 0.25
        const val RISKY_JUMP_DROP = 3
    }
}
