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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.block.BedPart
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.BlockStateProperties
import java.util.Random
import kotlin.math.abs

/**
 * What a layer of the bed defence is made of.
 *
 * @agent claude-opus-5-5
 */
internal enum class Fill { WOOL, WOOD, END_STONE }

/**
 * How a team covers its bed in a round: each layer a shell around the one inside it, the first around both bed
 * halves, made of the shape's fills from the inside out. A layer starts once the one inside it is done, so the cover
 * grows as the team gets the blocks for it.
 *
 * @agent claude-opus-5-5
 */
internal enum class Shape(private val weight: Int, vararg layers: Fill) {
    WOOL_CAP(5, Fill.WOOL),
    DOUBLE_WOOL(4, Fill.WOOL, Fill.WOOL),
    WOOD_SHELL(6, Fill.WOOL, Fill.WOOD),
    END_STONE_SHELL(5, Fill.WOOL, Fill.END_STONE),
    HARD_CORE(2, Fill.END_STONE, Fill.WOOL),
    FORT(3, Fill.WOOL, Fill.WOOD, Fill.END_STONE);

    val layers: List<Fill> = layers.toList()

    companion object {
        fun roll(random: Random): Shape {
            var pick = random.nextInt(entries.sumOf { it.weight })
            for (shape in entries) {
                pick -= shape.weight
                if (pick < 0) return shape
            }
            return WOOL_CAP
        }
    }
}

/**
 * A block of the bed defence: layer 1 touches the bed, each next layer covers the one before.
 *
 * @agent claude-opus-5-5
 */
internal data class Spot(val pos: BlockPos, val layer: Int, val fill: Fill)

/**
 * The cells around both bed halves by their block distance to the nearer half, as many layers as the shape has, at
 * or above the bed, without those [canPlace] refuses. Ordered by layer, then height.
 *
 * @agent claude-opus-5-5
 */
internal fun defenceSpots(halves: List<BlockPos>, shape: Shape, canPlace: (BlockPos) -> Boolean): Map<BlockPos, Spot> {
    val head = halves.firstOrNull() ?: return emptyMap()
    val layers = shape.layers.size
    val spots = ArrayList<Spot>()
    for (dx in -layers - 1..layers + 1) {
        for (dz in -layers - 1..layers + 1) {
            for (dy in 0..layers) {
                val pos = head.offset(dx, dy, dz)
                val layer = halves.minOf { abs(pos.x - it.x) + abs(pos.y - it.y) + abs(pos.z - it.z) }
                if (layer < 1 || layer > layers || !canPlace(pos)) continue
                spots += Spot(pos, layer, shape.layers[layer - 1])
            }
        }
    }
    spots.sortWith(compareBy<Spot> { it.layer }.thenBy { it.pos.y })
    return spots.associateByTo(LinkedHashMap()) { it.pos }
}

/**
 * Both halves of the bed at [pos], the given one first; only [pos] when it holds no bed.
 *
 * @agent claude-opus-5-5
 */
internal fun bedHalves(level: BlockGetter, pos: BlockPos): List<BlockPos> {
    val state = level.getBlockState(pos)
    if (!state.block.`is`(BlockKind.BED)) return listOf(pos)
    val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
    val toOther = if (state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) facing else facing.opposite
    return listOf(pos, pos.relative(toOther))
}

/**
 * @agent claude-opus-5-5
 */
internal fun isBed(level: BlockGetter, pos: BlockPos): Boolean = level.getBlockState(pos).block.`is`(BlockKind.BED)

/**
 * Whether a block collides; what Bukkit calls solid for the blocks a defence or a bridge is built of.
 *
 * @agent claude-opus-5-5
 */
internal fun isSolid(level: BlockGetter, pos: BlockPos): Boolean =
    !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty

/**
 * Whether a block may go in: nothing solid, no liquid, no bed.
 *
 * @agent claude-opus-5-5
 */
internal fun isOpen(level: BlockGetter, pos: BlockPos): Boolean =
    !isSolid(level, pos) && level.getFluidState(pos).isEmpty && !isBed(level, pos)

private val SUPPORT_ORDER = listOf(
    Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP,
)

/**
 * The side of a solid neighbour to place a block at [pos] against that [accepts], below first; null when there is
 * none, as when the block would float.
 *
 * @agent claude-opus-5-5
 */
internal fun support(level: BlockGetter, pos: BlockPos, accepts: (Direction) -> Boolean = { true }): Direction? =
    SUPPORT_ORDER.firstOrNull { isSolid(level, pos.relative(it)) && accepts(it) }
