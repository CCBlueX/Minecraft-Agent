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
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.level.block.state.BlockState
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

// What the mixins call. Everything runs on the client thread, for the account the game plays as at that moment: a
// game that runs several accounts at once ticks each of them with its own player loaded.

/**
 * Ticks a mouse turn keeps navigation's view turns off.
 */
private const val MOUSE_HOLD_TICKS = 40

/**
 * Player ticks of all accounts after which an account that did not tick counts as gone.
 */
private const val GONE_AFTER_TICKS = 100

/**
 * Every navigation made and still kept by its owner.
 */
private val navigations = CopyOnWriteArrayList<WeakReference<ClientNavigation>>()

/**
 * What the hooks keep for the player of one account.
 *
 * @agent claude-opus-5-5
 */
private class AccountState {

    /**
     * The navigations that went on with a walk in the account's last player tick.
     */
    val walking = ArrayList<ClientNavigation>()

    /**
     * The rotation of the last movement packet that had one; null before the first.
     */
    var lastSentRotation: ViewRotation? = null

    /**
     * The level whose section copies [levelChanged] drops once the player leaves it.
     */
    var level: ClientLevel? = null

    var playerTicks = 0
    var mouseTurnedAt = Int.MIN_VALUE / 2

    /**
     * The value of [allPlayerTicks] at the account's last player tick.
     */
    var tickedAt = 0
}

private val accounts = HashMap<String, AccountState>()

/**
 * Player ticks of every account together.
 */
private var allPlayerTicks = 0

/**
 * @agent claude-opus-5-5
 */
internal fun currentAccount(): String = Minecraft.getInstance().getUser().name

/**
 * @agent claude-opus-5-5
 */
private fun currentState(): AccountState? = accounts[currentAccount()]

/**
 * The rotation of the last movement packet of the current account that had one; null before the first.
 */
internal val lastSentRotation: ViewRotation?
    get() = currentState()?.lastSentRotation

/**
 * @agent claude-opus-5-5
 */
internal fun isMouseTurning(): Boolean {
    val state = currentState() ?: return false
    return state.playerTicks - state.mouseTurnedAt < MOUSE_HOLD_TICKS
}

/**
 * A new navigation hears the client's world from now on, until its owner lets go of it.
 *
 * @agent claude-opus-5-5
 */
internal fun register(navigation: ClientNavigation) {
    navigations.removeIf { it.get() == null }
    navigations += WeakReference(navigation)
    // Its account counts as playing until its player had time for a first tick
    accounts.getOrPut(navigation.account) { AccountState().also { it.tickedAt = allPlayerTicks } }
}

/**
 * The navigations of the current account.
 *
 * @agent claude-opus-5-5
 */
private inline fun forEachNavigation(action: (ClientNavigation) -> Unit) {
    val account = currentAccount()
    for (reference in navigations) {
        reference.get()?.takeIf { it.account == account }?.let(action)
    }
}

/**
 * An idle navigation whose account has not played for a while, after an account switch or a closed connection,
 * takes up [account].
 *
 * @agent claude-opus-5-5
 */
private fun ClientNavigation.adopt(account: String) {
    if (this.account == account || !isIdle) {
        return
    }
    val tickedAt = accounts[this.account]?.tickedAt ?: Int.MIN_VALUE / 2
    if (allPlayerTicks - tickedAt > GONE_AFTER_TICKS) {
        this.account = account
    }
}

/**
 * In `LocalPlayer.aiStep`, right after the input read the keys: runs the navigations that hold a walk, which press
 * keys and ask for rotations, and applies both before the player moves.
 *
 * @agent claude-opus-5-5
 */
internal fun afterInputTick(player: LocalPlayer, input: ClientInput) {
    val account = currentAccount()
    val state = accounts.getOrPut(account, ::AccountState)
    state.playerTicks++
    state.tickedAt = ++allPlayerTicks
    state.walking.clear()
    for (reference in navigations) {
        val navigation = reference.get() ?: continue
        navigation.adopt(account)
        if (navigation.account == account && navigation.tick()) {
            state.walking += navigation
            navigation.body.applyTick(player, input)
        }
    }
}

/**
 * In `Minecraft.tick`, right after the player sent its movement: the player looks through its camera again, which the
 * navigations then turn the way they go.
 *
 * @agent claude-opus-5-5
 */
internal fun afterMovementSent() {
    for (navigation in currentState()?.walking.orEmpty()) {
        navigation.body.restoreCamera()
        navigation.navigator.turnView()
    }
}

/**
 * Whether `Minecraft.continueAttack` must not stop the dig of a navigation while the attack key is up.
 *
 * @agent claude-opus-5-5
 */
internal fun keepsDestroying() = currentState()?.walking.orEmpty().any { it.body.isDestroying }

/**
 * Bodies that started an item use vanilla must not release, with the account they use it on.
 */
private val usingBodies = WeakHashMap<ClientBody, String>()

/**
 * Whether [body] keeps the item use it started going, on the current account, until the player stops using it.
 *
 * @agent claude-opus-5-5
 */
internal fun keepUsing(body: ClientBody, keep: Boolean) {
    if (keep) usingBodies[body] = currentAccount() else usingBodies.remove(body)
}

/**
 * Whether `Minecraft.handleKeybinds` must not release the item a body uses while the use key is up.
 *
 * @agent claude-opus-5-5
 */
internal fun keepsUsing(): Boolean {
    val account = currentAccount()
    if (Minecraft.getInstance().player?.isUsingItem != true) {
        usingBodies.values.removeIf { it == account }
        return false
    }
    return account in usingBodies.values
}

/**
 * @agent claude-opus-5-5
 */
internal fun packetSent(packet: Packet<*>) {
    if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
        val state = accounts.getOrPut(currentAccount(), ::AccountState)
        state.lastSentRotation = ViewRotation(packet.getYRot(0f), packet.getXRot(0f))
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun mouseTurned() {
    currentState()?.let { it.mouseTurnedAt = it.playerTicks }
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
    val agentPos = pos.toAgent()
    val agentState = state.toAgent()
    forEachNavigation { it.navigator.blockConfirmed(agentPos, agentState) }
}

/**
 * @agent claude-opus-5-5
 */
internal fun blockChanged(level: ClientLevel, pos: BlockPos) {
    if (level !== Minecraft.getInstance().level) {
        return
    }
    SectionCache.blockChanged(level, pos.immutable())
    val agentPos = pos.toAgent()
    forEachNavigation { it.navigator.blockChanged(agentPos) }
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
    val account = currentAccount()
    val state = accounts.getOrPut(account, ::AccountState)
    state.level?.let(SectionCache::forget)
    state.level = level
    state.lastSentRotation = null
    forEachNavigation { it.navigator.levelChanged() }
    if (level == null) {
        accounts.remove(account)
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun positionCorrected() {
    forEachNavigation { navigation ->
        navigation.body.positionCorrected()
        navigation.navigator.positionCorrected()
    }
}
