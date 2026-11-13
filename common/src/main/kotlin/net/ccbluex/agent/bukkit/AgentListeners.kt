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

package net.ccbluex.agent.bukkit

import net.ccbluex.agent.skills.AgentListener
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Passes what an agent reports on to every listener of its bot.
 *
 * @agent claude-opus-5-5
 */
internal class AgentListeners : AgentListener {

    private val listeners = CopyOnWriteArrayList<AgentListener>()

    fun add(listener: AgentListener) {
        listeners.addIfAbsent(listener)
    }

    fun remove(listener: AgentListener) {
        listeners.remove(listener)
    }

    override fun decision(label: String, score: Double, reason: String) =
        listeners.forEach { it.decision(label, score, reason) }

    override fun phase(name: String) = listeners.forEach { it.phase(name) }

    override fun engaged(target: LivingEntity) = listeners.forEach { it.engaged(target) }

    override fun ate(item: ItemStack) = listeners.forEach { it.ate(item) }

    override fun looted(pos: BlockPos) = listeners.forEach { it.looted(pos) }

    override fun died(killer: Entity?) = listeners.forEach { it.died(killer) }

    override fun roundEnded(won: Boolean) = listeners.forEach { it.roundEnded(won) }
}
