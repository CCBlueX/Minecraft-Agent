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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.InteractionHand

/**
 * Digs as a client digs (MultiPlayerGameMode): it counts the progress itself and tells the server, through the
 * player's game mode, when it starts, changes face, finishes or gives up, as the player action packets would. The
 * server checks the timing, fires the block events and shows the cracks to other players.
 *
 * @agent claude-opus-5-5
 */
internal class Digging(private val player: BotPlayer) {

    private var target: BlockPos? = null
    private var face = Direction.DOWN
    private var progress = 0f
    private var delay = 0

    fun start(pos: BlockPos, face: Direction): Boolean {
        val level = player.level()
        if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false
        if (!level.getWorldBorder().isWithinBounds(pos)) return false
        if (player.getAbilities().instabuild) {
            send(Action.START_DESTROY_BLOCK, pos, face)
            delay = AFTER_BREAK_DELAY
            return true
        }
        val current = target
        if (current == pos) return true
        if (current != null) send(Action.ABORT_DESTROY_BLOCK, current, face)
        val state = level.getBlockState(pos)
        send(Action.START_DESTROY_BLOCK, pos, face)
        if (state.isAir || state.getDestroyProgress(player, level, pos) < 1f) {
            target = pos.immutable()
            this.face = face
            progress = 0f
        } else {
            target = null
        }
        player.swingHand(InteractionHand.MAIN_HAND)
        return true
    }

    fun proceed(pos: BlockPos, face: Direction): Boolean {
        if (delay > 0) {
            delay--
            return true
        }
        val level = player.level()
        if (player.getAbilities().instabuild && level.getWorldBorder().isWithinBounds(pos)) {
            send(Action.START_DESTROY_BLOCK, pos, face)
            delay = AFTER_BREAK_DELAY
            return true
        }
        if (target != pos) return start(pos, face)
        val state = level.getBlockState(pos)
        if (state.isAir) {
            target = null
            return false
        }
        progress += state.getDestroyProgress(player, level, pos)
        player.swingHand(InteractionHand.MAIN_HAND)
        if (progress >= 1f) {
            send(Action.STOP_DESTROY_BLOCK, pos, face)
            target = null
            progress = 0f
            delay = AFTER_BREAK_DELAY
        } else if (this.face != face) {
            this.face = face
            send(Action.CHANGE_DESTROY_DIRECTION, pos, face)
        }
        return true
    }

    fun stop() {
        val current = target ?: return
        send(Action.ABORT_DESTROY_BLOCK, current, Direction.DOWN)
        target = null
        progress = 0f
        player.resetAttackStrengthTicker()
    }

    private fun send(action: Action, pos: BlockPos, face: Direction) {
        player.gameMode.handleBlockBreakAction(pos, action, face, player.level().getMaxY(), 0)
    }

    private companion object {
        // MultiPlayerGameMode waits this long after a break before it digs on
        const val AFTER_BREAK_DELAY = 5
    }
}
