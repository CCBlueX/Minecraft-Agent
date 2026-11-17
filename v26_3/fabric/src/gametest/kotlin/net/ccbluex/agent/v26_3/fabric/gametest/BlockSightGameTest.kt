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

@file:OptIn(InternalAgentApi::class)

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.navigation.seenPoint
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Ticks the server gets to answer a click.
 */
private const val ANSWER_TICKS = 10

/**
 * Clicks on blocks behind a wall: placing against a face the wall hides and opening a chest behind it are refused and
 * never sent, unless the options allow clicking through blocks (only without an anticheat, which would flag them).
 * With the wall gone the same clicks go through, so the wall is what refused them. Past stairs, a dig through the
 * step is refused and one over their low half breaks the block. A look grazing the top edge of a south face clicks
 * the top face. From a pond, a grass block between farmland, which lies lower and bares a sliver of the grass block's
 * sides, is aimed at where looks half a turn step off still meet its top, as a body turning in whole steps lands
 * there, and tilled.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal val blockSight: GameTestScope.() -> Unit = {
    val y = origin.y
    fill(BlockPos(-2, y, -4), BlockPos(8, y + 4, 4), "minecraft:air")
    fill(BlockPos(-2, y - 1, -4), BlockPos(8, y - 1, 4), "minecraft:stone")
    val against = BlockPos(4, y, -1)
    val placed = against.west()
    val chest = BlockPos(4, y, 1)
    run("setblock ${against.x} $y ${against.z} minecraft:stone")
    run("setblock ${chest.x} $y ${chest.z} minecraft:chest[facing=west]")
    fill(BlockPos(2, y, -3), BlockPos(2, y + 2, 3), "minecraft:stone")
    awaitServer("block 2 ${y + 2} 3 minecraft:stone")
    run("item replace entity $playerName hotbar.0 with minecraft:stone 16")
    moveTo(Vec3(0.5, y.toDouble(), 0.5), yRot = -90f)
    awaitClient("the stone to reach the hotbar") { checkNotNull(it.player).inventory.getItem(0).`is`(Items.STONE) }

    lateinit var navigation: ClientNavigation
    context.onClient {
        navigation = ClientNavigation()
        checkNotNull(it.player).inventory.selectedSlot = 0
    }
    val face = Vec3(against.x.toDouble(), y + 0.5, against.z + 0.5)
    val place = BlockHitResult(face, Direction.WEST, against, false)
    val open = BlockHitResult(Vec3.atCenterOf(chest).add(-0.5, 0.0, 0.0), Direction.WEST, chest, false)

    fun lookAt(point: Vec3) {
        context.onClient {
            val player = checkNotNull(it.player)
            val rotation = ViewRotation.fromVector(point.subtract(player.eyePosition).toAgent())
            player.yRot = rotation.yRot
            player.xRot = rotation.xRot
        }
        // The movement packet carries the rotation the click is checked against
        ticks(3)
    }

    fun click(hit: BlockHitResult): Boolean {
        lookAt(hit.location)
        return context.fromClient { navigation.navigator.useItemOn(InteractionHand.MAIN_HAND.toAgent(), hit.toAgent()) }
    }

    fun opened() = context.fromClient { checkNotNull(it.player).containerMenu is ChestMenu }

    fun closeChest() = context.onClient { checkNotNull(it.player).closeContainer() }

    check(!click(place)) { "Placed against a face behind the wall" }
    check(!click(open)) { "Opened a chest behind the wall" }
    ticks(ANSWER_TICKS)
    awaitServer("block ${placed.x} $y ${placed.z} minecraft:air")
    check(!opened()) { "The chest behind the wall opened" }

    if (anticheats.isEmpty() || paperOnly) {
        context.onClient { navigation.options = Options(canClickThroughBlocks = true) }
        check(click(place)) { "Placing through the wall was refused with clicks through blocks allowed" }
        awaitServer("block ${placed.x} $y ${placed.z} minecraft:stone")
        check(click(open)) { "Opening through the wall was refused with clicks through blocks allowed" }
        awaitClient("the chest behind the wall to open") { checkNotNull(it.player).containerMenu is ChestMenu }
        closeChest()
        context.onClient { navigation.options = Options() }
        run("setblock ${placed.x} $y ${placed.z} minecraft:air")
    }

    fill(BlockPos(2, y, -3), BlockPos(2, y + 2, 3), "minecraft:air")
    awaitServer("block 2 ${y + 2} 3 minecraft:air")
    awaitServer("block ${placed.x} $y ${placed.z} minecraft:air")
    awaitClient("the wall to go") { it.level?.getBlockState(BlockPos(2, y + 1, 0))?.isAir == true }
    check(click(place)) { "Placing against a face in sight was refused" }
    awaitServer("block ${placed.x} $y ${placed.z} minecraft:stone")
    check(click(open)) { "Opening a chest in sight was refused" }
    awaitClient("the chest in sight to open") { checkNotNull(it.player).containerMenu is ChestMenu }
    closeChest()

    // A dig past stairs: through the step it never starts, over the low half it breaks the block as the game would
    val dirt = BlockPos(3, y, 0)
    run("setblock ${dirt.x} $y ${dirt.z} minecraft:dirt")
    run("setblock 2 $y 0 minecraft:stone_stairs[facing=north]")
    awaitServer("block 2 $y 0 minecraft:stone_stairs")
    awaitClient("the stairs to show") { it.level?.getBlockState(BlockPos(2, y, 0))?.isAir == false }

    fun dug() = context.fromClient { it.level?.getBlockState(dirt)?.isAir == true }

    fun dig(at: Vec3, limit: Int): Boolean {
        lookAt(at)
        var started = false
        repeat(limit) {
            if (dug()) return true
            context.onClient {
                val navigator = navigation.navigator
                if (started) {
                    navigator.continueDestroy(dirt.toAgent(), Direction.WEST.toAgent())
                } else {
                    started = navigator.startDestroy(dirt.toAgent(), Direction.WEST.toAgent())
                }
            }
            ticks(1)
        }
        context.onClient { navigation.body.stopDestroy() }
        return dug()
    }

    check(!dig(Vec3(3.0, y + 0.7, 0.2), 40)) { "Dug a block through the step of the stairs" }
    awaitServer("block ${dirt.x} $y ${dirt.z} minecraft:dirt")
    check(dig(Vec3(3.0, y + 0.7, 0.8), 80)) { "The block over the low half of the stairs was not dug" }
    awaitServer("block ${dirt.x} $y ${dirt.z} minecraft:air")

    // A Ninja click from a bridge test, at the edge of the block below and with the yaw unwrapped: a look that passes
    // 0.0007 above or below the top edge of the south face, depending on how the view vector is worked out
    val edge = BlockPos(6, y, -3)
    run("setblock ${edge.x} $y ${edge.z} minecraft:stone")
    awaitServer("block ${edge.x} $y ${edge.z} minecraft:stone")
    // No gravity, so the player stays exactly where the bridge click was made
    run("attribute $playerName minecraft:gravity base set 0")
    val feet = Vec3(edge.x + 0.376737410055266, y + 1.0, edge.z + 1.203064042982586)
    run("tp $playerName ${feet.x} ${feet.y} ${feet.z}")
    awaitClient("the player at the edge") { checkNotNull(it.player).position().distanceToSqr(feet) < 1.0E-12 }
    context.onClient {
        val player = checkNotNull(it.player)
        player.yRot = -225f
        player.xRot = 79.950005f
    }
    ticks(3)
    val (south, top) = context.fromClient {
        navigation.navigator.canClick(edge.toAgent(), Direction.SOUTH.toAgent()) to
            navigation.navigator.canClick(edge.toAgent(), Direction.UP.toAgent())
    }
    check(!south && top) { "A look grazing the top edge clicks the south face" }
    run("attribute $playerName minecraft:gravity base reset")

    val pond = BlockPos(6, y - 1, 3)
    val grass = pond.offset(1, 0, 1)
    fill(pond.offset(-1, -1, -1), pond.offset(1, -1, 1), "minecraft:stone")
    fill(pond.offset(-1, 0, -1), pond.offset(1, 0, 1), "minecraft:grass_block")
    for ((x, z) in listOf(0 to -1, 1 to 0, -1 to 0, 0 to 1, 1 to -1, -1 to -1)) {
        run("setblock ${pond.x + x} ${pond.y} ${pond.z + z} minecraft:farmland")
    }
    run("setblock ${pond.x} ${pond.y} ${pond.z} minecraft:water")
    run("item replace entity $playerName hotbar.1 with minecraft:wooden_hoe")
    run("tp $playerName ${pond.x + 0.5156} ${pond.y} ${pond.z + 0.3807}")
    awaitClient("the player to stand in the pond") { checkNotNull(it.player).isInWater }
    context.onClient { checkNotNull(it.player).inventory.selectedSlot = 1 }
    val (eye, point) = context.fromClient { mc ->
        val player = checkNotNull(mc.player)
        val body = navigation.body
        val reach = player.blockInteractionRange()
        player.eyePosition to checkNotNull(mc.level).toAgent().seenPoint(
            player.eyePosition.toAgent(), grass.toAgent(), reach, Direction.UP.toAgent(), body.sentRotation,
            player.toAgent(), body.viewStep,
        )
    }
    checkNotNull(point) { "The grass between the farmland shows no point of its top from the pond" }
    val step = context.fromClient { navigation.body.viewStep }
    val aim = ViewRotation.fromVector(point.subtract(eye.toAgent()))
    for ((yRot, xRot) in listOf(step / 2 to 0f, -step / 2 to 0f, 0f to step / 2, 0f to -step / 2)) {
        context.onClient {
            val player = checkNotNull(it.player)
            player.yRot = aim.yRot + yRot
            player.xRot = aim.xRot + xRot
        }
        ticks(3)
        check(context.fromClient { navigation.navigator.canClick(grass.toAgent(), Direction.UP.toAgent()) }) {
            "A look ${yRot}/${xRot} degrees beside the point aimed at missed the top of the grass"
        }
    }
    check(click(BlockHitResult(point.toMinecraft(), Direction.UP, grass, false))) { "Tilling the grass from the pond was refused" }
    awaitServer("block ${grass.x} ${grass.y} ${grass.z} minecraft:farmland")
}

/**
 * @agent claude-opus-5-5
 */
public class BlockSightGameTest : ServerGameTest(blockSight)
