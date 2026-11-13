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

@file:JvmName("ClientHooks")

package net.ccbluex.agent.v26_3.fabric

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.pathfinder.level.SectionCache
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.CopyOnWriteArrayList

// What the mixins call. Everything runs on the client thread.

/**
 * Ticks a mouse turn keeps navigation's view turns off.
 */
private const val MOUSE_HOLD_TICKS = 40

internal val runningNavigations = CopyOnWriteArrayList<ClientNavigation>()

/**
 * The rotation of the last movement packet that had one; null before the first.
 */
internal var lastSentRotation: ViewRotation? = null
    private set

/**
 * The level whose section copies [levelChanged] drops once the client leaves it.
 */
private var currentLevel: ClientLevel? = null

private var playerTicks = 0
private var mouseTurnedAt = Int.MIN_VALUE / 2

/**
 * @agent claude-opus-5-5
 */
internal fun isMouseTurning() = playerTicks - mouseTurnedAt < MOUSE_HOLD_TICKS

/**
 * In `LocalPlayer.aiStep`, right after the input read the keys: runs the navigations, which press keys and ask for
 * rotations, and applies both before the player moves.
 *
 * @agent claude-opus-5-5
 */
internal fun afterInputTick(player: LocalPlayer, input: ClientInput) {
    playerTicks++
    for (navigation in runningNavigations) {
        navigation.tick()
        navigation.body.applyTick(player, input)
    }
}

/**
 * In `Minecraft.tick`, right after the player sent its movement: the player looks through its camera again, which the
 * navigations then turn the way they go.
 *
 * @agent claude-opus-5-5
 */
internal fun afterMovementSent() {
    for (navigation in runningNavigations) {
        navigation.body.restoreCamera()
        navigation.navigator.turnView()
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun holdsAtLedges(player: Player) =
    player === Minecraft.getInstance().player && runningNavigations.any { it.body.safeWalk }

/**
 * Whether `Minecraft.continueAttack` must not stop the dig of a navigation while the attack key is up.
 *
 * @agent claude-opus-5-5
 */
internal fun keepsDestroying() = runningNavigations.any { it.body.isDestroying }

/**
 * @agent claude-opus-5-5
 */
internal fun packetSent(packet: Packet<*>) {
    if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
        lastSentRotation = ViewRotation(packet.getYRot(0f), packet.getXRot(0f))
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun mouseTurned() {
    mouseTurnedAt = playerTicks
}

/**
 * A block of [level] the server sent, from a block or section update.
 *
 * @agent claude-opus-5-5
 */
internal fun blockConfirmed(level: ClientLevel, pos: BlockPos, state: BlockState) {
    if (level !== Minecraft.getInstance().level) {
        return
    }
    val immutable = pos.immutable()
    runningNavigations.forEach { it.navigator.blockConfirmed(immutable, state) }
}

/**
 * @agent claude-opus-5-5
 */
internal fun blockChanged(level: ClientLevel, pos: BlockPos) {
    if (level !== Minecraft.getInstance().level) {
        return
    }
    val immutable = pos.immutable()
    SectionCache.blockChanged(level, immutable)
    runningNavigations.forEach { it.navigator.blockChanged(immutable) }
}

/**
 * The chunk column at [chunkX], [chunkZ] of the current level was loaded or unloaded.
 *
 * @agent claude-opus-5-5
 */
internal fun columnChanged(chunkX: Int, chunkZ: Int) {
    Minecraft.getInstance().level?.let { SectionCache.columnChanged(it, chunkX, chunkZ) }
}

/**
 * The client switched to [level], or left the world with null.
 *
 * @agent claude-opus-5-5
 */
internal fun levelChanged(level: ClientLevel?) {
    currentLevel?.let(SectionCache::forget)
    currentLevel = level
    lastSentRotation = null
    runningNavigations.forEach { it.navigator.levelChanged() }
}

/**
 * @agent claude-opus-5-5
 */
internal fun positionCorrected() {
    for (navigation in runningNavigations) {
        navigation.body.positionCorrected()
        navigation.navigator.positionCorrected()
    }
}
