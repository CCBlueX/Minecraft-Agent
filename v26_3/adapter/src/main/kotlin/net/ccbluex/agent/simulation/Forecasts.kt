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

import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * The [Forecast]s of players whose keys this side does not know, each made once with the keys [SimulatedInput.guess]
 * reads from its last move. They hold until [clear], which the host calls every tick once the players moved on.
 *
 * From LiquidBounce: [PlayerSimulationCache.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L42-L137)
 *
 * @agent claude-opus-5-5
 */
public class Forecasts {

    private val forecasts = ConcurrentHashMap<Player, Forecast>()

    public fun of(player: Player): Forecast = forecasts.computeIfAbsent(player) {
        Forecast(SimulatedPlayer.fromOtherPlayer(it, SimulatedInput.guess(it.toAgent())))
    }

    public fun clear() {
        forecasts.clear()
    }
}
