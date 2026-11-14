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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

/**
 * A client makes its navigators while it loads, before there is a player, and keeps them across worlds.
 *
 * @agent claude-opus-5-5
 */
internal class NavigatorTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `a navigator is made without a player`() {
        val navigator = Navigator(NoPlayerBody)
        assertFalse(navigator.isActing)
    }

    /**
     * @agent claude-opus-5-5
     */
    private object NoPlayerBody : Body {
        override val player: Player
            get() = error("There is no player")
        override val sentRotation = ViewRotation(0f, 0f)
        override var safeWalk = false
        override val latencyMillis = 0
        override val traits = BodyTraits()
        override val flight: FlightControl? = null
        override val teleporter: Teleporter? = null

        override fun press(input: Input, exact: Boolean) = Unit
        override fun look(rotation: ViewRotation, purpose: LookPurpose) = Unit
        override fun selectHotbarSlot(slot: Int) = Unit
        override fun releaseHotbarSlot() = Unit
        override fun startDestroy(pos: BlockPos, face: Direction) = false
        override fun continueDestroy(pos: BlockPos, face: Direction) = false
        override fun stopDestroy() = Unit
        override fun useItemOn(hand: InteractionHand, hit: BlockHitResult) = false
        override fun useItem(hand: InteractionHand) = false
    }
}
