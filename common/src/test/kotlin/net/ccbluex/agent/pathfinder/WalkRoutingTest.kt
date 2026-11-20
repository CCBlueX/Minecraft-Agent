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

package net.ccbluex.agent.pathfinder

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.TestWorld.Companion.AIR
import net.ccbluex.agent.pathfinder.TestWorld.Companion.STONE
import net.ccbluex.agent.pathfinder.TestWorld.Companion.feet
import net.ccbluex.agent.pathfinder.route.BlockDestination
import net.ccbluex.agent.pathfinder.route.Limits
import net.ccbluex.agent.pathfinder.route.NearDestination
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.walk.Costs
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.RouteSearch
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.pathfinder.walk.isStraightWalkable
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.SlabType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * From LiquidBounce: [WalkRoutingTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/WalkRoutingTest.kt#L55-L419)
 */
class WalkRoutingTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val LIMITS = Limits(maxLength = 64.0, maxVisitedNodes = 20_000, maxComputeNanos = Long.MAX_VALUE)
        private const val MAX_SLICES = 1000
    }

    private fun abilities(
        canBreak: Boolean = false,
        canPlace: Boolean = false,
        canParkour: Boolean = false,
        avoid: LongSet = LongOpenHashSet(),
        unbreakable: LongSet = LongOpenHashSet(),
    ) = walkAbilities(canBreak, canPlace, canParkour, breakTicks = 20.0, avoid = avoid, unbreakable = unbreakable)

    private fun search(
        world: TestWorld,
        start: Vec3,
        goal: BlockPos,
        abilities: WalkAbilities = abilities(),
    ): Outcome<Route> {
        val terrain = world.terrain(canOpenDoors = true)
        return RouteSearch(terrain, start, BlockDestination(listOf(goal)), abilities, LIMITS).advanceToEnd(MAX_SLICES)
    }

    private fun isComplete(result: Outcome<Route>) =
        result is Outcome.Found && result.value.canReach

    private fun Route.moves() = steps.map { it.move }.toSet()

    @Test
    fun `walls are walked around`() {
        val world = TestWorld.flat().fill(5, 1, -5, 5, 2, 5)
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(10, 1, 0)))

        assertEquals(feet(10, 1, 0), path.end)
        assertTrue(path.steps.none { it.to.x == 5 && abs(it.to.z) <= 5 })
        assertTrue(path.steps.all { it.breaks.isEmpty() && it.places.isEmpty() })
    }

    @Test
    fun `one block steps are jumped up`() {
        val world = TestWorld.flat().fill(3, 1, -40, 40, 1, 40)
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(10, 2, 0)))

        assertTrue(Move.ASCEND in path.moves())
        assertEquals(feet(10, 2, 0), path.end)
    }

    @Test
    fun `drops of three blocks are taken, of five onto ground refused`() {
        fun cliff(height: Int) = TestWorld.flat().fill(-40, 1, -40, 0, height, 40)

        val path = completePath(search(cliff(3), feet(-2, 4, 0), BlockPos(5, 1, 0)))
        assertTrue(path.steps.any { it.move == Move.FALL && it.from.y - it.to.y == 3 })

        assertFalse(isComplete(search(cliff(5), feet(-2, 6, 0), BlockPos(5, 1, 0))))
    }

    @Test
    fun `a drop of four blocks is taken only while it leaves more than half the health`() {
        val world = TestWorld.flat().fill(-40, 1, -40, 0, 4, 40)
        val path = completePath(search(world, feet(-2, 5, 0), BlockPos(5, 1, 0), walkAbilities(health = 12f)))
        assertTrue(path.steps.any { it.move == Move.FALL && it.from.y - it.to.y == 4 })

        assertFalse(isComplete(search(world, feet(-2, 5, 0), BlockPos(5, 1, 0), walkAbilities(health = 11f))))
    }

    @Test
    fun `a hurting drop is walked around by stairs`() {
        val world = TestWorld.flat().fill(-40, 1, -40, 0, 4, 40)
            .fill(1, 1, 3, 1, 3, 3).fill(2, 1, 3, 2, 2, 3).fill(3, 1, 3, 3, 1, 3)
        val path = completePath(search(world, feet(-2, 5, 0), BlockPos(5, 1, 0), walkAbilities(health = 20f)))
        assertTrue(path.steps.none { it.move == Move.FALL && it.from.y - it.to.y > 3 })
    }

    @Test
    fun `drops of five blocks into water are taken`() {
        val world = TestWorld.flat()
            .fill(-40, 1, -40, 0, 5, 40)
            .fill(1, 1, -40, 3, 1, 40, Blocks.WATER.defaultBlockState())
        val path = completePath(search(world, feet(-2, 6, 0), BlockPos(6, 1, 0)))

        val fall = path.steps.single { it.move == Move.FALL }
        assertEquals(1, fall.to.y)
        assertTrue(world.getBlockState(fall.to).`is`(Blocks.WATER.toAgent()))
    }

    @Test
    fun `two block gaps are jumped only with parkour`() {
        // A trench two blocks deep: a missed jump lands safely but cannot climb out.
        val world = TestWorld.flat().fill(3, 0, -40, 4, 0, 40, AIR).fill(3, -2, -40, 4, -2, 40)

        val path = completePath(search(world, feet(0, 1, 0), BlockPos(8, 1, 0), abilities(canParkour = true)))
        assertTrue(Move.PARKOUR in path.moves())

        assertFalse(isComplete(search(world, feet(0, 1, 0), BlockPos(8, 1, 0))))
    }

    @Test
    fun `gaps over a deadly drop are never jumped`() {
        val world = TestWorld.flat().fill(3, 0, -40, 4, 0, 40, AIR)

        assertFalse(isComplete(search(world, feet(0, 1, 0), BlockPos(8, 1, 0), abilities(canParkour = true))))
    }

    @Test
    fun `walls are tunnelled through only when breaking is allowed`() {
        val world = TestWorld.flat().fill(5, 1, -40, 5, 10, 40)

        val path = completePath(search(world, feet(0, 1, 0), BlockPos(10, 1, 0), abilities(canBreak = true)))
        val breaks = path.steps.flatMap { it.breaks }
        assertTrue(breaks.isNotEmpty())
        assertTrue(breaks.all { it.x == 5 })

        assertFalse(isComplete(search(world, feet(0, 1, 0), BlockPos(10, 1, 0))))
    }

    @Test
    fun `a wall block the server put back is tunnelled around`() {
        val world = TestWorld.flat().fill(5, 1, -40, 5, 10, 40)
        val first = completePath(search(world, feet(0, 1, 0), BlockPos(10, 1, 0), abilities(canBreak = true)))
        val refused = LongOpenHashSet(first.steps.flatMap { it.breaks }.map { it.asLong() })

        val second = completePath(
            search(world, feet(0, 1, 0), BlockPos(10, 1, 0), abilities(canBreak = true, unbreakable = refused))
        )
        val breaks = second.steps.flatMap { it.breaks }
        assertTrue(breaks.isNotEmpty())
        assertTrue(breaks.none { refused.contains(it.asLong()) })
    }

    /**
     * A bot dug at one map block below it for 75 s, the server putting it back.
     */
    @Test
    fun `a block below the server put back is not dug again`() {
        val world = TestWorld.flat().fill(-3, 1, -3, 3, 5, 3)
        val goal = BlockPos(0, 2, 0)
        val refused = BlockPos(0, 5, 0)
        val first = completePath(search(world, feet(0, 6, 0), goal, abilities(canBreak = true)))
        assertTrue(refused in first.steps.flatMap { it.breaks })

        val unbreakable = LongOpenHashSet(longArrayOf(refused.asLong()))
        val result = search(world, feet(0, 6, 0), goal, abilities(canBreak = true, unbreakable = unbreakable))
        if (result is Outcome.Found) {
            assertTrue(result.value.steps.none { refused in it.breaks })
        }
    }

    @Test
    fun `gaps are bridged only when placing is allowed`() {
        val world = TestWorld.flat().fill(3, 0, -40, 5, 0, 40, AIR)

        val path = completePath(search(world, feet(0, 1, 0), BlockPos(8, 1, 0), abilities(canPlace = true)))
        val places = path.steps.flatMap { it.places }
        assertEquals(3, places.size)
        assertTrue(places.all { it.y == 0 && it.x in 3..5 })

        assertFalse(isComplete(search(world, feet(0, 1, 0), BlockPos(8, 1, 0))))
    }

    /**
     * @agent claude-opus-5-5
     */
    @Test
    fun `reserved cells, such as those a build is to fill, are bridged around`() {
        val world = TestWorld.flat().fill(3, 0, -40, 5, 0, 40, AIR)
        val reserved = LongOpenHashSet()
        for (x in 3..5) for (z in -1..1) reserved.add(BlockPos.asLong(x, 0, z))
        val abilities = WalkAbilities(
            canBreak = false,
            canPlace = true,
            placeableBlocks = 64,
            canParkour = false,
            canSprint = true,
            safeFall = 3,
            waterFall = 20,
            breakTicks = { Double.POSITIVE_INFINITY },
            avoid = LongOpenHashSet(),
            reserved = reserved,
        )

        val places = completePath(search(world, feet(0, 1, 0), BlockPos(8, 1, 0), abilities)).steps.flatMap { it.places }
        assertTrue(places.isNotEmpty() && places.none { reserved.contains(it.asLong()) }, "$places")
    }

    /**
     * A build walk fell into a composter and then pressed against its wall for minutes: the route bridged west out of
     * it, through the rim, as only plain traverses checked the way across the edge.
     *
     * @agent claude-opus-5-5
     */
    @Test
    fun `the rim of a composter stood in is never bridged across`() {
        val world = TestWorld.flat().fill(-3, 0, -40, -1, 0, 40, AIR).set(0, 1, 0, Blocks.COMPOSTER.defaultBlockState())
        val path = completePath(search(world, Vec3(0.5, 1.125, 0.5), BlockPos(-4, 1, 0), abilities(canPlace = true)))
        val out = path.steps.first()
        assertTrue(out.to.y > out.from.y, "${out.move} ${out.from} ${out.to} ${out.places}")
    }

    /**
     * small12: a build walk fell into a composter on the way and then planned to pillar out of it from inside.
     *
     * @agent claude-opus-5-5
     */
    @Test
    fun `no step ends in a composter`() {
        val world = TestWorld.flat().fill(0, 1, 0, 0, 2, 0).set(1, 1, 0, Blocks.COMPOSTER.defaultBlockState())
        val path = completePath(search(world, feet(0, 3, 0), BlockPos(3, 1, 0), abilities()))
        assertTrue(path.steps.none { it.to == BlockPos(1, 1, 0) }, "${path.steps}")
    }

    /**
     * small14: a build walk ended standing in the top of a hopper and every walk from there was unreachable, as the
     * rim a step high counted as a wall.
     *
     * @agent claude-opus-5-5
     */
    @Test
    fun `the rim of a hopper stood in is stepped over`() {
        val world = TestWorld.flat().set(0, 1, 0, Blocks.HOPPER.defaultBlockState())
        completePath(search(world, Vec3(0.5, 1.6875, 0.5), BlockPos(4, 1, 0), abilities()))
    }

    @Test
    fun `leaning over the corner of a bridge, the path starts on the block holding the player`() {
        // A bridge eight blocks up ending at x 5; the feet hang past its corner, held by the last block.
        val world = TestWorld.flat().fill(0, 8, -1, 4, 8, 1).set(5, 8, 0, STONE)
        val start = Vec3(6.29, 9.0, 1.29)

        val path = completePath(search(world, start, BlockPos(9, 9, 0), abilities(canPlace = true)))
        assertEquals(BlockPos(5, 9, 0), path.steps.first().from)
        assertTrue(path.steps.all { it.to.y == 9 }, "the path leaves the bridge height: ${path.steps}")
    }

    @Test
    fun `three block cliffs are pillared up`() {
        val world = TestWorld.flat().fill(3, 1, -40, 40, 3, 40)
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(6, 4, 0), abilities(canPlace = true)))

        assertTrue(Move.PILLAR in path.moves())
        assertTrue(path.steps.flatMap { it.places }.isNotEmpty())
    }

    /**
     * The walk used to pillar once where it stood and then stack two blocks beside the pillar, priced as a bridge
     * run, which from a stop takes twice as long as stepping up onto one block and pillaring there.
     */
    @Test
    fun `a three block cliff is climbed with a step up and a pillar, not a stack on the ground`() {
        val world = TestWorld.flat().fill(3, 1, -40, 40, 3, 40)
        val abilities = WalkAbilities(
            canBreak = false,
            canPlace = true,
            placeableBlocks = 64,
            canParkour = false,
            canSprint = true,
            safeFall = 3,
            waterFall = 20,
            breakTicks = { Double.POSITIVE_INFINITY },
            avoid = LongOpenHashSet(),
            bridgeTicks = Costs.NINJA_BRIDGE,
        )
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(4, 4, 0), abilities))

        val climb = path.steps.map { it.move }.dropWhile { it == Move.TRAVERSE }.take(3)
        assertEquals(listOf(Move.ASCEND, Move.PILLAR, Move.ASCEND), climb)
    }

    @Test
    fun `rises over a gap are one-stacked, the floor beside first and a block on top`() {
        // A pad eight blocks up, and past a gap of five one three blocks higher.
        val world = TestWorld.flat().fill(0, 8, -1, 2, 8, 1).fill(8, 11, -1, 10, 11, 1)
        val abilities = WalkAbilities(
            canBreak = false,
            canPlace = true,
            placeableBlocks = 64,
            canParkour = false,
            canSprint = true,
            safeFall = 3,
            waterFall = 20,
            breakTicks = { Double.POSITIVE_INFINITY },
            avoid = LongOpenHashSet(),
            bridgeTicks = Costs.NINJA_BRIDGE,
        )
        val path = completePath(search(world, feet(1, 9, 0), BlockPos(9, 12, 0), abilities))

        // Two rises over the gap, the third up onto the pad already there.
        val rises = path.steps.filter { it.move == Move.ASCEND }
        val stacked = rises.count { it.places == listOf(it.to.below(2), it.to.below()) }
        assertEquals(listOf(2, 1), listOf(stacked, rises.count { it.places.isEmpty() }), "${path.steps.map { it.move }}")
    }

    @Test
    fun `ladders are climbed`() {
        val ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH.toMinecraft())
        val world = TestWorld.flat()
            .fill(-40, 1, 1, 40, 4, 3)
            .fill(0, 1, 0, 0, 4, 0, ladder)
        val path = completePath(search(world, feet(0, 1, -3), BlockPos(0, 5, 2)))

        assertTrue(Move.CLIMB_UP in path.moves())
        assertTrue(path.steps.all { it.breaks.isEmpty() && it.places.isEmpty() })
    }

    /**
     * Aimed at the middle of a ladder shaft, the walk stopped with the box resting on the ladder's top edge and never
     * dropped in.
     */
    @Test
    fun `a drop into a ladder shaft aims where the player clears the ladder`() {
        val ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST.toMinecraft())
        val world = TestWorld.flat()
            .fill(-3, 1, -3, 3, 5, 3)
            .fill(0, 1, 0, 0, 5, 0, ladder)
            .fill(0, 1, -1, 0, 2, -1, AIR)
        val terrain = world.terrain()
        val path = completePath(search(world, feet(-2, 6, 0), BlockPos(0, 1, -1)))

        val intoLadder = path.steps.filter { world.getBlockState(it.to).`is`(Blocks.LADDER.toAgent()) }
        assertTrue(intoLadder.isNotEmpty())
        // The walk stops pressing keys this close to the feet.
        val stopsWithin = 0.1
        for (step in intoLadder) {
            for (off in listOf(-stopsWithin, stopsWithin)) {
                assertTrue(terrain.isClear(terrain.box(step.feet.add(off, 0.0, 0.0))), "${step.move} to ${step.to}")
            }
        }
    }

    @Test
    fun `a vine with nothing beside it is only climbed where jumping climbs`() {
        val world = TestWorld.flat()
            .fill(0, 1, 0, 0, 4, 0, Blocks.VINE.defaultBlockState())
            .set(1, 4, 0, STONE)
        val goal = BlockPos(1, 5, 0)

        val path = completePath(search(world, feet(-3, 1, 0), goal))
        assertTrue(Move.CLIMB_UP in path.moves())
        val pushOnly = walkAbilities(breakTicks = 20.0, jumpClimbs = false)
        assertFalse(isComplete(search(world, feet(-3, 1, 0), goal, pushOnly)))
    }

    @Test
    fun `unreachable goals give an incomplete path that ends closer`() {
        val world = TestWorld.flat()
            .fill(8, 1, -2, 12, 3, 2)
            .fill(10, 1, 0, 10, 2, 0, AIR)
        val goal = BlockPos(10, 1, 0)

        val path = found(search(world, feet(0, 1, 0), goal))
        assertFalse(path.canReach)
        val last = path.steps.last().to
        assertTrue(last.distSqr(goal) < BlockPos(0, 1, 0).distSqr(goal))
    }

    @Test
    fun `avoided blocks change the path`() {
        val avoid = LongOpenHashSet()
        for (x in 1..9) {
            avoid.add(BlockPos.asLong(x, 1, 0))
        }

        val path = completePath(
            search(TestWorld.flat(), feet(0, 1, 0), BlockPos(10, 1, 0), abilities(avoid = avoid))
        )
        assertTrue(path.steps.none { avoid.contains(it.to.asLong()) })
    }

    @Test
    fun `blocks below gravel are never broken`() {
        val world = TestWorld.flat()
            .fill(5, 1, -40, 5, 2, 40)
            .fill(5, 3, -40, 5, 3, 40, Blocks.GRAVEL.defaultBlockState())

        val result = search(world, feet(0, 1, 0), BlockPos(10, 1, 0), abilities(canBreak = true))
        assertFalse(isComplete(result))
        if (result is Outcome.Found) {
            val breaks = result.value.steps.flatMap { it.breaks }
            assertTrue(breaks.none { world.getBlockState(it.above()).`is`(Blocks.GRAVEL.toAgent()) })
        }
    }

    @Test
    fun `gaps one and a half blocks high are crouched through, one block high walked around`() {
        val slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
        fun wall() = TestWorld.flat()
            .fill(5, 1, -20, 5, 3, 20)
            .set(5, 1, 0, AIR)
        val gap = BlockPos(5, 1, 0)

        val path = completePath(search(wall().set(5, 2, 0, slab), feet(0, 1, 0), BlockPos(10, 1, 0)))
        val crouched = path.steps.filter { it.from == gap || it.to == gap }
        assertEquals(2, crouched.size, "the path should lead through the gap")
        assertTrue(crouched.all { it.shift })
        assertTrue(path.steps.none { it.shift && it.from != gap && it.to != gap })

        val low = completePath(search(wall(), feet(0, 1, 0), BlockPos(10, 1, 0)))
        assertTrue(low.steps.none { it.to == gap })
    }

    @Test
    fun `narrow bridges over deep drops are walked with safe walk`() {
        // Two platforms ten blocks over the ground, joined by a bridge one block wide.
        val world = TestWorld.flat()
            .fill(-3, 10, -3, 0, 10, 3)
            .fill(1, 10, 0, 8, 10, 0)
            .fill(9, 10, -3, 12, 10, 3)
        val path = completePath(search(world, feet(-2, 11, 0), BlockPos(11, 11, 0)))

        val bridge = path.steps.filter { it.to.x in 1..8 }
        assertTrue(bridge.isNotEmpty() && bridge.all { it.edge && !it.shift })
        assertFalse(path.steps.first().edge, "the middle of a platform is no edge")
        assertTrue(path.steps.all { it.to.y == 11 })
    }

    /**
     * Ground down to y = -6 with a wall across it at x -1..1 and a pool four blocks deep on each side of the wall,
     * joined by a flooded tunnel one block high and [length] blocks long along their bottom.
     */
    private fun pools(length: Int): TestWorld {
        val water = Blocks.WATER.defaultBlockState()
        val west = -length / 2
        val east = west + length - 1
        return TestWorld()
            .fill(-40, -6, -40, 40, 0, 40)
            .fill(-1, 1, -40, 1, 4, 40)
            .fill(west - 5, -3, -2, west - 1, 0, 2, water)
            .fill(east + 1, -3, -2, east + 5, 0, 2, water)
            .fill(west, -3, 0, east, -3, 0, water)
    }

    @Test
    fun `flooded tunnels one block high are swum through`() {
        val path = completePath(search(pools(8), feet(-12, 1, 0), BlockPos(11, 1, 0)))

        val tunnel = path.steps.filter { it.to.y == -3 && it.to.x in -4..3 }
        assertEquals(8, tunnel.size, "the path should lead through the tunnel")
        assertTrue(tunnel.all { it.isUnderWater && it.move == Move.SWIM })
        assertTrue(path.steps.none { it.isUnderWater && it.to.y > 0 })
    }

    @Test
    fun `flooded tunnels longer than the air lasts are swum through only past air pockets`() {
        assertFalse(isComplete(search(pools(30), feet(-23, 1, 0), BlockPos(22, 1, 0))))

        val world = pools(30).set(-6, -2, 0, AIR).set(6, -2, 0, AIR)
        val path = completePath(search(world, feet(-23, 1, 0), BlockPos(22, 1, 0)))

        val pockets = path.steps.filter { it.to == BlockPos(-6, -3, 0) || it.to == BlockPos(6, -3, 0) }
        assertEquals(2, pockets.size, "the path should lead through both air pockets")
        assertTrue(pockets.none { it.isUnderWater })
    }

    @Test
    fun `straight walks along deep drops are refused`() {
        val high = TestWorld.flat().fill(0, 10, 0, 10, 10, 0)
        assertFalse(isStraightWalkable(high.terrain(), feet(0, 11, 0), feet(10, 11, 0), 0.5, safeFall = 3))

        val low = TestWorld.flat().fill(0, 1, 0, 10, 1, 0)
        assertTrue(isStraightWalkable(low.terrain(), feet(0, 2, 0), feet(10, 2, 0), 0.5, safeFall = 3))
    }

    @Test
    fun `a point resting on farmland is reached`() {
        val world = TestWorld.flat().fill(5, 0, -1, 7, 0, 1, Blocks.FARMLAND.defaultBlockState())
        val item = Vec3(6.7, 0.9375, 0.8)
        val search = RouteSearch(world.terrain(), feet(0, 1, 0), NearDestination(item, 0.75), abilities(), LIMITS)

        val path = completePath(search.advanceToEnd())
        assertTrue(path.end.distanceTo(item) <= 0.75, "the path ends at ${path.end}, away from $item")
    }

    @Test
    fun `soul sand is walked around while ice is crossed like plain ground`() {
        val plain = completePath(search(TestWorld.flat(), feet(0, 1, 0), BlockPos(10, 1, 0)))

        val soulSand = Blocks.SOUL_SAND.defaultBlockState().toAgent()
        val sandWorld = TestWorld.flat().fill(3, 0, -2, 7, 0, 2, soulSand)
        val sand = completePath(search(sandWorld, feet(0, 1, 0), BlockPos(10, 1, 0)))
        assertTrue(sand.steps.none { sandWorld.getBlockState(it.to) == soulSand })

        val ice = Blocks.ICE.defaultBlockState().toAgent()
        val iceWorld = TestWorld.flat().fill(3, 0, -2, 7, 0, 2, ice)
        val path = completePath(search(iceWorld, feet(0, 1, 0), BlockPos(10, 1, 0)))
        assertTrue(path.steps.any { iceWorld.getBlockState(it.to.below()) == ice })
        assertEquals(plain.cost, path.cost, 1.0E-9)
    }

    @Test
    fun `honey is no floor to jump up from`() {
        fun world(floor: BlockState) = TestWorld.flat()
            .fill(-40, 0, -40, 2, 0, 40, floor)
            .fill(3, 1, -40, 40, 1, 40, Blocks.FARMLAND.defaultBlockState())
        val start = Vec3(0.5, 0.875, 0.5)
        val goal = BlockPos(10, 1, 0)

        val path = completePath(search(world(Blocks.SOUL_SAND.defaultBlockState()), start, goal))
        assertTrue(Move.ASCEND in path.moves())

        assertFalse(isComplete(search(world(Blocks.HONEY_BLOCK.defaultBlockState()), start, goal)))
    }

    private fun TestWorld.door(x: Int, z: Int, block: Block) = apply {
        val lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST.toMinecraft())
        set(x, 1, z, lower)
        set(x, 2, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
    }

    @Test
    fun `closed wooden doors and fence gates are opened instead of a long detour`() {
        val gate = Blocks.OAK_FENCE_GATE.defaultBlockState()
            .setValue(FenceGateBlock.FACING, Direction.EAST.toMinecraft())
        val world = TestWorld.flat()
            .fill(5, 1, -20, 5, 3, 20)
            .door(5, 0, Blocks.OAK_DOOR)
            .fill(8, 1, -20, 8, 3, 20)
            .set(8, 1, 0, gate)
            .set(8, 2, 0, AIR)
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(12, 1, 0)))

        assertEquals(listOf(BlockPos(5, 1, 0), BlockPos(8, 1, 0)), path.steps.flatMap { it.opens })
    }

    @Test
    fun `iron doors are walked around`() {
        val world = TestWorld.flat().fill(5, 1, -20, 5, 3, 20).door(5, 0, Blocks.IRON_DOOR)
        val path = completePath(search(world, feet(0, 1, 0), BlockPos(10, 1, 0)))

        assertTrue(path.steps.none { it.to.x == 5 && abs(it.to.z) <= 20 })
        assertTrue(path.steps.all { it.opens.isEmpty() })
    }

    @Test
    fun `trapdoors are opened in walls and walked over on floors`() {
        val trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
        val floor = TestWorld.flat().set(5, 1, 0, trapdoor)
        val over = completePath(search(floor, feet(0, 1, 0), BlockPos(10, 1, 0)))
        assertTrue(over.steps.any { it.to == BlockPos(5, 1, 0) })
        assertTrue(over.steps.all { it.opens.isEmpty() })

        val wall = TestWorld.flat()
            .fill(5, 1, -20, 5, 3, 20)
            .set(5, 1, 0, AIR)
            .set(5, 2, 0, trapdoor)
        val through = completePath(search(wall, feet(0, 1, 0), BlockPos(10, 1, 0)))
        assertEquals(listOf(BlockPos(5, 2, 0)), through.steps.flatMap { it.opens })
    }

}
