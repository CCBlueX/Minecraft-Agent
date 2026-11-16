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

package net.ccbluex.agent.v26_3.paper.body

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.Teleporter

/**
 * Teleports a server bot by setting its position step by step: the server is the one that decides where it is.
 *
 * @agent claude-opus-5-5
 */
internal class PositionTeleporter(private val player: BotPlayer) : Teleporter {

    override fun send(position: Vec3, onGround: Boolean) {
        player.moveTo(position.x, position.y, position.z, player.yRot, player.xRot)
        player.setOnGround(onGround)
    }

    override fun land(position: Vec3) {
        val moved = player.rootVehicle
        if (moved === player) {
            player.moveTo(position.x, position.y, position.z, player.yRot, player.xRot)
        } else {
            moved.setPos(position.x, position.y, position.z)
        }
        moved.deltaMovement = net.minecraft.world.phys.Vec3.ZERO
        player.resetFallDistance()
    }
}
