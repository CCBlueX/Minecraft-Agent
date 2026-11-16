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

package net.ccbluex.agent.navigation.glide

import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.glide.GlideAbilities
import net.ccbluex.agent.world.entity.EquipmentSlot

private const val HOTBAR_SIZE = 9

/**
 * How the body can glide now, counting rockets in the offhand and hotbar; null when
 * [net.ccbluex.agent.navigation.Options.canElytra] is off or no elytra is worn.
 *
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.captureGlide(): GlideAbilities? {
    val options = options()
    if (!options.canElytra) {
        return null
    }

    val player = body.player
    val inventory = player.inventory
    val stacks = listOf(player.offhandItem) + (0 until HOTBAR_SIZE).map(inventory::getItem)
    return GlideAbilities.of(player.getItemBySlot(EquipmentSlot.CHEST), stacks, options.elytraRockets)
}
