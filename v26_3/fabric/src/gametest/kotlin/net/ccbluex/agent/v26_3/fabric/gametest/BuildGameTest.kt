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

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

private const val BUILD_TICKS = 3000

/**
 * A stepped pyramid of 5 by 5 by 3 around [centre], its lowest layer at centre's height: oak planks, then cobblestone
 * ringed by oak stairs that lead up towards the middle, twice. Its stairs start a block and a half up, too high to
 * step on from the ground, and the top layer shows no face to a player there.
 *
 * @agent claude-opus-5-5
 */
internal fun steppedPyramid(centre: BlockPos): Map<BlockPos, BlockState> {
    val blocks = HashMap<BlockPos, BlockState>()
    for (x in -2..2) for (z in -2..2) blocks[centre.offset(x, 0, z)] = Blocks.OAK_PLANKS.defaultBlockState()
    for (layer in 1..2) {
        val ring = 3 - layer
        for (x in -ring..ring) for (z in -ring..ring) {
            val edge = when {
                x == -ring -> Direction.EAST
                x == ring -> Direction.WEST
                z == -ring -> Direction.SOUTH
                z == ring -> Direction.NORTH
                else -> null
            }
            blocks[centre.offset(x, layer, z)] = edge?.let { Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, it) }
                ?: Blocks.COBBLESTONE.defaultBlockState()
        }
    }
    return blocks
}

/**
 * Clears the ground around [centre], gives the player the blocks of [steppedPyramid] and two spare dirt to stand on,
 * and stands it west of [centre].
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.prepareSite(centre: BlockPos) {
    fill(centre.offset(-8, 0, -8), centre.offset(8, 5, 8), "minecraft:air")
    fill(centre.offset(-8, -1, -8), centre.offset(8, -1, 8), "minecraft:stone")
    awaitServer("block ${centre.x + 8} ${centre.y - 1} ${centre.z + 8} minecraft:stone")
    run("clear $playerName")
    give("minecraft:oak_planks", 25)
    give("minecraft:cobblestone", 10)
    give("minecraft:oak_stairs", 24)
    // Fewer than the spots the top layer is placed from: each comes down again once the walk goes on elsewhere
    give("minecraft:dirt", 2)
    moveTo(Vec3(centre.x - 5.5, centre.y.toDouble(), centre.z + 0.5), yRot = -90f)
}

/**
 * Builds [blocks] and fails unless every one is there, facing the way it is wanted, and nothing else stands around
 * them.
 *
 * @agent claude-opus-5-5
 */
internal fun GameTestScope.buildAndCheck(blocks: Map<BlockPos, BlockState>, canBreak: Boolean) {
    val builder = context.fromClient { TestBuilder() }
    try {
        builder.start(context, blocks, canBreak)
        val took = await(context, BUILD_TICKS, "The build did not end") { builder.result != null }
        logTicks("Building ${blocks.size} blocks", took)
        val result = checkNotNull(builder.result)
        val wrong = context.fromClient { mc ->
            val level = checkNotNull(mc.level)
            blocks.filter { (pos, wanted) ->
                val actual = level.getBlockState(pos)
                actual.block != wanted.block || wanted.hasProperty(StairBlock.FACING) &&
                    (actual.getValue(StairBlock.FACING) != wanted.getValue(StairBlock.FACING) ||
                        actual.getValue(StairBlock.HALF) != wanted.getValue(StairBlock.HALF))
            }.mapValues { (pos, _) -> level.getBlockState(pos) }
        }
        check(result.isDone && wrong.isEmpty()) {
            "Left ${result.unbuilt.size} blocks unbuilt, missing ${result.missing}, scaffold missing: " +
                "${result.isMissingScaffold}; ${wrong.size} wrong: $wrong"
        }
        val scaffold = context.fromClient { mc ->
            val level = checkNotNull(mc.level)
            val min = blocks.keys.reduce(BlockPos::min)
            val max = blocks.keys.reduce(BlockPos::max)
            BlockPos.betweenClosed(min.offset(-6, 0, -6), max.offset(6, 3, 6))
                .map(BlockPos::immutable)
                .filter { it !in blocks && !level.getBlockState(it).isAir }
                .associateWith(level::getBlockState)
        }
        check(scaffold.isEmpty()) { "Blocks left standing outside the build: $scaffold" }
    } finally {
        builder.stop(context)
    }
}

/**
 * The agent builds a stepped pyramid with stairs from a list of blocks: a plank already in place stays, dirt where a
 * plank belongs is dug out, and every stair faces the way the list says.
 *
 * @agent claude-opus-5-5
 */
internal val building: GameTestScope.() -> Unit = {
    val centre = origin.offset(8, 0, 0)
    prepareSite(centre)
    setBlock(centre.offset(-2, 0, 0), "minecraft:oak_planks")
    setBlock(centre.offset(-2, 0, 1), "minecraft:dirt")
    awaitServer("block ${centre.x - 2} ${centre.y} ${centre.z + 1} minecraft:dirt")
    buildAndCheck(steppedPyramid(centre), canBreak = true)
}

/**
 * @agent claude-opus-5-5
 */
public class BuildGameTest : ServerGameTest(building)
