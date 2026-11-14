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

import net.minecraft.world.phys.Vec3

/**
 * A [SimulatedPlayer] after one of its ticks. [hasBackedOffEdge] tells that sneaking or safe walk would have held it back
 * at a ledge in that tick.
 *
 * From LiquidBounce: [PlayerSimulationCache.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L205-L219)
 *
 * @agent claude-opus-5-5
 */
public data class PlayerSnapshot(
    public val pos: Vec3,
    public val fallDistance: Double,
    public val deltaMovement: Vec3,
    public val onGround: Boolean,
    public val hasBackedOffEdge: Boolean,
) {
    public constructor(player: SimulatedPlayer) : this(
        player.position(),
        player.fallDistance,
        player.deltaMovement,
        player.onGround(),
        player.hasBackedOffEdge,
    )
}
