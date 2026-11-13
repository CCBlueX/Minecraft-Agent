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

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
class DefenceTest {

    private val head = BlockPos(0, 1, 0)
    private val foot = BlockPos(1, 1, 0)

    @BeforeTest
    fun bootstrap() = MinecraftBootstrap.ensureInitialized()

    /**
     * A stone floor with a bed whose head lies at [head] and foot at [foot].
     */
    private fun world(): TestWorld = TestWorld.flat().withBed()

    private fun TestWorld.withBed(): TestWorld = set(0, 1, 0, bed(BedPart.HEAD)).set(1, 1, 0, bed(BedPart.FOOT))

    private fun bed(part: BedPart) = Blocks.BED.pick(DyeColor.RED).defaultBlockState()
        .setValue(BedBlock.FACING, Direction.WEST)
        .setValue(BedBlock.PART, part)

    @Test
    fun `both bed halves are found from either one`() {
        val world = world()
        assertEquals(listOf(head, foot), bedHalves(world, head))
        assertEquals(listOf(foot, head), bedHalves(world, foot))
        assertEquals(listOf(BlockPos(5, 1, 5)), bedHalves(world, BlockPos(5, 1, 5)))
    }

    @Test
    fun `a wool cap is the ring of cells touching the bed at or above it`() {
        val spots = defenceSpots(listOf(head, foot), Shape.WOOL_CAP) { true }
        val expected = setOf(
            BlockPos(0, 2, 0), BlockPos(1, 2, 0),
            BlockPos(-1, 1, 0), BlockPos(2, 1, 0),
            BlockPos(0, 1, -1), BlockPos(0, 1, 1), BlockPos(1, 1, -1), BlockPos(1, 1, 1),
        )
        assertEquals(expected, spots.keys)
        assertTrue(spots.values.all { it.layer == 1 && it.fill == Fill.WOOL })
    }

    @Test
    fun `every layer is a shell of its own fill, inner layers and lower cells first`() {
        for (shape in Shape.entries) {
            val spots = defenceSpots(listOf(head, foot), shape) { true }.values.toList()
            for (spot in spots) {
                val layer = minOf(distance(spot.pos, head), distance(spot.pos, foot))
                assertEquals(layer, spot.layer, "$shape ${spot.pos}")
                assertEquals(shape.layers[layer - 1], spot.fill, "$shape ${spot.pos}")
                assertTrue(spot.pos.y >= head.y, "$shape ${spot.pos}")
            }
            assertEquals(shape.layers.size, spots.maxOf { it.layer }, "$shape")
            val order = spots.map { it.layer to it.pos.y }
            assertEquals(order.sortedWith(compareBy({ it.first }, { it.second })), order, "$shape")
        }
    }

    @Test
    fun `cells the game refuses are left out`() {
        val spots = defenceSpots(listOf(head, foot), Shape.FORT) { it.y < 3 }
        assertTrue(spots.keys.all { it.y < 3 })
        assertTrue(spots.keys.isNotEmpty())
    }

    @Test
    fun `shapes are rolled by their weights`() {
        assertEquals(Shape.WOOL_CAP, Shape.roll(fixed(0)))
        assertEquals(Shape.WOOL_CAP, Shape.roll(fixed(4)))
        assertEquals(Shape.DOUBLE_WOOL, Shape.roll(fixed(5)))
        assertEquals(Shape.WOOD_SHELL, Shape.roll(fixed(9)))
        assertEquals(Shape.END_STONE_SHELL, Shape.roll(fixed(15)))
        assertEquals(Shape.HARD_CORE, Shape.roll(fixed(20)))
        assertEquals(Shape.FORT, Shape.roll(fixed(24)))
    }

    @Test
    fun `teammates take different blocks, lowest first, and keep their own`() {
        val world = world()
        val plan = TeamPlan("Red", Shape.WOOL_CAP)
        plan.locate(world, head) { true }
        val near = Vec3(0.5, 1.0, 3.5)
        val first = assertNotNull(plan.nextDefence(world, "a", near) { false })
        assertEquals(1, first.pos.y, "the ring goes down before the top")
        plan.claim("a", first.pos)
        val second = assertNotNull(plan.nextDefence(world, "b", near) { false })
        assertNotEquals(first.pos, second.pos)
        assertEquals(first, plan.nextDefence(world, "a", Vec3(10.0, 1.0, 10.0)) { false })
    }

    @Test
    fun `the next layer starts once the inner one is done`() {
        val world = world()
        val plan = TeamPlan("Red", Shape.WOOD_SHELL)
        plan.locate(world, head) { true }
        assertEquals(1, plan.layer(world))
        assertEquals(mapOf(Fill.WOOL to 8, Fill.WOOD to plan.defence.values.count { it.layer == 2 }), plan.lacking(world))
        val wool = Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState()
        for (spot in plan.defence.values.filter { it.layer == 1 }) world.set(spot.pos.x, spot.pos.y, spot.pos.z, wool)
        assertEquals(2, plan.layer(world))
        val next = assertNotNull(plan.nextDefence(world, "a", Vec3.ZERO) { false })
        assertEquals(Fill.WOOD, next.fill)
    }

    @Test
    fun `a block that keeps failing is given up`() {
        val world = world()
        val plan = TeamPlan("Red", Shape.WOOL_CAP)
        plan.locate(world, head) { true }
        val open = plan.openSpots(world)
        val pos = BlockPos(-1, 1, 0)
        repeat(2) { plan.failed(pos) }
        assertEquals(open, plan.openSpots(world))
        plan.failed(pos)
        assertEquals(open - 1, plan.openSpots(world))
    }

    @Test
    fun `floating cells wait for support`() {
        val world = TestWorld().withBed()
        val plan = TeamPlan("Red", Shape.WOOL_CAP)
        plan.locate(world, head) { true }
        // Without a floor only the cells beside or on the bed have something to go against
        val next = assertNotNull(plan.nextDefence(world, "a", Vec3.ZERO) { false })
        assertNotNull(support(world, next.pos))
        assertNull(support(world, BlockPos(5, 5, 5)))
    }

    @Test
    fun `standing in a cell of the target's layer or inside it is in the way`() {
        val plan = TeamPlan("Red", Shape.DOUBLE_WOOL)
        plan.locate(world(), head) { true }
        val inner = BlockPos(-1, 1, 0)
        val outer = BlockPos(-2, 1, 0)
        assertTrue(plan.isInTheWay(inner, outer))
        assertTrue(plan.isInTheWay(outer, outer))
        assertTrue(!plan.isInTheWay(outer, inner))
        assertTrue(!plan.isInTheWay(BlockPos(9, 1, 9), inner))
    }

    @Test
    fun `members count apart from the asking one`() {
        val plan = TeamPlan("Red", Random(1))
        plan.doing("a", Activity.RUSH, "Blue")
        plan.doing("b", Activity.RUSH, "Blue")
        plan.doing("c", Activity.COVER, null)
        assertEquals(1, plan.count(Activity.RUSH, "a"))
        assertEquals(2, plan.count(Activity.RUSH, null))
        assertEquals(1, plan.goingFor("Blue", "b"))
        plan.leave("a")
        assertEquals(0, plan.goingFor("Blue", "b"))
        assertEquals(listOf<Any>("c"), plan.others("b"))
    }

    private fun distance(a: BlockPos, b: BlockPos) = abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)

    private fun fixed(value: Int) = object : Random() {
        override fun nextInt(bound: Int): Int = value
    }
}
