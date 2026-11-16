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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.simulation.PlayerMotion.Companion.HALF_WIDTH
import net.ccbluex.agent.simulation.PlayerMotion.Companion.HEIGHT
import net.ccbluex.agent.simulation.PlayerMotion.Companion.STEP_HEIGHT

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
    val minX = x - HALF_WIDTH
    val minZ = z - HALF_WIDTH
    val maxX = x + HALF_WIDTH
    val maxY = y + HEIGHT
    val maxZ = z + HALF_WIDTH
    var directX = dx
    var directY = dy
    var directZ = dz
    if (dx * dx + dy * dy + dz * dz != 0.0) {
        // AABB.expandTowards by the movement
        colliders.collect(
            if (dx < 0.0) minX + dx else minX, if (dy < 0.0) y + dy else y, if (dz < 0.0) minZ + dz else minZ,
            if (dx > 0.0) maxX + dx else maxX, if (dy > 0.0) maxY + dy else maxY, if (dz > 0.0) maxZ + dz else maxZ,
        )
        colliders.collide(dx, dy, dz, minX, y, minZ, maxX, maxY, maxZ)
        directX = colliders.movedX
        directY = colliders.movedY
        directZ = colliders.movedZ
    }
    val lands = dy != directY && dy < 0.0
    if (!(lands || onGround) || dx == directX && dz == directZ) {
        return Vec3(directX, directY, directZ)
    }

    val box = AABB(minX, y, minZ, maxX, maxY, maxZ)
    val grounded = if (lands) box.move(0.0, directY, 0.0) else box
    var region = grounded.expandTowards(dx, STEP_HEIGHT.toDouble(), dz)
    if (!lands) {
        region = region.expandTowards(0.0, -STEP_PROBE, 0.0)
    }
    colliders.collect(region.minX, region.minY, region.minZ, region.maxX, region.maxY, region.maxZ)
    for (height in colliders.stepHeights(grounded, STEP_HEIGHT, skip = directY.toFloat())) {
        colliders.collide(
            dx, height.toDouble(), dz, grounded.minX, grounded.minY, grounded.minZ,
            grounded.maxX, grounded.maxY, grounded.maxZ,
        )
        val steppedX = colliders.movedX
        val steppedZ = colliders.movedZ
        if (steppedX * steppedX + steppedZ * steppedZ > directX * directX + directZ * directZ) {
            return Vec3(steppedX, colliders.movedY - (box.minY - grounded.minY), steppedZ)
        }
    }
    return Vec3(directX, directY, directZ)
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
