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

package net.ccbluex.agent.pathfinder.glide

import net.minecraft.world.phys.Vec3

/**
 * Where to look while gliding, whether to fire a rocket now and whether a rocket pushes this tick.
 *
 * @agent claude-opus-5-5
 */
public class Controls(
    public val yRot: Float,
    public val xRot: Float,
    public val shouldFireRocket: Boolean,
    public val isBoosted: Boolean,
)

/**
 * From LiquidBounce: [Steering.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Steering.kt#L27-L34)
 *
 * @agent claude-opus-5-5
 */
public interface Steering {

    /**
     * Where the glide comes down.
     */
    public val target: Vec3

    public fun steer(position: Vec3, deltaMovement: Vec3): Controls

}
