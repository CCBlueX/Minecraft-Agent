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

import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.world.block.BlockState
import java.util.function.ToDoubleFunction

/**
 * What the walker may do, captured on the game thread for one search. Falls are in blocks, [air] in ticks.
 *
 * From LiquidBounce: [Abilities.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Abilities.kt#L29-L51)
 *
 * @agent claude-opus-5-5
 */
public class WalkAbilities(
    public val canBreak: Boolean,
    public val canPlace: Boolean,
    public val placeableBlocks: Int,
    public val canParkour: Boolean,
    public val riskyJumps: Boolean = false,
    public val canSprint: Boolean,
    public val safeFall: Int,
    public val waterFall: Int,

    /**
     * Ticks to mine a block with the best tool at hand; infinite when it must not or cannot be mined.
     */
    public val breakTicks: ToDoubleFunction<BlockState>,

    /**
     * Feet blocks the walker got stuck at; moves into them cost extra.
     */
    public val avoid: LongSet,
    public val air: Int = Water.MAX_AIR,
    public val motion: MotionTraits = MotionTraits.DEFAULT,

    /**
     * Whether holding jump climbs a ladder or vine; before 1.14 only pushing against a block beside it does.
     */
    public val jumpClimbs: Boolean = true,

    /**
     * Extra ticks per floor block a level walk places; few where another feature runs bridges across.
     */
    public val bridgeTicks: Double = Costs.PLACE,

    /**
     * Blocks the server put back after the walker broke them, which are never planned to break again.
     */
    public val unbreakable: LongSet = LongSets.EMPTY_SET,

    /**
     * Health the walker has; a fall deeper than [safeFall] is taken only while it leaves more than half a full bar.
     */
    public val health: Float = 0f,
) {

    /**
     * These abilities with the player moving as [motion] says, such as at the speed it was measured to walk.
     */
    public fun withMotion(motion: MotionTraits): WalkAbilities = WalkAbilities(
        canBreak, canPlace, placeableBlocks, canParkour, riskyJumps, canSprint, safeFall, waterFall, breakTicks, avoid,
        air, motion, jumpClimbs, bridgeTicks, unbreakable, health,
    )
}
