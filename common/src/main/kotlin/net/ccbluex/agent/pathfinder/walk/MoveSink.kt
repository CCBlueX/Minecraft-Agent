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

/**
 * From LiquidBounce: [Moves.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Moves.kt#L39-L49)
 *
 * @agent claude-opus-5-5
 */
internal fun interface MoveSink {
    /**
     * The lists are reused; copy them to keep them.
     */
    fun accept(
        to: BlockPos,
        move: Move,
        cost: Double,
        breaks: List<BlockPos>,
        places: List<BlockPos>,
        opens: List<BlockPos>,
        jump: Jump?,
    )
}
