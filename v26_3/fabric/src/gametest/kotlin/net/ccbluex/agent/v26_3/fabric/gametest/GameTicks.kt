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

package net.ccbluex.agent.v26_3.fabric.gametest

import net.minecraft.client.player.LocalPlayer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Points of the client tick tests watch, called by the game test mixins on the client thread: the start of the
 * client tick, the player's tick right after the input read the keys (where navigation runs), and the end of the
 * player's tick.
 *
 * @agent claude-opus-5-5
 */
public object GameTicks {

    internal val clientTick = CopyOnWriteArrayList<Runnable>()
    internal val afterInput = CopyOnWriteArrayList<Consumer<LocalPlayer>>()
    internal val afterPlayerTick = CopyOnWriteArrayList<Consumer<LocalPlayer>>()

    @JvmStatic
    public fun startOfClientTick() {
        clientTick.forEach(Runnable::run)
    }

    @JvmStatic
    public fun afterInput(player: LocalPlayer) {
        afterInput.forEach { it.accept(player) }
    }

    @JvmStatic
    public fun afterPlayerTick(player: LocalPlayer) {
        afterPlayerTick.forEach { it.accept(player) }
    }

}
