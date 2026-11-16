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

package net.ccbluex.agent.pathfinder.route

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.distSqr
import net.ccbluex.agent.pathfinder.level.BlockRegion
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.world.LevelBlocks
import kotlin.math.sqrt

/**
 * Where a route should end, in blocks the feet are in.
 *
 * From LiquidBounce: [Goal.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/Goal.kt#L34-L47)
 *
 * @agent claude-fable-5-2
 */
public interface Destination {

    public fun isGoal(x: Int, y: Int, z: Int): Boolean

    /**
     * Blocks from [x] [y] [z] to the goal; never more than the real distance.
     */
    public fun distance(x: Int, y: Int, z: Int): Double

    /**
     * Sizes the searched region.
     */
    public val ends: Collection<Vec3>

    public fun isGoal(pos: BlockPos): Boolean = isGoal(pos.x, pos.y, pos.z)

    public fun distance(pos: BlockPos): Double = distance(pos.x, pos.y, pos.z)

    public fun region(start: Vec3, maxLength: Double, verticalMargin: Int): BlockRegion =
        BlockRegion.around(start, ends, maxLength, verticalMargin)

}

/**
 * From LiquidBounce: [Goal.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/Goal.kt#L51-L67)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
public class BlockDestination(positions: Collection<BlockPos>) : Destination {

    private val set = positions.toSet()

    override val ends: Collection<Vec3> = set.map(Vec3::atBottomCenterOf)

    override fun isGoal(x: Int, y: Int, z: Int): Boolean = BlockPos(x, y, z) in set

    override fun distance(x: Int, y: Int, z: Int): Double = set.minOf { goal ->
        goal.distSqr(x, y, z)
    }.let { sqrt(it.toDouble()) }

    override fun equals(other: Any?): Boolean = other is BlockDestination && other.set == set

    override fun hashCode(): Int = set.hashCode()

}

/**
 * Feet within [radius] of [center]; vertically from its block, as feet on farmland rest below the block top.
 *
 * From LiquidBounce: [Goal.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/Goal.kt#L72-L87)
 *
 * @agent claude-fable-5-2
 */
public data class NearDestination(public val center: Vec3, public val radius: Double) : Destination {

    override val ends: Collection<Vec3> = listOf(center)

    private val centerY = Mth.floor(center.y)

    override fun isGoal(x: Int, y: Int, z: Int): Boolean = feetDistance(x, y, z) <= radius

    override fun distance(x: Int, y: Int, z: Int): Double = (feetDistance(x, y, z) - radius).coerceAtLeast(0.0)

    private fun feetDistance(x: Int, y: Int, z: Int): Double {
        val dx = x + 0.5 - center.x
        val dy = (y - centerY).toDouble()
        val dz = z + 0.5 - center.z
        return Mth.length(dx, dy, dz)
    }

}

/**
 * [goal], or a stage on the way to it: feet [range] blocks from [start] or next to blocks [terrain] does not know,
 * and at least [MIN_PROGRESS] blocks closer to [goal], so stages never lead back.
 *
 * From LiquidBounce: [Goal.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/route/Goal.kt#L98-L131)
 *
 * @agent claude-fable-5-2
 */
public class StageDestination(
    public val goal: Destination,
    private val start: Vec3,
    private val range: Double,
    private val terrain: Terrain,
) : Destination {

    private val maxStageDistance = goal.distance(BlockPos.containing(start)) - MIN_PROGRESS

    override val ends: Collection<Vec3>
        get() = goal.ends

    override fun isGoal(x: Int, y: Int, z: Int): Boolean =
        goal.isGoal(x, y, z) || isStage(x, y, z) && goal.distance(x, y, z) <= maxStageDistance

    // To [goal], which heads the search for the stage with the cheapest way on.
    override fun distance(x: Int, y: Int, z: Int): Double = goal.distance(x, y, z)

    private fun isStage(x: Int, y: Int, z: Int): Boolean {
        val dx = x + 0.5 - start.x
        val dz = z + 0.5 - start.z
        return dx * dx + dz * dz >= range * range ||
            !terrain.isKnown(x + 1, y, z) || !terrain.isKnown(x - 1, y, z) ||
            !terrain.isKnown(x, y, z + 1) || !terrain.isKnown(x, y, z - 1)
    }

    public companion object {
        private const val MIN_PROGRESS = 2.0

        public fun region(start: Vec3, range: Double, verticalMargin: Int): BlockRegion =
            BlockRegion.around(start, emptyList(), range, verticalMargin)

        /**
         * Whether [goal] is farther than [range] from [start] or in blocks [level] does not know.
         *
         * @agent claude-fable-5-2
         * @agent claude-opus-5-5
         */
        public fun isNeeded(level: LevelBlocks, start: Vec3, goal: Vec3, range: Double): Boolean {
            if (start.distanceToSqr(goal) >= range * range) {
                return true
            }
            val block = BlockPos.containing(goal)
            return !level.isKnown(block.x, block.y, block.z)
        }
    }

}
