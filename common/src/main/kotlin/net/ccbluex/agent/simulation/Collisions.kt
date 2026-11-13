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

import net.ccbluex.agent.simulation.PlayerMotion.Companion.HALF_WIDTH
import net.ccbluex.agent.simulation.PlayerMotion.Companion.HEIGHT
import net.ccbluex.agent.simulation.PlayerMotion.Companion.STEP_HEIGHT
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val STEP_PROBE = 1.0E-5
private const val EDGE_INSET = 1.0E-7

/**
 * Entity.collide: the movement the blocks in the way leave, stepping up ledges of up to [STEP_HEIGHT].
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L477-L504)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun PlayerMotion.collide(dx: Double, dy: Double, dz: Double): Vec3 {
    val box = AABB(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH)
    val movement = Vec3(dx, dy, dz)
    val direct = if (movement.lengthSqr() == 0.0) {
        movement
    } else {
        colliders.collect(box.expandTowards(movement))
        colliders.collide(dx, dy, dz, box)
    }
    val lands = dy != direct.y && dy < 0.0
    if (!(lands || onGround) || dx == direct.x && dz == direct.z) {
        return direct
    }

    val grounded = if (lands) box.move(0.0, direct.y, 0.0) else box
    var region = grounded.expandTowards(dx, STEP_HEIGHT.toDouble(), dz)
    if (!lands) {
        region = region.expandTowards(0.0, -STEP_PROBE, 0.0)
    }
    colliders.collect(region)
    for (height in colliders.stepHeights(grounded, STEP_HEIGHT, skip = direct.y.toFloat())) {
        val stepped = colliders.collide(dx, height.toDouble(), dz, grounded)
        if (stepped.horizontalDistanceSqr() > direct.horizontalDistanceSqr()) {
            return stepped.subtract(0.0, box.minY - grounded.minY, 0.0)
        }
    }
    return direct
}

/**
 * Whether nothing is within [depth] below the player moved by [dx] and [dz].
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L336-L351)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun PlayerMotion.canFall(dx: Double, dz: Double, depth: Double): Boolean {
    val minX = x - HALF_WIDTH + EDGE_INSET + dx
    val minZ = z - HALF_WIDTH + EDGE_INSET + dz
    val width = 2 * (HALF_WIDTH - EDGE_INSET)
    val probe = AABB(minX, y - depth - EDGE_INSET, minZ, minX + width, y, minZ + width)
    for (bx in Mth.floor(probe.minX)..Mth.floor(probe.maxX)) {
        for (bz in Mth.floor(probe.minZ)..Mth.floor(probe.maxZ)) {
            for (by in Mth.floor(probe.minY) - 1..Mth.floor(probe.maxY)) {
                if (colliders.intersects(bx, by, bz, probe)) {
                    return false
                }
            }
        }
    }
    return true
}
