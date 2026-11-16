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

import net.ccbluex.agent.math.Vec2
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.entity.MoverType

/**
 * How a host changes the movement of its own player, so a [SimulatedPlayer] of that player moves the same. Every
 * default is vanilla; pass hooks only for the player the host changes.
 *
 * @agent claude-opus-5-5
 */
public interface SimulationHooks {

    /**
     * Sideways (x) and forward (y) speed factors while using an item whose use effects slow by [multiplier].
     */
    public fun itemUseMultiplier(multiplier: Float): Vec2 = Vec2(multiplier, multiplier)

    /**
     * The sneaking speed factor, from the player's sneaking speed attribute [speed].
     */
    public fun sneakingSpeed(speed: Double): Double = speed

    /**
     * The movement Entity.move gets for [movement] of [type].
     */
    public fun move(type: MoverType, movement: Vec3): Vec3 = movement

    /**
     * Whether using an item that cannot be used while sprinting stops sprinting.
     */
    public fun slowedByItemUse(): Boolean = true

    /**
     * Whether the host holds the player back at ledges as sneaking does, without sneaking (safe walk). Asked once
     * when the simulation is made.
     *
     * @agent claude-opus-5-5
     */
    public fun isStayingOnGroundSurface(): Boolean = false

    public companion object {
        @JvmField
        public val VANILLA: SimulationHooks = object : SimulationHooks {}
    }
}
