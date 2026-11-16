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

package net.ccbluex.agent.skills

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.item.ItemStack

/**
 * What an [Agent] reports while it plays; every method is optional and called on the ticking thread.
 *
 * @agent claude-opus-5-5
 */
public interface AgentListener {

    /**
     * A choice was made: [label] won with [score]; [reason] says what tipped it.
     */
    public fun decision(label: String, score: Double, reason: String) {}

    /**
     * The game mode entered phase [name].
     */
    public fun phase(name: String) {}

    public fun engaged(target: LivingEntity) {}

    public fun ate(item: ItemStack) {}

    public fun looted(pos: BlockPos) {}

    public fun died(killer: Entity?) {}

    public fun roundEnded(won: Boolean) {}

    public companion object {
        @JvmField
        public val NONE: AgentListener = object : AgentListener {}
    }
}
