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

package net.ccbluex.agent.world

/**
 * The movement keys of a tick.
 *
 * @agent claude-opus-5-5
 */
public class Input(
    public val forward: Boolean,
    public val backward: Boolean,
    public val left: Boolean,
    public val right: Boolean,
    public val jump: Boolean,
    public val shift: Boolean,
    public val sprint: Boolean,
) {
    override fun equals(other: Any?): Boolean = this === other || other is Input && other.forward == forward &&
        other.backward == backward && other.left == left && other.right == right && other.jump == jump &&
        other.shift == shift && other.sprint == sprint

    override fun hashCode(): Int = listOf(forward, backward, left, right, jump, shift, sprint)
        .fold(0) { hash, key -> hash * 2 + if (key) 1 else 0 }

    override fun toString(): String =
        "Input[forward=$forward, backward=$backward, left=$left, right=$right, jump=$jump, shift=$shift, " +
            "sprint=$sprint]"

    public companion object {
        @JvmField
        public val EMPTY: Input = Input(false, false, false, false, false, false, false)
    }
}

/**
 * @agent claude-opus-5-5
 */
public enum class InteractionHand {
    MAIN_HAND,
    OFF_HAND,
}
