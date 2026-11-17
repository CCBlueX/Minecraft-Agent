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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.Bridging
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.navigation.walk.PlacedBlock
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import kotlin.math.abs
import net.ccbluex.agent.math.BlockPos as AgentBlockPos

private const val GOAL_RADIUS = 0.5

/**
 * A survival player bridges over the void with each method of crossing, unhurt: Ninja along a straight bridge and a
 * staircase, godbridging a long one, Breezily and Moonwalk shorter ones, a telly along a 20 block one, one-stacking up
 * three blocks and sneaking all the way in Swift Sneak III leggings; and godbridging a short bridge and a telly
 * when the option asks for them. Each crossing logs its blocks per second. Blocks the server refuses end Ninja
 * for placing at the edge, and a godbridge that falls for one gives the bridge up.
 *
 * @agent claude-opus-5-5
 */
internal val walkBridge: GameTestScope.() -> Unit = {
    val placements = Placements()
    lateinit var safe: TestWalker
    lateinit var risky: TestWalker
    lateinit var godbridging: TestWalker
    lateinit var tellying: TestWalker
    context.onClient {
        safe = TestWalker(GOAL_RADIUS, listener = placements, options = Options(canPlace = true))
        risky = TestWalker(GOAL_RADIUS, listener = placements, options = Options(canPlace = true, riskyBridges = true))
        godbridging = TestWalker(
            GOAL_RADIUS,
            listener = placements,
            options = Options(canPlace = true, bridging = Bridging.GODBRIDGE),
        )
        tellying = TestWalker(
            GOAL_RADIUS,
            listener = placements,
            options = Options(canPlace = true, bridging = Bridging.TELLY),
        )
    }

    val y = origin.y + 20
    try {
        buildCourses(y)
        Refusals.throughBlocks.set(0)
        run("effect give $playerName minecraft:saturation infinite 0 true")
        safe.start(context)
        risky.start(context)
        godbridging.start(context)
        tellying.start(context)

        val ninja = cross(safe, placements, Vec3(1.5, y + 1.0, 0.5), Vec3(14.5, y + 1.0, 0.5), "Ninja")
        // Turning up to the horizon between the clicks shows as a pitch below 30 degrees.
        check(ninja.lowestXRot >= MIN_X_ROT) { "The view rose to ${ninja.lowestXRot} degrees while bridging" }
        check(ninja.yRotOff < STRAIGHT_BACK) { "Ninja did not bridge backwards: ${ninja.yRotOff} degrees off" }

        cross(safe, placements, Vec3(1.5, y + 1.0, 10.5), Vec3(9.5, y + 1.0, 18.5), "Ninja diagonally")

        val godbridge =
            cross(godbridging, placements, Vec3(1.5, y + 1.0, -10.5), Vec3(20.5, y + 1.0, -10.5), "Godbridge")
        check(godbridge.yRotOff in DIAGONAL) { "Godbridge did not look diagonally: ${godbridge.yRotOff} degrees off" }
        check(godbridge.sneakShare < UNSNEAKED) { "Godbridge sneaked ${godbridge.sneakShare} of the time" }
        check(godbridge.airborne > 0) { "Godbridge never hopped to reset its timing" }

        val breezily = cross(risky, placements, Vec3(1.5, y + 1.0, -20.5), Vec3(12.5, y + 1.0, -20.5), "Breezily")
        check(breezily.sneakShare < UNSNEAKED) { "Breezily sneaked ${breezily.sneakShare} of the time" }

        val moonwalk = cross(risky, placements, Vec3(1.5, y + 1.0, -30.5), Vec3(11.5, y + 1.0, -24.5), "Moonwalk")
        check(moonwalk.sneakShare < UNSNEAKED) { "Moonwalk sneaked ${moonwalk.sneakShare} of the time" }

        val telly = cross(risky, placements, Vec3(1.5, y + 1.0, 60.5), Vec3(24.5, y + 1.0, 60.5), "Telly")
        check(telly.blocks == TELLY_GAP) { "Telly placed ${telly.blocks} blocks for a gap of $TELLY_GAP" }
        check(telly.airborne > 0) { "Telly never jumped" }
        check(telly.throughBlocks == 0) { "Telly clicked ${telly.throughBlocks} blocks through another block" }
        check(telly.blocksPerSecond > godbridge.blocksPerSecond) {
            "Telly bridged ${telly.blocksPerSecond} blocks/s, no faster than godbridging"
        }

        cross(safe, placements, Vec3(1.5, y + 1.0, 20.5), Vec3(9.5, y + 4.0, 20.5), "One-stack")

        val fixed =
            cross(godbridging, placements, Vec3(1.5, y + 1.0, -40.5), Vec3(12.5, y + 1.0, -40.5), "Godbridge set")
        check(fixed.yRotOff in DIAGONAL) { "Godbridge as the option did not look diagonally: ${fixed.yRotOff}" }

        val tellySet = cross(tellying, placements, Vec3(1.5, y + 1.0, 70.5), Vec3(24.5, y + 1.0, 70.5), "Telly set")
        check(tellySet.blocks == TELLY_GAP) { "Telly as the option placed ${tellySet.blocks} blocks for $TELLY_GAP" }
        check(tellySet.airborne > 0) { "Telly as the option never jumped" }

        run("item replace entity $playerName armor.legs with " +
            "minecraft:leather_leggings[enchantments={\"minecraft:swift_sneak\":3}]")
        val swiftSneak = cross(safe, placements, Vec3(1.5, y + 1.0, 40.5), Vec3(14.5, y + 1.0, 40.5), "Swift Sneak")
        check(swiftSneak.sneakShare > SNEAKED) { "Swift Sneak sneaked only ${swiftSneak.sneakShare} of the time" }
        check(swiftSneak.blocks == 10) { "Swift Sneak placed ${swiftSneak.blocks} blocks for a gap of 10" }
        run("item replace entity $playerName armor.legs with minecraft:air")

        // Blocks are refused by the integrated server's block use event, which a game test server does not have.
        if (anticheats.isEmpty()) {
            crossRefused(safe, placements, Vec3(1.5, y + 1.0, 30.5), Vec3(20.5, y + 1.0, 30.5))
            fallRefused(risky, placements, Vec3(1.5, y + 1.0, 50.5), Vec3(20.5, y + 1.0, 50.5))
        }
    } finally {
        Refusals.left.set(0)
        run("item replace entity $playerName armor.legs with minecraft:air")
        safe.stop(context)
        risky.stop(context)
        godbridging.stop(context)
        tellying.stop(context)
    }
}

/**
 * @agent claude-opus-5-5
 */
public class WalkBridgeGameTest : ServerGameTest(walkBridge)

private const val MIN_X_ROT = 30f
private const val STRAIGHT_BACK = 20f
private val DIAGONAL = 30f..60f
private const val UNSNEAKED = 0.4
private const val SNEAKED = 0.8
private const val EDGE_SNEAKED = 0.5
private const val TELLY_GAP = 20
private val clicks = LoggerFactory.getLogger("Agent/GameTest")

/**
 * The block the server refuses while [left] is above 0, counting each refusal down; the client still predicts it,
 * as a plugin protecting the spot or an anticheat would have it. Also counts the clicks the integrated server sees
 * hit a face its own ray from the player's eye does not hit first, in [throughBlocks].
 *
 * @agent claude-opus-5-5
 */
private object Refusals {
    @Volatile
    var pos: BlockPos? = null
    val left = AtomicInteger()
    val throughBlocks = AtomicInteger()

    init {
        UseBlockCallback.EVENT.register { player, level, _, hit ->
            if (!level.isClientSide) {
                val seen = player.pick(player.blockInteractionRange(), 1f, false) as? BlockHitResult
                if (seen == null || seen.blockPos != hit.blockPos || seen.direction != hit.direction) {
                    clicks.warn(
                        "The server saw a click on {} {} hit {} {} first",
                        hit.blockPos, hit.direction, seen?.blockPos, seen?.direction,
                    )
                    throughBlocks.incrementAndGet()
                }
            }
            val refuses = !level.isClientSide && hit.blockPos.relative(hit.direction) == pos &&
                left.getAndUpdate { maxOf(0, it - 1) } > 0
            if (refuses) InteractionResult.FAIL else InteractionResult.PASS
        }
    }

    fun refuse(pos: BlockPos, times: Int) {
        this.pos = pos
        left.set(times)
    }
}

/**
 * Pads twenty blocks over the ground, three blocks wide, with a gap between start and goal: 10 blocks along z 0, a
 * staircase from z 10 to z 19, 16 blocks along z -10, 8 along z -20, 8 along z -30 with a path turning south after
 * it, 8 along z -40, 5 along z 20 to a pad 3 blocks higher, 16 along z 30, 16 along z 50 with a floor 3 blocks below
 * its first two, 10 along z 40 and 20 along z 60.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCourses(y: Int) {
    fillArea(BlockPos(-2, y - 3, -42), BlockPos(26, y + 8, 72), "minecraft:air")
    pads(y, 0, 13)
    fill(BlockPos(0, y, 9), BlockPos(2, y, 11), "minecraft:stone")
    fill(BlockPos(8, y, 17), BlockPos(10, y, 19), "minecraft:stone")
    pads(y, -10, 19)
    pads(y, -20, 11)
    fill(BlockPos(0, y, -31), BlockPos(2, y, -29), "minecraft:stone")
    fill(BlockPos(11, y, -30), BlockPos(11, y, -24), "minecraft:stone")
    pads(y, -40, 11)
    fill(BlockPos(0, y, 19), BlockPos(2, y, 21), "minecraft:stone")
    fill(BlockPos(8, y + 3, 19), BlockPos(10, y + 3, 21), "minecraft:stone")
    pads(y, 30, 19)
    pads(y, 50, 19)
    fill(BlockPos(3, y - 3, 49), BlockPos(4, y - 3, 51), "minecraft:stone")
    pads(y, 40, 13)
    pads(y, 60, TELLY_GAP + 3)
    pads(y, 70, TELLY_GAP + 3)
    awaitServer("block 15 $y 41 minecraft:stone")
}

/**
 * A start pad at x 0..2 and a goal pad from [goalX] on, around [z].
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.pads(y: Int, z: Int, goalX: Int) {
    fill(BlockPos(0, y, z - 1), BlockPos(2, y, z + 1), "minecraft:stone")
    fill(BlockPos(goalX, y, z - 1), BlockPos(goalX + 2, y, z + 1), "minecraft:stone")
}

/**
 * How a crossing went: the blocks placed and how fast, the highest the view rose, the share of ticks sneaked and
 * how far the yaw kept off straight back along the way, from the first block placed to the last; and the blocks placed
 * through another.
 *
 * @agent claude-opus-5-5
 */
private class Crossed(
    val blocks: Int,
    val blocksPerSecond: Double,
    val lowestXRot: Float,
    val sneakShare: Double,
    val yRotOff: Float,
    val airborne: Int,
    val throughBlocks: Int,
)

/**
 * One tick of a crossing.
 *
 * @agent claude-opus-5-5
 */
private class Sample(val tick: Int, val sneaks: Boolean, val xRot: Float, val yRotOff: Float, val onGround: Boolean)

/**
 * Walks from [start] to [goal] across the gap, measured from the first block placed to the last.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.cross(
    walker: TestWalker,
    placements: Placements,
    start: Vec3,
    goal: Vec3,
    method: String,
): Crossed {
    give("minecraft:cobblestone", 64)
    moveTo(start)
    placements.clear()
    placements.sentRotation = walker::sentRotation
    val since = server.console.size
    val back = Mth.wrapDegrees(goal.subtract(start).toAgent().yRot + 180f)
    val samples = ArrayList<Sample>()
    unhurt("Bridging with $method") {
        walker.walk(
            context,
            goal,
            1200,
            "Bridging with $method",
            "Did not bridge with $method",
            acting = { walker.isActing },
        ) { player ->
            samples += Sample(
                player.tickCount,
                player.isShiftKeyDown,
                player.xRot,
                Mth.wrapDegrees(player.yRot - back),
                player.onGround(),
            )
        }
    }
    check(placements.isNotEmpty()) { "Crossed without placing a block with $method" }
    val first = placements.first()
    val last = placements.last()
    val across = samples.filter { it.tick in first..last }.ifEmpty { samples }
    val crossed = Crossed(
        blocks = placements.size,
        blocksPerSecond = if (last > first) (placements.size - 1) * 20.0 / (last - first) else 0.0,
        lowestXRot = across.minOf { it.xRot },
        sneakShare = across.count { it.sneaks }.toDouble() / across.size,
        yRotOff = across.map { abs(it.yRotOff) }.average().toFloat(),
        airborne = across.count { !it.onGround },
        throughBlocks = placements.throughBlocks.get() + Refusals.throughBlocks.getAndSet(0),
    )
    // The anticheat's flags with their check names, so a violation level names the method behind it
    val flags = server.console.drop(since).filter { it.anticheat != null }
    flags.forEach { logger.warn("FLAG {}: {}", method, it) }
    logger.info(
        "BRIDGE {}: {} blocks, {} blocks/s, view up to {} degrees, sneaked {}, {} degrees off straight back, " +
            "{} clicked through blocks, {} anticheat flags",
        method,
        crossed.blocks,
        "%.2f".format(crossed.blocksPerSecond),
        crossed.lowestXRot,
        "%.2f".format(crossed.sneakShare),
        crossed.yRotOff,
        crossed.throughBlocks,
        flags.size,
    )
    check(crossed.throughBlocks == 0) { "$method clicked ${crossed.throughBlocks} blocks through another block" }
    return crossed
}

/**
 * The ticks blocks were placed at, by the player's tick count.
 *
 * @agent claude-opus-5-5
 */
private class Placements(private val ticks: MutableList<Int> = CopyOnWriteArrayList()) :
    Listener,
    MutableList<Int> by ticks {

    /**
     * The ticks the server refused blocks at.
     */
    val rejected: MutableList<Int> = CopyOnWriteArrayList()

    /**
     * Blocks placed with a click that, from the eye with the rotation the server has, hit another block before the
     * new one, or the face it went on.
     */
    val throughBlocks = AtomicInteger()

    @Volatile
    var sentRotation: (() -> ViewRotation)? = null

    override fun blockPlaced(block: PlacedBlock) {
        val player = Minecraft.getInstance().player
        add(player?.tickCount ?: 0)
        val rotation = sentRotation?.invoke() ?: return
        val eye = player?.eyePosition ?: return
        val end = eye.add(rotation.viewVector.toMinecraft().scale(player.blockInteractionRange()))
        // The client shows the new block already, so the click stops on it.
        val hit = player.level().clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val clean = hit.type == HitResult.Type.BLOCK &&
            (hit.blockPos == block.pos.toMinecraft() || hit.blockPos.relative(hit.direction) == block.pos.toMinecraft())
        if (!clean) {
            clicks.warn("Clicked {} through {} from {} with {}", block.pos, hit.blockPos, eye, rotation)
            throughBlocks.incrementAndGet()
        }
    }

    /**
     * Steps the walk gave up on.
     */
    val stuck: MutableList<AgentBlockPos> = CopyOnWriteArrayList()

    override fun stuck(pos: AgentBlockPos) {
        stuck += pos
    }

    override fun blockRejected(block: PlacedBlock) {
        rejected += Minecraft.getInstance().player?.tickCount ?: 0
    }

    override fun clear() {
        ticks.clear()
        rejected.clear()
        stuck.clear()
        throughBlocks.set(0)
    }
}

/**
 * Walks from [start] to [goal] across a long gap whose first block the server refuses twice: Ninja falls back to
 * placing each floor standing at the edge, which sneaks, and the bridge is still crossed.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.crossRefused(walker: TestWalker, placements: Placements, start: Vec3, goal: Vec3) {
    give("minecraft:cobblestone", 64)
    moveTo(start)
    placements.clear()
    Refusals.refuse(BlockPos.containing(start).offset(2, -1, 0), 2)
    val samples = ArrayList<Sample>()
    unhurt("Bridging past refused blocks") {
        walker.walking(context, goal) {
            await(context, 1200, "Did not bridge past refused blocks to $goal") {
                context.fromClient { mc ->
                    val player = checkNotNull(mc.player)
                    samples += Sample(player.tickCount, player.isShiftKeyDown, player.xRot, 0f, player.onGround())
                    player.position().arrivedAt(goal, GOAL_RADIUS)
                }
            }
        }
    }
    check(placements.rejected.size == 2) { "The server refused ${placements.rejected.size} blocks, not 2" }
    val edge = samples.filter { it.tick in placements.rejected[1]..placements.last() }
    val sneakShare = edge.count { it.sneaks }.toDouble() / maxOf(1, edge.size)
    logger.info("BRIDGE refused: {} refusals, then sneaked {} at the edge", placements.rejected.size, sneakShare)
    check(sneakShare > EDGE_SNEAKED) { "After two refusals the floors were placed sneaking only $sneakShare" }
}

/**
 * Godbridges from [start] to [goal] while the server refuses the first block once. A godbridge already stands on
 * that block when the refusal comes, so the player falls onto the floor below, where the walk gives the bridge up
 * at once and finds its way up and across from there.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.fallRefused(walker: TestWalker, placements: Placements, start: Vec3, goal: Vec3) {
    give("minecraft:cobblestone", 64)
    moveTo(start)
    placements.clear()
    Refusals.refuse(BlockPos.containing(start).offset(2, -1, 0), 1)
    var lowest = start.y
    unhurt("Falling off a refused godbridge") {
        walker.walking(context, goal) {
            await(context, 1200, "Did not get across after falling to $goal") {
                context.fromClient { mc ->
                    val player = checkNotNull(mc.player)
                    lowest = minOf(lowest, player.y)
                    player.position().arrivedAt(goal, GOAL_RADIUS)
                }
            }
        }
    }
    logger.info("BRIDGE fell: {} refusals, down to {}, stuck at {}", placements.rejected.size, lowest, placements.stuck)
    check(placements.rejected.size == 1) { "The server refused ${placements.rejected.size} blocks, not 1" }
    check(lowest >= start.y - 1 || placements.stuck.isNotEmpty()) { "Fell to $lowest without giving the bridge up" }
}
