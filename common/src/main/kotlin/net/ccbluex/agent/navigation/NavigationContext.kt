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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.BlockHitResult
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
)

/**
 * [host] with [press] held back until [takePressed], and the one way to the host's clicks on blocks: a click goes out
 * only when the rotation the server has hits that face of that block first ([pick]), unless
 * [Options.canClickThroughBlocks]; else it is refused with false.
 *
 * @agent claude-opus-5-5
 */
internal class TickBody(val host: Body, private val options: () -> Options) : Body by host {

    private var pressed: Input? = null

    override fun press(input: Input) {
        pressed = input
    }

    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (options().canClickThroughBlocks) return host.useItemOn(hand, hit)
        return host.useItemOn(hand, pick(hit.blockPos, hit.direction) ?: return false)
    }

    override fun startDestroy(pos: BlockPos, face: Direction): Boolean = sees(pos, face) && host.startDestroy(pos, face)

    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean =
        sees(pos, face) && host.continueDestroy(pos, face)

    private fun sees(pos: BlockPos, face: Direction) = options().canClickThroughBlocks || pick(pos, face) != null

    fun takePressed(): Input? = pressed.also { pressed = null }
}
