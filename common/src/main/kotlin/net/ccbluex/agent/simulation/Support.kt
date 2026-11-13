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
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB

private const val SUPPORT_EPSILON = 1.0E-6

/**
 * Entity.mainSupportingBlockPos as plain coordinates: the block the feet stand on, valid while [exists].
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L443-L471)
 *
 * @agent claude-opus-5-5
 */
internal class Support {
    var x = 0
        private set
    var y = 0
        private set
    var z = 0
        private set
    var exists = false
        private set

    fun set(x: Int, y: Int, z: Int) {
        this.x = x
        this.y = y
        this.z = z
        exists = true
    }

    fun clear() {
        exists = false
    }

    fun copyFrom(other: Support) {
        x = other.x
        y = other.y
        z = other.z
        exists = other.exists
    }

    /**
     * Entity.checkSupportingBlock: the block right below feet at [feetX] [feetY] [feetZ] closest to them, looked for
     * [backX] [backZ] back.
     */
    fun find(colliders: BlockColliders, feetX: Double, feetY: Double, feetZ: Double, backX: Double, backZ: Double) {
        val minX = feetX - backX - HALF_WIDTH
        val minZ = feetZ - backZ - HALF_WIDTH
        val probe = AABB(minX, feetY - SUPPORT_EPSILON, minZ, minX + 2 * HALF_WIDTH, feetY, minZ + 2 * HALF_WIDTH)
        var best = Double.MAX_VALUE
        exists = false
        for (bx in Mth.floor(probe.minX)..Mth.floor(probe.maxX)) {
            for (bz in Mth.floor(probe.minZ)..Mth.floor(probe.maxZ)) {
                for (by in Mth.floor(probe.minY) - 1..Mth.floor(probe.maxY)) {
                    if (!colliders.intersects(bx, by, bz, probe)) {
                        continue
                    }

                    val distance = Mth.lengthSquared(bx + 0.5 - feetX, by + 0.5 - feetY, bz + 0.5 - feetZ)
                    if (distance < best) {
                        best = distance
                        set(bx, by, bz)
                    }
                }
            }
        }
    }
}
