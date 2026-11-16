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
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.route.Search
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.v26_3.GameBlocks
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.LevelBlocks
import net.ccbluex.agent.world.Mechanics
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.block.Blocks
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.minecraft.core.BlockPos as McBlockPos
import net.minecraft.world.level.block.Blocks as McBlocks
import net.minecraft.world.level.block.state.BlockState as McBlockState

/**
 * An in-memory world for routing tests; every block is air unless filled.
 *
 * From LiquidBounce: [TestWorld.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/TestWorld.kt#L41-L82)
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class TestWorld(
    private val known: (x: Int, z: Int) -> Boolean = { _, _ -> true },
) : LevelBlocks, GameBlocks {

    override var mechanics: Mechanics = Mechanics.MODERN

    private val states = HashMap<BlockPos, BlockState>()

    override val blocks: BlockGetter = object : BlockGetter {
        override fun getBlockEntity(pos: McBlockPos): BlockEntity? = null

        override fun getBlockState(pos: McBlockPos): McBlockState =
            this@TestWorld.getBlockState(pos.toAgent()).toMinecraft()

        override fun getFluidState(pos: McBlockPos): FluidState = getBlockState(pos).fluidState

        override fun getHeight(): Int = height

        override fun getMinY(): Int = minY
    }

    public fun fill(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, state: BlockState = STONE): TestWorld = apply {
        for (x in minOf(x1, x2)..maxOf(x1, x2)) {
            for (y in minOf(y1, y2)..maxOf(y1, y2)) {
                for (z in minOf(z1, z2)..maxOf(z1, z2)) {
                    states[BlockPos(x, y, z)] = state
                }
            }
        }
    }

    public fun fill(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, state: McBlockState): TestWorld =
        fill(x1, y1, z1, x2, y2, z2, state.toAgent())

    public fun set(x: Int, y: Int, z: Int, state: McBlockState): TestWorld = fill(x, y, z, x, y, z, state.toAgent())

    public fun set(x: Int, y: Int, z: Int, state: BlockState): TestWorld = fill(x, y, z, x, y, z, state)

    public fun terrain(canOpenDoors: Boolean = false): Terrain = Terrain(this, canOpenDoors = canOpenDoors)

    override fun isKnown(x: Int, y: Int, z: Int): Boolean = y >= minY && y <= maxY && known(x, z)

    override fun isInsideBorder(x: Double, z: Double): Boolean = true

    override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: AIR

    override val height: Int get() = 384

    override val minY: Int get() = -64

    public companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        public val STONE: BlockState get() = McBlocks.STONE.defaultBlockState().toAgent()
        public val AIR: BlockState get() = Blocks.AIR.defaultBlockState()

        /**
         * A stone floor at y = 0 from -40 to 40, so feet stand at y = 1.
         */
        public fun flat(known: (x: Int, z: Int) -> Boolean = { _, _ -> true }): TestWorld =
            TestWorld(known).fill(-40, 0, -40, 40, 0, 40)

        public fun feet(x: Int, y: Int, z: Int): Vec3 = Vec3.atBottomCenterOf(BlockPos(x, y, z))
    }

}

/**
 * Advances the search until it is done, after the first slice at most [maxSlices] more times.
 *
 * From LiquidBounce: [TestWorld.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/TestWorld.kt#L88-L92)
 *
 * @agent claude-opus-5-5
 */
public fun <T> Search<T>.advanceToEnd(maxSlices: Int = Int.MAX_VALUE): Outcome<T> {
    var result = advance()
    var slices = 0
    while (result == Outcome.Searching && slices++ < maxSlices) {
        result = advance()
    }
    return result
}

/**
 * @agent claude-opus-5-5
 */
public inline fun <reified T> found(result: Outcome<T>): T = assertIs<Outcome.Found<T>>(result).value

/**
 * From LiquidBounce: [TestWorld.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/TestWorld.kt#L99-L100)
 *
 * @agent claude-opus-5-5
 */
public fun completePath(result: Outcome<Route>, message: String? = "path should reach the goal"): Route =
    found(result).also { assertTrue(it.canReach, message) }

/**
 * A sprinting walker that takes falls of [safeFall] blocks and mines every block in [breakTicks].
 *
 * From LiquidBounce: [TestWorld.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/TestWorld.kt#L103-L124), [WalkRoutingTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/WalkRoutingTest.kt#L66-L71)
 *
 * @agent claude-opus-5-5
 */
public fun walkAbilities(
    canBreak: Boolean = false,
    canPlace: Boolean = false,
    canParkour: Boolean = false,
    riskyJumps: Boolean = false,
    safeFall: Int = 3,
    breakTicks: Double = Double.POSITIVE_INFINITY,
    avoid: LongSet = LongOpenHashSet(),
    jumpClimbs: Boolean = true,
    unbreakable: LongSet = LongOpenHashSet(),
): WalkAbilities = WalkAbilities(
    canBreak = canBreak,
    canPlace = canPlace,
    placeableBlocks = if (canPlace) 64 else 0,
    canParkour = canParkour,
    riskyJumps = riskyJumps,
    canSprint = true,
    safeFall = safeFall,
    waterFall = 20,
    breakTicks = { breakTicks },
    avoid = avoid,
    jumpClimbs = jumpClimbs,
    unbreakable = unbreakable,
)
