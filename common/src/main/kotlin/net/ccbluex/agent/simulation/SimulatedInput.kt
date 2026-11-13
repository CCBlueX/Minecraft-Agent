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

import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

/**
 * The keys a [SimulatedPlayer] holds, as the client's keyboard input turns them into movement.
 *
 * [forceStayOnGroundSurface] holds the player back at ledges as sneaking does, without sneaking; [ignoresEdgeBackOff] lets it
 * walk off them even while sneaking.
 *
 * From LiquidBounce: [SimulatedPlayer.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/SimulatedPlayer.kt#L58-L440)
 *
 * @agent claude-opus-5-5
 */
public class SimulatedInput @JvmOverloads constructor(
    public val keys: Input,
    public var forceStayOnGroundSurface: Boolean = false,
    public var ignoresEdgeBackOff: Boolean = false,
) {

    /**
     * KeyboardInput.tick: the left (x) and forward (y) impulse of the keys, at most one long.
     */
    public val moveVector: Vec2 =
        Vec2(impulse(keys.left, keys.right), impulse(keys.forward, keys.backward)).normalized()

    /**
     * ClientInput.hasForwardImpulse
     */
    public fun hasForwardImpulse(): Boolean = moveVector.y > MIN_FORWARD_IMPULSE

    override fun toString(): String = "SimulatedInput($keys)"

    private companion object {
        const val MIN_FORWARD_IMPULSE = 1.0E-5f

        /**
         * 1 for only the positive key, -1 for only the negative one, else 0.
         */
        fun impulse(positive: Boolean, negative: Boolean): Float = when {
            positive == negative -> 0f
            positive -> 1f
            else -> -1f
        }
    }
}
