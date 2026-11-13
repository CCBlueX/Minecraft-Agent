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

import net.minecraft.world.entity.player.Input

/**
 * The keys a walk sets over one tick, which the navigator presses at its end.
 *
 * @agent claude-opus-5-5
 */
internal class MovementKeys {

    /**
     * Only the four movement keys are read.
     */
    var directionalInput: Input = Input.EMPTY
    var jump = false
    var sneak = false

    fun toInput(sprint: Boolean): Input = directionalInput.withKeys(jump, sneak, sprint)
}

/**
 * Whether any of the four movement keys is held.
 */
internal val Input.isMoving: Boolean
    get() = forward() || backward() || left() || right()

/**
 * The four movement keys of this with [jump], [sneak] and [sprint].
 */
internal fun Input.withKeys(jump: Boolean, sneak: Boolean, sprint: Boolean): Input =
    Input(forward(), backward(), left(), right(), jump, sneak, sprint)
