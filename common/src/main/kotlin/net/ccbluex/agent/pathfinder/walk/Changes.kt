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
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.block.BlockKind


/**
 * The blocks one move out of [from] breaks and places, and the ticks the breaking takes. The lists are reused for
 * every move. Not thread-safe.
 *
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L475-L542)
 *
 * @agent claude-opus-5-5
 */
internal class Changes(private val terrain: Terrain, private val nodes: Nodes, private val abilities: WalkAbilities) {

    val breaks = ArrayList<BlockPos>(3)
    val places = ArrayList<BlockPos>(1)
    var breakCost = 0.0
        private set

    /**
     * The node the moves start from.
     */
    var from: BlockPos = BlockPos.ZERO

    fun clear() {
        breaks.clear()
        places.clear()
        breakCost = 0.0
    }

    fun passOrBreak(x: Int, y: Int, z: Int): Boolean {
        if (terrain.isPassable(x, y, z)) {
            return true
        }

        val ticks = breakTicks(x, y, z)
        if (ticks.isNaN()) {
            return false
        }

        breaks += BlockPos(x, y, z)
        breakCost += ticks + Costs.BREAK_OVERHEAD
        return true
    }

    /**
     * Ticks to break the cell; NaN when it must not be broken. Only [floor] allows the block the walker stands on.
     */
    fun breakTicks(x: Int, y: Int, z: Int, floor: Boolean = false): Double {
        val stoodOn = x == from.x && z == from.z && y == from.y - 1
        if (!abilities.canBreak || !nodes.isHarmless(x, y, z) || stoodOn && !floor || !nodes.isDry(x, y, z) ||
            BlockPos.asLong(x, y, z) in abilities.unbreakable
        ) {
            return Double.NaN
        }

        val state = terrain.state(x, y, z)
        if (state.isAir || terrain.state(x, y + 1, z).block.`is`(BlockKind.FALLING)) {
            return Double.NaN
        }
        val ticks = abilities.breakTicks.applyAsDouble(state)
        return if (ticks.isFinite()) ticks else Double.NaN
    }

    /**
     * Whether a block placed at [x] [y] [z] has a side to place it against; the floor stood on counts if [onFloor].
     */
    fun canPlaceAgainst(x: Int, y: Int, z: Int, onFloor: Boolean): Boolean {
        if (nodes.isSolid(x, y - 1, z)) {
            return true
        }

        for (i in Nodes.DX.indices) {
            val nx = x + Nodes.DX[i]
            val nz = z + Nodes.DZ[i]
            if (onFloor && nx == from.x && nz == from.z && y == from.y - 1 || nodes.isSolid(nx, y, nz)) {
                return true
            }
        }
        return false
    }

}
