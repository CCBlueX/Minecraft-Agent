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

package net.ccbluex.agent.navigation.ride

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.pathfinder.ride.Vehicle
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Drives a ridden vehicle by movement input: a boat with left, right and forward, a mount by where its rider looks.
 *
 * From LiquidBounce: [Steering.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/ride/Steering.kt#L37-L113)
 *
 * @agent claude-opus-5-5
 */
internal class Steering(private val body: Body) {

    private var lastYRot = Float.NaN

    /**
     * Ticks the jump key is held for the next jump, counting on after its release until the jump is retried.
     */
    private var chargeTicks = 0

    fun reset() {
        lastYRot = Float.NaN
        chargeTicks = 0
    }

    /**
     * Turns [boat] towards [target] and paddles once it faces it; with [brake], only while it is slow.
     */
    fun boat(boat: Entity, target: Vec3, brake: Boolean) {
        val yRot = boat.yRot
        val turnRate = if (lastYRot.isNaN()) 0f else Mth.wrapDegrees(yRot - lastYRot)
        lastYRot = yRot

        // The boat keeps turning after the key is released, the more the less its floor slows it.
        val friction = if (isOnIce(boat)) ICE_FRICTION else WATER_FRICTION
        val coast = turnRate * friction / (1f - friction)
        val off = Mth.wrapDegrees(target.subtract(boat.position()).yRot - yRot)
        val error = off - coast

        val slowEnough = !brake || boat.deltaMovement.horizontalDistance() < BRAKE_SPEED
        body.press(
            Input(
                abs(off) < BOAT_FORWARD_ANGLE && slowEnough,
                !slowEnough,
                error < -BOAT_DEAD_ANGLE,
                error > BOAT_DEAD_ANGLE,
                false,
                false,
                false,
            ),
        )
    }

    /**
     * Rides towards [target]; within [reach] of [jumpTo], where a jump leads, the jump is charged and released.
     */
    fun mount(mount: Entity, target: Vec3, jumpTo: Vec3?, reach: Double) {
        val desired = target.subtract(mount.position()).yRot
        val facing = face(desired)
        val aligned = abs(Mth.wrapDegrees(desired - facing)) < MOUNT_FORWARD_ANGLE

        val atJump = jumpTo != null && mount.position().horizontalDistanceTo(jumpTo) < reach
        chargeTicks = if (atJump) chargeTicks + 1 else 0
        val jump = chargeTicks in 1..Vehicle.JUMP_CHARGE_TICKS
        body.press(Input(aligned, false, false, false, jump, false, false))
        if (chargeTicks > Vehicle.JUMP_CHARGE_TICKS + JUMP_RETRY_TICKS) {
            chargeTicks = 0
        }
    }

    fun stop(sneak: Boolean = false) {
        reset()
        body.press(Input(false, false, false, false, false, sneak, false))
    }

    /**
     * Turns the rider's view at most [MAX_TURN] towards [yRot] and returns the yaw it now faces. A ridden mount goes
     * the way its rider looks, so this is the view itself, not a rotation only the server sees.
     */
    fun face(yRot: Float): Float {
        val player = body.player
        val facing = player.yRot + Mth.wrapDegrees(yRot - player.yRot).coerceIn(-MAX_TURN, MAX_TURN)
        body.look(ViewRotation(facing, player.xRot), LookPurpose.VIEW)
        return facing
    }

    private fun isOnIce(boat: Entity): Boolean {
        val below = BlockPos.containing(boat.x, boat.y - FLOOR_PROBE, boat.z)
        return body.level.getBlockState(below).block.friction >= ICE_THRESHOLD
    }

    private companion object {
        const val WATER_FRICTION = 0.9f
        const val ICE_FRICTION = 0.98f
        const val ICE_THRESHOLD = 0.9f
        const val BOAT_DEAD_ANGLE = 4f
        const val BOAT_FORWARD_ANGLE = 35f
        const val BRAKE_SPEED = 0.12
        const val MOUNT_FORWARD_ANGLE = 50f
        const val MAX_TURN = 35f
        const val JUMP_RETRY_TICKS = 20
        const val FLOOR_PROBE = 0.1
    }

}
