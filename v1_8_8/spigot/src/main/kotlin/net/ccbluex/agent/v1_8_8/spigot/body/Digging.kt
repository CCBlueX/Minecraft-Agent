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

package net.ccbluex.agent.v1_8_8.spigot.body

import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.EnumDirection
import net.minecraft.server.v1_8_R3.Material

/**
 * Digs as a 1.8 client does: it counts the progress itself and sends the start and the finish of a block, which the
 * server's PlayerInteractManager checks against the ticks between them, firing the block events.
 *
 * @agent claude-opus-5-5
 */
internal class Digging(private val player: BotPlayer) {

    private var target: BlockPosition? = null
    private var progress = 0f
    private var delay = 0

    fun start(pos: BlockPosition, face: EnumDirection): Boolean {
        val world = player.world
        if (!world.worldBorder.a(pos)) return false
        if (player.playerInteractManager.isCreative) {
            player.playerInteractManager.a(pos, face)
            delay = AFTER_BREAK_DELAY
            return true
        }
        if (target == pos) return true
        if (target != null) player.playerInteractManager.e()
        val block = world.getType(pos).block
        player.playerInteractManager.a(pos, face)
        target = if (block.material == Material.AIR || block.getDamage(player, world, pos) < 1f) pos else null
        progress = 0f
        player.bw()
        return true
    }

    fun proceed(pos: BlockPosition, face: EnumDirection): Boolean {
        if (delay > 0) {
            delay--
            return true
        }
        if (player.playerInteractManager.isCreative) return start(pos, face)
        if (target != pos) return start(pos, face)
        val world = player.world
        val block = world.getType(pos).block
        if (block.material == Material.AIR) {
            target = null
            return false
        }
        progress += block.getDamage(player, world, pos)
        player.bw()
        if (progress >= 1f) {
            player.playerInteractManager.a(pos)
            target = null
            progress = 0f
            delay = AFTER_BREAK_DELAY
        }
        return true
    }

    fun stop() {
        if (target == null) return
        player.playerInteractManager.e()
        target = null
        progress = 0f
    }

    private companion object {
        // PlayerControllerMP waits this long after a break before it digs on
        const val AFTER_BREAK_DELAY = 5
    }
}
