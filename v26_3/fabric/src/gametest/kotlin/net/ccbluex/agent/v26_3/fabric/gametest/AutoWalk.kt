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

import com.mojang.authlib.GameProfile
import net.ccbluex.agent.v26_3.fabric.ClientNavigation
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.DestinationSource
import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.v26_3.toAgent
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.abs
import net.ccbluex.agent.math.Vec3 as AgentVec3

/**
 * LiquidBounce's AutoWalk on a [ClientNavigation] with the default options: `.goto` walks onto the ground at a block
 * and stops on arrival, `.follow` walks up to a player, Direction walks the way the player faces. Only on the client
 * thread.
 *
 * From LiquidBounce: [ModuleAutoWalk.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/ModuleAutoWalk.kt#L100-L188)
 *
 * @agent claude-opus-5-5
 */
internal object AutoWalk {

    private var current: ClientNavigation? = null

    /**
     * The navigation of every walk, with the key timing of the scenario that started it.
     */
    private val navigation: ClientNavigation
        get() = (current ?: ClientNavigation(KeyTiming.body()).also { current = it })
            .also { it.options = KeyTiming.options(Options()) }

    private val player: LocalPlayer
        get() = checkNotNull(Minecraft.getInstance().player)

    val running: Boolean
        get() = current?.navigator?.isInProgress == true

    fun goTo(destination: Vec3) = navigation.navigator.follow(Goto(BlockPos.containing(destination)))

    fun follow(target: String) = navigation.navigator.follow(Follow(target))

    fun walkFacing() {
        val direction = Direction()
        GameTicks.afterPlayerTick.addIfAbsent(direction.tickEnd)
        this.direction = direction
        navigation.navigator.follow(direction)
    }

    fun stop() {
        current?.navigator?.stop()
        direction?.let { GameTicks.afterPlayerTick.remove(it.tickEnd) }
        direction = null
    }

    private var direction: Direction? = null

    /**
     * Stops on arrival.
     */
    private class Goto(private val destination: BlockPos) : DestinationSource {

        private var goal = Vec3.ZERO
        private var known = false

        override fun goal(): AgentVec3? {
            if (!known) {
                val level = player.level()
                goal = Vec3.atBottomCenterOf(feetAt(level, destination))
                // Whether the destination is the ground to stand on is only known once its chunk is loaded.
                known = level.hasChunkAt(destination.x, destination.z)
            }

            if (player.rootVehicle.position().distanceTo(goal) > radius()) {
                return goal.toAgent()
            }
            stop()
            return null
        }

        /**
         * The ground to stand on: the block above when [pos] is not but that one is.
         */
        private fun feetAt(level: Level, pos: BlockPos): BlockPos {
            val terrain = Terrain.live(level.toAgent())
            val above = pos.above()
            return if (!terrain.isStandable(pos.toAgent()) && terrain.isStandable(above.toAgent())) above else pos
        }
    }

    private class Follow(private val target: String) : DestinationSource {

        /**
         * Walked on to once [target] goes out of sight.
         */
        private var lastSeen: Vec3? = null

        override fun goal(): AgentVec3? {
            val player = player
            player.level().players().find { it !== player && it.gameProfile.name.equals(target, true) }
                ?.let { lastSeen = it.position() }
            return lastSeen?.toAgent()
        }

        override fun radius() = FOLLOW_DISTANCE
    }

    /**
     * Keeps a goal [DISTANCE] blocks the way the user faces, set again once the user turned the view or half the way
     * is walked. The camera turns with the walk too, so only its turns between ticks, the mouse's, count.
     */
    private class Direction : DestinationSource {

        private var goal: AgentVec3? = null
        private var goalYRot = 0f
        private var heading = Float.NaN
        private var tickEndYRot = Float.NaN

        val tickEnd = Consumer<LocalPlayer> { tickEndYRot = it.yRot }

        override fun goal(): AgentVec3 {
            val player = player
            val position = player.position().toAgent()
            heading = if (heading.isNaN() || tickEndYRot.isNaN()) {
                player.yRot
            } else {
                heading + Mth.wrapDegrees(player.yRot - tickEndYRot)
            }
            val yRot = heading
            val goal = goal
            if (goal != null && position.horizontalDistanceTo(goal) > REFRESH &&
                abs(Mth.wrapDegrees(yRot - goalYRot)) <= MAX_TURN
            ) {
                return goal
            }

            goalYRot = yRot
            return position.add(ViewRotation(yRot, 0f).viewVector.scale(DISTANCE)).also { this.goal = it }
        }

        override fun radius() = DIRECTION_RADIUS
    }

    private const val FOLLOW_DISTANCE = 3.0
    private const val DISTANCE = 1024.0
    private const val REFRESH = 512.0
    private const val DIRECTION_RADIUS = 2.0
    private const val MAX_TURN = 20f

}

/**
 * A player only this client knows, standing at [position], like LiquidBounce's `.fakeplayer spawn`.
 *
 * @agent claude-opus-5-5
 */
internal fun Minecraft.spawnTwin(name: String, position: Vec3): Int {
    val level = checkNotNull(level)
    val twin = RemotePlayer(level, GameProfile(UUID.randomUUID(), name))
    twin.id = TWIN_ID
    twin.snapTo(position.x, position.y, position.z)
    level.addEntity(twin)
    return twin.id
}

/**
 * @agent claude-opus-5-5
 */
internal fun Minecraft.removeTwin(id: Int) {
    level?.removeEntity(id, Entity.RemovalReason.DISCARDED)
}

/**
 * Below the ids the server hands out.
 */
private const val TWIN_ID = -1_000_000

/**
 * Goes to [goal] like `.goto` until it stops within [arrival] of it; true if [flying] held on the way.
 *
 * From LiquidBounce: [NavigationGameTests.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/NavigationGameTests.kt#L207-L233)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun goto(
    context: ClientGameTestContext,
    goal: Vec3,
    what: String,
    ticks: Int,
    arrival: Double,
    flying: (LocalPlayer) -> Boolean,
): Boolean {
    context.onClient { AutoWalk.goTo(goal) }
    check(context.fromClient { AutoWalk.running }) { "goTo did not start the navigation" }

    var flew = false
    repeat(ticks) { tick ->
        val (arrived, now) = context.fromClient { mc -> !AutoWalk.running to flying(checkNotNull(mc.player)) }
        flew = flew || now
        if (arrived) {
            val position = context.playerPosition()
            check(position.distanceTo(goal) <= arrival) { "The navigation stopped at $position going $what" }
            logTicks("Going $what", tick)
            return flew
        }
        context.waitTick()
    }

    val position = context.playerPosition()
    context.onClient { AutoWalk.stop() }
    error("Did not get $what to $goal within $ticks ticks, stopped at $position")
}
