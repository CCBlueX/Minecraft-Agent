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

package net.ccbluex.agent.navigation

import net.minecraft.world.phys.Vec3

/**
 * Where a mover is on its route and the corners still ahead, sampled by a renderer every frame.
 *
 * From LiquidBounce: [RouteView.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/render/route/RouteView.kt#L41-L52)
 *
 * @agent claude-fable-5-2
 */
public interface Progress {

    public val isMoving: Boolean

    public fun head(partialTicks: Float): Vec3

    public val ahead: List<Vec3>

}

/**
 * The player of [body] heading straight for [target] while [moving].
 *
 * From LiquidBounce: [RouteView.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/render/route/RouteView.kt#L52-L63)
 *
 * @agent claude-opus-5-5
 */
internal class TargetProgress(
    private val body: Body,
    private val moving: () -> Boolean,
    private val target: () -> Vec3?,
) : Progress {

    override val isMoving: Boolean
        get() = moving()

    override val ahead: List<Vec3>
        get() = listOfNotNull(target())

    override fun head(partialTicks: Float): Vec3 = body.player.getPosition(partialTicks)

}
