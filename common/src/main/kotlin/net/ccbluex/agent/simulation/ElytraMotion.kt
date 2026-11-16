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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.LivingEntity.Companion.DEFAULT_BASE_GRAVITY
import net.ccbluex.agent.world.entity.LivingEntity.Companion.ELYTRA_HORIZONTAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.ELYTRA_VERTICAL_AIR_DRAG
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elytra flight as the game moves it, one tick at a time.
 *
 * From LiquidBounce: [ElytraMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/ElytraMotion.kt#L34-L83)
 *
 * @agent claude-opus-5-5
 */
public object ElytraMotion {

    private const val LIFT = 0.75
    private const val DIVE = 0.1
    private const val CLIMB = 0.04
    private const val CLIMB_LIFT = 3.2
    private const val TURN = 0.1
    private const val ROCKET_PUSH = 0.1
    private const val ROCKET_SPEED = 1.5
    private const val ROCKET_PULL = 0.5

    public fun updateFallFlyingMovement(deltaMovement: Vec3, yRot: Float, xRot: Float, isBoosted: Boolean): Vec3 {
        val look = Vec3.directionFromRotation(xRot, yRot)
        val lean = Math.toRadians(xRot.toDouble())
        val lookHorizontal = look.horizontalDistance()
        val moveHorizontal = deltaMovement.horizontalDistance()
        val lift = cos(lean) * cos(lean)

        var x = deltaMovement.x
        var y = deltaMovement.y + DEFAULT_BASE_GRAVITY * (-1.0 + lift * LIFT)
        var z = deltaMovement.z
        if (lookHorizontal > 0.0) {
            if (y < 0.0) {
                val convert = y * -DIVE * lift
                x += look.x * convert / lookHorizontal
                y += convert
                z += look.z * convert / lookHorizontal
            }
            if (lean < 0.0) {
                val convert = moveHorizontal * -sin(lean) * CLIMB
                x -= look.x * convert / lookHorizontal
                y += convert * CLIMB_LIFT
                z -= look.z * convert / lookHorizontal
            }
            x += (look.x / lookHorizontal * moveHorizontal - x) * TURN
            z += (look.z / lookHorizontal * moveHorizontal - z) * TURN
        }

        x *= ELYTRA_HORIZONTAL_AIR_DRAG
        y *= ELYTRA_VERTICAL_AIR_DRAG
        z *= ELYTRA_HORIZONTAL_AIR_DRAG
        if (isBoosted) {
            x += look.x * ROCKET_PUSH + (look.x * ROCKET_SPEED - x) * ROCKET_PULL
            y += look.y * ROCKET_PUSH + (look.y * ROCKET_SPEED - y) * ROCKET_PULL
            z += look.z * ROCKET_PUSH + (look.z * ROCKET_SPEED - z) * ROCKET_PULL
        }
        return Vec3(x, y, z)
    }

    public fun yRotTowards(dx: Double, dz: Double): Float = Mth.wrapDegrees(Math.toDegrees(atan2(-dx, dz))).toFloat()

    public fun xRotTowards(up: Double, ahead: Double): Float = (-Math.toDegrees(atan2(up, ahead))).toFloat()

}
