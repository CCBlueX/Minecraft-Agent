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

package net.ccbluex.agent.v26_3.paper

import net.ccbluex.agent.bukkit.Bot
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Keeps the chunks around bodies loaded, as a player's view does, with plugin chunk tickets counted per chunk so
 * bodies sharing a chunk do not let it go for each other.
 *
 * @agent claude-opus-5-5
 */
internal class ChunkHolds(private val plugin: Plugin) {

    private data class Hold(val world: UUID, val x: Int, val z: Int)

    private val held = HashMap<Bot, Hold>()
    private val counts = HashMap<Hold, Int>()

    /**
     * Holds the chunks within [RADIUS] of the one [bot] stands in, and lets go of those it left.
     */
    fun hold(bot: Bot, world: World, chunkX: Int, chunkZ: Int) {
        val center = Hold(world.uid, chunkX, chunkZ)
        if (held[bot] == center) return
        release(bot)
        held[bot] = center
        around(center) { hold ->
            if (counts.merge(hold, 1, Int::plus) == 1) world.addPluginChunkTicket(hold.x, hold.z, plugin)
        }
    }

    fun release(bot: Bot) {
        val center = held.remove(bot) ?: return
        val world = plugin.server.getWorld(center.world)
        around(center) { hold ->
            val left = (counts[hold] ?: 1) - 1
            if (left > 0) {
                counts[hold] = left
            } else {
                counts -= hold
                world?.removePluginChunkTicket(hold.x, hold.z, plugin)
            }
        }
    }

    private inline fun around(center: Hold, each: (Hold) -> Unit) {
        for (x in center.x - RADIUS..center.x + RADIUS) {
            for (z in center.z - RADIUS..center.z + RADIUS) each(Hold(center.world, x, z))
        }
    }

    private companion object {
        const val RADIUS = 1
    }
}
