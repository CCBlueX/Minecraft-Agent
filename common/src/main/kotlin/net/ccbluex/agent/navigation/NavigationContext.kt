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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import org.slf4j.Logger

/**
 * What the parts of a [Navigator] share. [body] holds back the keys pressed in a tick, so the navigator adds
 * sprinting before the host gets them.
 *
 * @agent claude-opus-5-5
 */
internal class NavigationContext(
    val body: Body,
    val route: RouteReport,
    val options: () -> Options,
    val listener: Listener,
    val logger: Logger,
) {

    /**
     * Whether the keys of the last tick moved the player, or it sprints.
     */
    var isMoving = false

    /**
     * Set by a part waiting for [canClickInventory]: the navigator presses no key this tick.
     */
    var shouldStand = false

    /**
     * Whether the player stands still enough for a click in its inventory; moving or sprinting at the click is what
     * anticheats flag, as a player has to open the inventory, which stops them.
     */
    val canClickInventory: Boolean
        get() = !isMoving && !body.player.isSprinting
}

/**
 * [host] with [press] held back until [takePressed], and the one way to the host's clicks on blocks: a click goes out
 * only when the rotation the server has hits that face of that block first ([pick]), unless
 * [Options.canClickThroughBlocks]; else it is refused with false.
 *
 * @agent claude-opus-5-5
 */
internal class TickBody(val host: Body, private val options: () -> Options) : Body by host {

    private var pressed: Input? = null

    /**
     * Whether the keys held back are exact, see [Body.press].
     */
    var pressedExact: Boolean = false
        private set

    override fun press(input: Input, exact: Boolean) {
        pressed = input
        pressedExact = exact
    }

    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (options().canClickThroughBlocks) return host.useItemOn(hand, hit)
        return host.useItemOn(hand, pick(hit.blockPos, hit.direction) ?: return false)
    }

    override fun startDestroy(pos: BlockPos, face: Direction): Boolean =
        canClick(pos, face) && host.startDestroy(pos, face)

    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean {
        if (canClick(pos, face)) return host.continueDestroy(pos, face)
        // The game's own dig ends too once the crosshair leaves the block
        host.stopDestroy()
        return false
    }

    fun canClick(pos: BlockPos, face: Direction): Boolean =
        options().canClickThroughBlocks || pick(pos, face) != null

    fun takePressed(): Input? = pressed.also { pressed = null }
}
