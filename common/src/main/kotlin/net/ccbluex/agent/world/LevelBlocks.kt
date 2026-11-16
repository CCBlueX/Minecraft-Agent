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

package net.ccbluex.agent.world

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.block.BlockEntityType
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.FluidState

/**
 * The blocks a route may be planned through. Unknown blocks are never entered.
 *
 * From LiquidBounce: [WorldBlocks.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/world/WorldBlocks.kt#L30-L36)
 *
 * @agent claude-opus-5-5
 */
public interface LevelBlocks : BlockGetter {

    public fun isKnown(x: Int, y: Int, z: Int): Boolean

    public fun isInsideBorder(x: Double, z: Double): Boolean

}

/**
 * The world as it is now. Only for the thread that ticks [level].
 *
 * From LiquidBounce: [WorldBlocks.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/world/WorldBlocks.kt#L39-L56)
 *
 * @agent claude-opus-5-5
 */
public class LiveLevel(private val level: Level) : LevelBlocks {

    override fun isKnown(x: Int, y: Int, z: Int): Boolean =
        y >= level.minY && y <= level.maxY && level.hasChunkAt(x, z)

    override fun isInsideBorder(x: Double, z: Double): Boolean = level.worldBorder.isWithinBounds(x, z)

    override fun getBlockEntity(pos: BlockPos): BlockEntityType? = level.getBlockEntity(pos)

    override fun getBlockState(pos: BlockPos): BlockState = level.getBlockState(pos)

    override fun getFluidState(pos: BlockPos): FluidState = level.getFluidState(pos)

    override fun clip(context: ClipContext): BlockHitResult = level.clip(context)

    override val height: Int get() = level.height

    override val minY: Int get() = level.minY

}
