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

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * @agent claude-opus-5-5
 */
public enum class Move {
    TRAVERSE,
    DIAGONAL,
    ASCEND,
    FALL,
    PARKOUR,
    PILLAR,
    DOWNWARD,
    SWIM,
    CLIMB_UP,
    CLIMB_DOWN,
}

/**
 * From LiquidBounce: [Path.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Path.kt#L43-L61)
 *
 * @agent claude-opus-5-5
 */
public class Step(
    public val from: BlockPos,
    public val to: BlockPos,
    public val move: Move,
    public val feet: Vec3,
    public val breaks: List<BlockPos>,
    public val places: List<BlockPos>,

    /**
     * Closed doors, trapdoors and fence gates to open before the move; a door by its lower half.
     */
    public val opens: List<BlockPos>,
    public val cost: Double,

    /**
     * Through a gap only a crouching player fits.
     */
    public val shift: Boolean = false,

    /**
     * Level past a deadly drop: walked with safe walk, which keeps to the edge as sneaking does but at full speed.
     */
    public val edge: Boolean = false,
    public val isUnderWater: Boolean = false,
    public val jump: Jump? = null,
) {
    /**
     * A level walk onto a floor placed on the way, which another feature such as Scaffold can place instead.
     */
    public val isBridge: Boolean
        get() = move == Move.TRAVERSE && to.y == from.y && breaks.isEmpty() && opens.isEmpty() && jump == null &&
            places.size == 1 && places[0] == to.below()
}

/**
 * A walking path; an incomplete one ends at the reachable spot closest to the goal.
 *
 * From LiquidBounce: [Path.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Path.kt#L67-L78)
 *
 * @agent claude-opus-5-5
 */
public class Route(
    public val start: Vec3,
    public val steps: List<Step>,
    public val canReach: Boolean,
    public val abilities: WalkAbilities,
) {

    public val end: Vec3
        get() = steps.lastOrNull()?.feet ?: start

    public val points: List<Vec3> by lazy { listOf(start) + steps.map(Step::feet) }

    public val cost: Double
        get() = steps.sumOf(Step::cost)

}
