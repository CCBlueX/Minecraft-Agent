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

import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.pathfinder.glide.Passage
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.setBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.Vec3

private const val CLEARANCE = 1.0
private const val FLIGHT_TICKS = 1600
private const val ARRIVAL = 1.5

/**
 * Where the landing counts as one, not as a contact.
 */
private const val LANDING_REACH = 4.0

/**
 * Where the way down onto the landing comes closer to the floor than the clearance.
 */
private const val LANDING_ROOM = 12.0

/**
 * The inside of one room of the cavern, every block included.
 */
private class Room(val x1: Int, val x2: Int, val y1: Int, val y2: Int, val z1: Int, val z2: Int)

/**
 * A cavern along +z, about 600 blocks long, down from y 175 to a landing at y 70: rooms bending from side to side,
 * a low stretch and pillars from floor to ceiling.
 */
private val ROOMS = listOf(
    Room(-15, 15, 130, 175, 20, 80),
    Room(-5, 25, 120, 150, 70, 160),
    Room(5, 35, 110, 140, 150, 240),
    Room(-10, 20, 100, 130, 230, 320),
    Room(-25, 5, 92, 112, 310, 400),
    Room(-20, 10, 84, 110, 390, 480),
    Room(-10, 20, 76, 100, 470, 560),
    Room(-10, 20, 70, 95, 550, 600),
)
private val PILLARS = listOf(
    Room(18, 21, 110, 140, 190, 193),
    Room(-12, -9, 92, 112, 350, 353),
    Room(4, 7, 76, 100, 510, 513),
)
private val START = BlockPos(0, 170, 30)
private val GOAL = Vec3(5.5, 70.0, 590.5)

/**
 * A fall into a cavern is taken over by a glide that flies all of it without touching a block, keeping
 * [Options.glideClearance], and lands at the far end.
 *
 * @agent claude-opus-5-5
 */
internal val tunnelGlide: GameTestScope.() -> Unit = {
    buildCavern()
    travel(START, yaw = 0f)
    run("item replace entity $playerName armor.chest with minecraft:elytra")
    awaitClient("the elytra") { !checkNotNull(it.player).getItemBySlot(EquipmentSlot.CHEST).isEmpty }

    val touches = ArrayList<BlockPos>()
    val listener = object : Listener {
        override fun touched(pos: BlockPos, direction: Direction) {
            if (Vec3.atCenterOf(pos).distanceTo(GOAL) > LANDING_REACH + 1.0) {
                touches += pos
            }
        }
    }
    var navigation: ClientNavigation? = null
    try {
        setBlock(START.below(), "minecraft:air")
        awaitClient("the fall") { !checkNotNull(it.player).onGround() }
        context.onClient {
            navigation = ClientNavigation(options = Options(glideClearance = CLEARANCE), listener = listener).also {
                it.navigator.moveTo(GOAL, ARRIVAL)
            }
        }
        flyCavern { navigation?.navigator?.isInProgress == false }
        check(touches.isEmpty()) { "The navigator reported touching ${touches.size} blocks: ${touches.take(5)}" }
    } finally {
        context.onClient { navigation?.navigator?.stop() }
    }
}

/**
 * @agent claude-opus-5-5
 */
public class TunnelGlideGameTest : ServerGameTest(TestWorld.VOID, tunnelGlide)

/**
 * Stone two blocks thick around every room, then the rooms hollowed out and the pillars put in, and a glass block
 * to start on.
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.buildCavern() {
    run("forceload add -40 0 50 300")
    run("forceload add -40 301 50 620")
    awaitServer("loaded -40 64 620")
    for (room in ROOMS) {
        fillArea(BlockPos(room.x1 - 2, room.y1 - 2, room.z1 - 2), BlockPos(room.x2 + 2, room.y2 + 2, room.z2 + 2), "minecraft:stone")
    }
    for (room in ROOMS) {
        fillArea(BlockPos(room.x1, room.y1, room.z1), BlockPos(room.x2, room.y2, room.z2), "minecraft:air")
    }
    for (pillar in PILLARS) {
        fillArea(BlockPos(pillar.x1, pillar.y1, pillar.z1), BlockPos(pillar.x2, pillar.y2, pillar.z2), "minecraft:stone")
    }
    setBlock(START.below(), "minecraft:glass")
    val last = PILLARS.last()
    awaitServer("block ${last.x2} ${last.y2} ${last.z2} minecraft:stone", 1200)
    awaitServer("block ${START.x} ${START.y - 1} ${START.z} minecraft:glass")
}

/**
 * Watches the glide until the navigation ends: every tick fall-flying that touched a block counts, except the
 * touch-down at the goal, and the room kept to the blocks is measured.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
private fun GameTestScope.flyCavern(hasEnded: () -> Boolean) {
    var contacts = 0
    var glidingTicks = 0
    var length = 0.0
    var leastRoom = Double.MAX_VALUE
    var leastRoomAt = Vec3.ZERO
    var last = context.playerPosition()
    for (tick in 0 until FLIGHT_TICKS) {
        val state = context.fromClient { mc ->
            val player = checkNotNull(mc.player)
            val touching = player.horizontalCollision || player.verticalCollision || player.onGround()
            val position = player.position()
            val measured = player.isFallFlying && position.distanceTo(GOAL) > LANDING_ROOM
            val room = if (measured) room(Terrain.live(player.level()), position) else Double.MAX_VALUE
            Triple(player.isFallFlying, touching && position.distanceTo(GOAL) > LANDING_REACH, room)
        }
        val (gliding, touched, room) = state
        val position = context.playerPosition()
        if (gliding) {
            glidingTicks++
            length += position.distanceTo(last)
            if (room < leastRoom) {
                leastRoom = room
                leastRoomAt = position
            }
            if (touched) {
                contacts++
                logger.info("TUNNEL contact at {} after {} ticks", position, tick)
            }
        }
        last = position

        if (!hasEnded()) {
            context.waitTick()
            continue
        }
        logger.info(
            "TUNNEL glided {} blocks in {} ticks ({} gliding), least room {} at {}, {} contacts",
            "%.0f".format(length), tick, glidingTicks, "%.2f".format(leastRoom), leastRoomAt, contacts,
        )
        check(glidingTicks > 0) { "Got through the cavern without gliding" }
        check(leastRoom >= CLEARANCE - ROOM_STEP / 2) { "Came within $leastRoom of a block at $leastRoomAt" }
        check(contacts == 0) { "Touched blocks on $contacts ticks while gliding" }
        check(position.distanceTo(GOAL) <= ARRIVAL + 0.1) { "Stopped at $position, away from $GOAL" }
        return
    }
    error("Did not glide through the cavern within $FLIGHT_TICKS ticks, stopped at ${context.playerPosition()}")
}

/**
 * The most room up to 3 blocks, in tenths, the body at [feet] has to every block.
 *
 * @agent claude-opus-5-5
 */
private fun room(terrain: Terrain, feet: Vec3): Double {
    var margin = 0.0
    while (margin < MAX_ROOM && terrain.isClear(Passage.box(feet, margin + ROOM_STEP))) {
        margin += ROOM_STEP
    }
    return margin
}

private const val MAX_ROOM = 3.0
private const val ROOM_STEP = 0.1
