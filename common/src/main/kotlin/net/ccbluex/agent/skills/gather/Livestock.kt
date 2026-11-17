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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.entity.Animal
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.BlockGetter

/**
 * Whether [animal] belongs to a player and is not to be hunted: named, on a lead, or kept in a pen.
 *
 * @agent claude-opus-5-5
 */
internal fun isLivestock(animal: Animal): Boolean =
    animal.customName != null || animal.isLeashed || isPenned(animal.level(), animal.blockPosition())

/**
 * Whether a fence, gate or wall stands within 5 blocks of [pos] around its height, as around a pen or a farm.
 *
 * @agent claude-opus-5-5
 */
internal fun isPenned(level: BlockGetter, pos: BlockPos): Boolean =
    BlockPos.betweenClosed(pos.offset(-5, -1, -5), pos.offset(5, 1, 5)).any {
        val block = level.getBlockState(it).block
        block.`is`(BlockKind.FENCE) || block.`is`(BlockKind.FENCE_GATE) || block.`is`(BlockKind.WALL)
    }
