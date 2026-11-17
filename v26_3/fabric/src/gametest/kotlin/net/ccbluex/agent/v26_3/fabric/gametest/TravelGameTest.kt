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

import net.ccbluex.agent.navigation.Options
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * A player with a boat in its inventory walks to the far side of a lake it cannot walk around: it puts the boat on
 * the water, rows across, and breaks the boat at the far shore and picks it up again.
 *
 * @agent claude-opus-5-5
 */
internal val boatCrossing: GameTestScope.() -> Unit = {
    // On a platform above the ground, so the lake's sides drop off and the only way is across
    val y = origin.y + 10
    fill(BlockPos(-4, y - 4, -10), BlockPos(40, y + 4, 10), "minecraft:air")
    fill(BlockPos(-4, y - 4, -10), BlockPos(40, y - 1, 10), "minecraft:stone")
    fill(BlockPos(6, y - 3, -10), BlockPos(26, y - 1, 10), "minecraft:water")
    awaitServer("block 26 ${y - 1} 10 minecraft:water")
    run("clear $playerName")
    run("item replace entity $playerName inventory.0 with minecraft:oak_boat")
    moveTo(Vec3(1.5, y.toDouble(), 0.5), yRot = -90f)
    lateinit var walker: TestWalker
    context.onClient { walker = TestWalker(1.0, options = Options(canBoat = true)) }
    walker.start(context)
    try {
        var rode = false
        walker.walking(context, Vec3(33.5, y.toDouble(), 0.5)) {
            await(context, 1600, "Did not get across the lake") {
                context.fromClient { mc ->
                    val player = checkNotNull(mc.player)
                    rode = rode || player.isPassenger
                    player.position().arrivedAt(Vec3(33.5, y.toDouble(), 0.5), 1.0)
                }
            }
        }
        check(rode) { "Swam across the lake, never got into a boat" }
        val (boats, left) = context.fromClient { mc ->
            val player = checkNotNull(mc.player)
            val boats = (0 until 36).count { player.inventory.getItem(it).`is`(Items.OAK_BOAT) }
            val area = AABB.ofSize(player.position(), 80.0, 20.0, 40.0)
            boats to checkNotNull(mc.level).getEntitiesOfClass(AbstractBoat::class.java, area).size
        }
        check(left == 0) { "Left $left boats behind" }
        check(boats == 1) { "Carries $boats boats after the crossing, not the one it brought" }
    } finally {
        walker.stop(context)
        fill(BlockPos(-4, y - 4, -10), BlockPos(40, y + 4, 10), "minecraft:air")
    }
}

/**
 * @agent claude-opus-5-5
 */
public class BoatCrossingGameTest : ServerGameTest(boatCrossing)

/**
 * A hungry player walks a long corridor with a gap it bridges, and eats on the way: the steak it keeps outside its
 * hotbar first, as it fills more than the bread, and never while it places the bridge.
 *
 * @agent claude-opus-5-5
 */
internal val eatingOnTheWay: GameTestScope.() -> Unit = {
    val y = origin.y + 10
    fill(BlockPos(-2, y - 4, -3), BlockPos(44, y + 3, 3), "minecraft:air")
    fill(BlockPos(-2, y - 1, -2), BlockPos(44, y - 1, 2), "minecraft:stone")
    // Too wide to jump: the walk bridges it
    fill(BlockPos(24, y - 1, -2), BlockPos(28, y - 1, 2), "minecraft:air")
    awaitServer("block 26 ${y - 1} 0 minecraft:air")
    run("clear $playerName")
    run("item replace entity $playerName hotbar.0 with minecraft:cobblestone 32")
    run("item replace entity $playerName hotbar.1 with minecraft:bread 4")
    run("item replace entity $playerName inventory.0 with minecraft:cooked_beef 2")
    moveTo(Vec3(0.5, y.toDouble(), 0.5), yRot = -90f)
    // Hunger at its strongest empties the bar within a few seconds
    run("effect give $playerName minecraft:hunger 3 255")
    awaitClient("the player to get hungry") { checkNotNull(it.player).foodData.foodLevel <= 6 }
    run("effect clear $playerName minecraft:hunger")
    val hunger = context.fromClient { checkNotNull(it.player).foodData.foodLevel }
    lateinit var walker: TestWalker
    context.onClient { walker = TestWalker(1.0, options = Options(canPlace = true, canEat = true)) }
    walker.start(context)
    try {
        var ateWhileBuilding = 0
        walker.walking(context, Vec3(42.5, y.toDouble(), 0.5)) {
            await(context, 2400, "Did not walk the corridor") {
                context.fromClient { mc ->
                    val player = checkNotNull(mc.player)
                    if (walker.isBuilding && player.isUsingItem) ateWhileBuilding++
                    player.position().arrivedAt(Vec3(42.5, y.toDouble(), 0.5), 1.0)
                }
            }
        }
        val (fed, beef) = context.fromClient { mc ->
            val player = checkNotNull(mc.player)
            player.foodData.foodLevel to player.inventory.countItem(Items.COOKED_BEEF)
        }
        check(ateWhileBuilding == 0) { "Ate for $ateWhileBuilding ticks while placing the bridge" }
        check(beef < 2) { "Ate no steak, the best food it carried" }
        check(fed > hunger) { "Hunger went from $hunger to $fed on the way" }
    } finally {
        walker.stop(context)
        fill(BlockPos(-2, y - 4, -3), BlockPos(44, y + 3, 3), "minecraft:air")
    }
}

/**
 * @agent claude-opus-5-5
 */
public class EatingOnTheWayGameTest : ServerGameTest(eatingOnTheWay)
