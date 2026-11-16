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
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.Player
import org.slf4j.LoggerFactory
import net.minecraft.world.entity.player.Player as McPlayer

/**
 * A [Body] over a [handle] the test moves itself: it keeps the keys pressed and the rotation looked for last.
 *
 * @agent claude-opus-5-5
 */
internal class TestBody(
    val handle: McPlayer,
    override val flight: FlightControl? = null,
    override val teleporter: Teleporter? = null,
) : Body {

    override val player: Player = handle.toAgent()

    var pressed: Input = Input.EMPTY
        private set

    var looked: ViewRotation? = null
        private set

    override val sentRotation: ViewRotation
        get() = ViewRotation(player.yRot, player.xRot)
    override var safeWalk = false
    override val latencyMillis = 0
    override val traits = BodyTraits(exactSimulation = false)

    override fun press(input: Input, exact: Boolean) {
        pressed = input
    }

    override fun look(rotation: ViewRotation, purpose: LookPurpose) {
        looked = rotation
    }

    override fun selectHotbarSlot(slot: Int) = Unit
    override fun releaseHotbarSlot() = Unit
    override fun startDestroy(pos: BlockPos, face: Direction) = false
    override fun continueDestroy(pos: BlockPos, face: Direction) = false
    override fun stopDestroy() = Unit
    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult) = false
    override fun useItem(hand: InteractionHand) = false

    override fun simulate(input: SimulatedInput, yRot: Float): PlayerSimulation.Steered =
        SimulatedPlayer.fromPlayer(handle, input, traits.navigatesInMovementTick, simulationHooks)
            .also { it.yRot = yRot }

    /**
     * What the parts of a navigator over this body share, with default options.
     */
    fun context(): NavigationContext =
        NavigationContext(this, RouteReport(Listener.NONE), { Options() }, Listener.NONE, LoggerFactory.getLogger("Test"))
}
