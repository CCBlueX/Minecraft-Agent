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
package net.ccbluex.agent.skills.build

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.ObserverBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.ccbluex.agent.world.block.BlockState as AgentState

/**
 * What a build counts as done and in which order it goes: blocks other blocks make, double chests, clicked and
 * waterlogged blocks, observers and liquids.
 *
 * @agent claude-opus-5-5
 */
class BuildingTest {

    init {
        MinecraftBootstrap.ensureInitialized()
    }

    private val at = BlockPos(0, 1, 0)

    private fun ground() = TestWorld().fill(-5, 0, -5, 5, 0, 5)

    private fun TestWorld.put(pos: BlockPos, state: BlockState) = set(pos.x, pos.y, pos.z, state)

    private fun BlockState.agent(): AgentState = toAgent()

    @Test
    fun `piston heads, upper halves and flowing liquids follow from other blocks`() {
        assertTrue(follows(Blocks.PISTON_HEAD.defaultBlockState().agent()))
        assertTrue(follows(Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER).agent()))
        assertTrue(follows(Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 3).agent()))
        assertFalse(follows(Blocks.WATER.defaultBlockState().agent()))
        assertFalse(follows(Blocks.TALL_GRASS.defaultBlockState().agent()))
        assertFalse(follows(Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.EXTENDED, true).agent()))
    }

    @Test
    fun `half a double chest is placed right while the other half is missing`() {
        val left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT)
        val single = left.setValue(ChestBlock.TYPE, ChestType.SINGLE)
        val world = ground().put(at, single)
        assertTrue(isPlaced(world, at, single.agent(), left.agent()))
        // The left half's partner is clockwise of its facing: east of a chest facing north
        world.put(at.east(), single)
        assertFalse(isPlaced(world, at, single.agent(), left.agent()))
        assertTrue(isPlaced(world, at, left.agent(), left.agent()))
    }

    @Test
    fun `a block is built once clicked and holding its water`() {
        val stair = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
        val wet = stair.setValue(StairBlock.WATERLOGGED, true)
        assertFalse(isBuilt(ground().put(at, stair), at, wet.agent()))
        assertTrue(isBuilt(ground().put(at, wet), at, wet.agent()))
        val trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
        assertFalse(isBuilt(ground().put(at, trapdoor), at, trapdoor.setValue(TrapDoorBlock.OPEN, true).agent()))
        val repeater = Blocks.REPEATER.defaultBlockState()
        assertTrue(isPlaced(ground(), at, repeater.agent(), repeater.setValue(RepeaterBlock.DELAY, 3).agent()))
        assertFalse(isBuilt(ground().put(at, repeater), at, repeater.setValue(RepeaterBlock.DELAY, 3).agent()))
        // A lever is switched as the schematic had it: a farm saved running needs its pistons out while it is built
        val lever = Blocks.LEVER.defaultBlockState()
        assertFalse(isBuilt(ground().put(at, lever), at, lever.setValue(BlockStateProperties.POWERED, true).agent()))
        assertTrue(isPlaced(ground(), at, lever.agent(), lever.setValue(BlockStateProperties.POWERED, true).agent()))
    }

    @Test
    fun `an observer waits for the block it watches`() {
        val observer = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST).agent()
        val wanted = mapOf(at to observer, at.east() to Blocks.STONE.defaultBlockState().agent())
        assertFalse(isReady(ground(), wanted, at, observer))
        assertTrue(isReady(ground().put(at.east(), Blocks.STONE.defaultBlockState()), wanted, at, observer))
        // One behind it may come later: the observer does not watch that side
        assertTrue(isReady(ground(), mapOf(at to observer, at.west() to Blocks.STONE.defaultBlockState().agent()), at, observer))
    }

    @Test
    fun `a liquid waits for the blocks beside and under it, and none closes it from above`() {
        val lava = Blocks.LAVA.defaultBlockState().agent()
        val stone = Blocks.STONE.defaultBlockState()
        val wanted = mapOf(at to lava, at.north() to stone.agent(), at.above() to stone.agent())
        assertFalse(isReady(ground(), wanted, at, lava))
        val walled = ground().put(at.north(), stone)
        assertTrue(isReady(walled, wanted, at, lava))
        assertFalse(isReady(walled, wanted, at.above(), stone.agent()))
        assertTrue(isReady(walled.put(at, Blocks.LAVA.defaultBlockState()), wanted, at.above(), stone.agent()))
    }

    @Test
    fun `clicks go on the outline of the block clicked`() {
        val chest = Blocks.CHEST.defaultBlockState().agent()
        val bounds = chest.getShape(ground(), at).bounds()
        val points = facePoints(bounds, at, net.ccbluex.agent.math.Direction.EAST)
        // A chest stops a sixteenth short of its block; a point on the block's edge would be in the air before it
        assertTrue(points.all { it.x == at.x + 15.0 / 16 })
        assertTrue(points.size == 3)
        val comparator = Blocks.COMPARATOR.defaultBlockState().agent()
        assertTrue(facePoints(comparator.getShape(ground(), at).bounds(), at, net.ccbluex.agent.math.Direction.EAST).size == 1)
    }

    @Test
    fun `the top of a hopper is clicked on its rim`() {
        val world = ground().put(at, Blocks.HOPPER.defaultBlockState())
        val hopper = Blocks.HOPPER.defaultBlockState().agent()
        val points = facePoints(hopper.getShape(world, at).bounds(), at, net.ccbluex.agent.math.Direction.UP)
        // The middle of its top is the hollow of the funnel; a point beside each edge lies on the rim
        assertTrue(points.size == 5)
        assertTrue(points.drop(1).all { point ->
            val x = point.x - at.x
            val z = point.z - at.z
            point.y == at.y + 1.0 && (x == 1.0 / 16 || x == 15.0 / 16 || z == 1.0 / 16 || z == 15.0 / 16)
        })
    }

    @Test
    fun `scaffold in the way is the cell and the stand columns straight out or one aside`() {
        assertTrue(isInStand(at, at))
        assertTrue(isInStand(at.offset(3, -3, 0), at))
        assertTrue(isInStand(at.offset(0, 2, -2), at))
        assertTrue(isInStand(at.offset(1, 0, 3), at))
        assertFalse(isInStand(at.offset(2, 0, 2), at))
        assertFalse(isInStand(at.offset(4, 0, 0), at))
        assertFalse(isInStand(at.offset(0, -4, 1), at))
    }
}
