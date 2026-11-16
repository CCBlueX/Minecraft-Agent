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
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ifNaN
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.terrain.hasRoom
import net.ccbluex.agent.pathfinder.terrain.isOpen
import net.ccbluex.agent.pathfinder.walk.Course.Companion.HALF_BLOCK
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.entity.Entity.Companion.DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5
import kotlin.math.max

/**
 * What a feet block is to the walker: stood in, swum in, climbed or crouched through, and where a fall from it ends.
 * Only looks at [terrain]. Not thread-safe.
 *
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L505-L609)
 *
 * @agent claude-opus-5-5
 */
internal class Nodes(private val terrain: Terrain, private val abilities: WalkAbilities) {

    val water = Water(terrain, abilities.canSprint)

    private val canPlace = abilities.canPlace && abilities.placeableBlocks > 0
    private val maxDrop = max(abilities.safeFall, abilities.waterFall) + 1
    private val halfWidth = terrain.width / 2.0

    fun isNode(x: Int, y: Int, z: Int) = stands(x, y, z) || swims(x, y, z) || climbs(x, y, z) || crouches(x, y, z)

    fun isUnderwater(pos: BlockPos) = swims(pos.x, pos.y, pos.z) && water.isSubmerged(pos.x, pos.y, pos.z)

    fun stands(x: Int, y: Int, z: Int) = !terrain.standHeight(x, y, z).isNaN()

    fun swims(x: Int, y: Int, z: Int) = water.fits(x, y, z)

    fun climbs(x: Int, y: Int, z: Int) = terrain.isClimbable(x, y, z) && terrain.hasRoom(x, y, z)

    fun crouches(x: Int, y: Int, z: Int) = !stands(x, y, z) && !terrain.crouchHeight(x, y, z).isNaN() &&
        !terrain.isWater(x, y, z) && !terrain.isClimbable(x, y, z)

    /**
     * Feet height in [x] [y] [z]; a node the terrain does not have yet stands on its floor or a placed block.
     */
    fun height(x: Int, y: Int, z: Int): Double {
        val stand = terrain.standHeight(x, y, z)
        return when {
            !stand.isNaN() -> stand
            swims(x, y, z) || climbs(x, y, z) -> y.toDouble()
            else -> terrain.crouchHeight(x, y, z).ifNaN { floorTop(x, y, z).ifNaN { y.toDouble() } }
        }
    }

    /**
     * Where the feet go in [x] [y] [z]: the middle of the block, or of the room a ladder's board leaves in it, so a
     * player stopping near there drops past the board instead of standing on its top edge.
     */
    fun feet(x: Int, y: Int, z: Int): Vec3 {
        val shape = terrain.shape(x, y, z)
        if (shape.isEmpty || !climbs(x, y, z)) {
            return Vec3(x + HALF_BLOCK, height(x, y, z), z + HALF_BLOCK)
        }
        val board = shape.bounds()
        return Vec3(x + roomMiddle(board.minX, board.maxX), height(x, y, z), z + roomMiddle(board.minZ, board.maxZ))
    }

    /**
     * The middle of what a board from [min] to [max] against one side of a block leaves of its width.
     */
    private fun roomMiddle(min: Double, max: Double) = when {
        min > 0.0 && max >= 1.0 -> min / 2.0
        max < 1.0 && min <= 0.0 -> (max + 1.0) / 2.0
        else -> HALF_BLOCK
    }

    /**
     * Top of the block below [x] [y] [z] the feet would rest on, whatever is in the feet block; NaN without one.
     */
    fun floorTop(x: Int, y: Int, z: Int): Double {
        if (!isHarmless(x, y - 1, z)) {
            return Double.NaN
        }

        var top = Double.NaN
        for (box in terrain.shape(x, y - 1, z).toAabbs()) {
            val boxTop = y - 1 + box.maxY
            val inFootprint = box.minX < 0.5 + halfWidth && box.maxX > 0.5 - halfWidth &&
                box.minZ < 0.5 + halfWidth && box.maxZ > 0.5 - halfWidth
            if (inFootprint && boxTop >= y && boxTop < y + 1 && !(boxTop <= top)) {
                top = boxTop
            }
        }
        return top
    }

    /**
     * The block below feet at [height] in column [x] [z] that sets the speed, friction and jump of the player.
     */
    fun floorBlock(x: Int, z: Int, height: Double): Block =
        terrain.state(x, Mth.floor(height - DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5), z).block

    /**
     * Where a fall down column [x] [z] from block [top] with the feet at [height] ends, null unless safely.
     */
    fun landing(x: Int, top: Int, z: Int, height: Double): Landing? {
        for (y in top downTo max(top - maxDrop, terrain.minY)) {
            val stand = terrain.standHeight(x, y, z)
            return when {
                swims(x, y, z) -> Landing(y, height - y).takeIf { it.drop <= abilities.waterFall }
                !stand.isNaN() -> Landing(y, height - stand).takeIf { it.drop <= abilities.safeFall }
                climbs(x, y, z) -> Landing(y, height - y)
                terrain.isPassable(x, y, z) -> continue
                else -> null
            }
        }

        return null
    }

    /**
     * Whether a player in [x] [y] [z] with the feet at [height] can walk off a side into a deadly fall.
     */
    fun isNearDrop(x: Int, y: Int, z: Int, height: Double): Boolean {
        for (i in DX.indices) {
            val nx = x + DX[i]
            val nz = z + DZ[i]
            // Servers may never send the empty chunks of the void, so an unloaded side counts as one.
            if (!terrain.isKnown(nx, y, nz)) {
                return true
            }
            val open = terrain.isOpen(nx, y, nz) && floorTop(nx, y, nz).isNaN() && !terrain.isWater(nx, y, nz)
            if (open && landing(nx, y - 1, nz, height) == null) {
                return true
            }
        }
        return false
    }

    fun isHarmless(x: Int, y: Int, z: Int) = terrain.isKnown(x, y, z) && !terrain.isHazard(x, y, z)

    fun isSolid(x: Int, y: Int, z: Int) = isHarmless(x, y, z) && !terrain.isPassable(x, y, z)

    fun isPlaceable(x: Int, y: Int, z: Int) =
        canPlace && isHarmless(x, y, z) && terrain.state(x, y, z).canBeReplaced()

    fun isDry(x: Int, y: Int, z: Int): Boolean {
        if (!terrain.state(x, y, z).fluidState.isEmpty || !terrain.state(x, y + 1, z).fluidState.isEmpty) {
            return false
        }
        for (i in DX.indices) {
            if (!terrain.state(x + DX[i], y, z + DZ[i]).fluidState.isEmpty) {
                return false
            }
        }
        return true
    }

    class Landing(val y: Int, val drop: Double)

    companion object {
        val DX = intArrayOf(1, -1, 0, 0)
        val DZ = intArrayOf(0, 0, 1, -1)
    }

}
