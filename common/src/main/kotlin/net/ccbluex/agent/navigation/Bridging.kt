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

import net.ccbluex.agent.navigation.walk.Bridge

/**
 * How a player crosses the bridges a walk places: the methods Java players use, each a set of choices the crossing
 * makes every tick, or [AUTO] for one picked per bridge. Blocks are counted from 0 along the bridge.
 *
 * @agent claude-opus-5-5
 */
public enum class Bridging(
    /**
     * Degrees below the horizon the view holds while crossing.
     */
    internal val xRot: Float,
) {

    /**
     * A method picked per bridge: one-stack up rising bridges, Swift Sneak in Swift Sneak III leggings, and Ninja
     * unless [Options.riskyBridges] allows Telly, Godbridge, Moonwalk and Breezily.
     */
    AUTO(xRot = 80f),

    /**
     * Backwards, sneaking only on reaching the edge, a click, and on across the new block without sneaking. On a
     * staircase bridge the steps alternate S + A and S + D, which is diagonal Ninja.
     */
    NINJA(xRot = 80f),

    /**
     * Diagonally backwards without sneaking, one click per block as the player passes the edge, and a hop in place
     * every 7 or 8 blocks as tick drift builds up; straight bridges only.
     */
    GODBRIDGE(xRot = 75f) {
        override val yRotOffset: Float
            get() = DIAGONAL

        override val clicksPastEdge: Boolean
            get() = true

        override val hopsOnReset: Boolean
            get() = true

        override fun resets(block: Int): Boolean = block > 0 && block % JUMP_CYCLE in JUMP_BLOCKS

        override fun canCross(bridge: Bridge): Boolean = bridge.isStraight && !bridge.rises
    },

    /**
     * Straight backwards without sneaking, tapping the side key towards the turn after the bridge once in the middle
     * of every block, which drifts the player that way; a sneak reset every 12 blocks; straight bridges only.
     */
    MOONWALK(xRot = 79f) {
        override val clicksPastEdge: Boolean
            get() = true

        override fun resets(block: Int): Boolean = block > 0 && block % SNEAK_RESET == 0

        override fun tap(block: Int, along: Double, turn: Int): Int = when {
            along !in MID_BLOCK -> 0
            turn == 0 -> 1
            else -> turn
        }

        override fun canCross(bridge: Bridge): Boolean = bridge.isStraight && !bridge.rises
    },

    /**
     * Straight backwards without sneaking, tapping the side keys in turn in the middle of every block, never at an
     * edge; a sneak reset every 12 blocks; straight bridges only.
     */
    BREEZILY(xRot = 79f) {
        override val clicksPastEdge: Boolean
            get() = true

        override fun resets(block: Int): Boolean = block > 0 && block % SNEAK_RESET == 0

        override fun tap(block: Int, along: Double, turn: Int): Int = when {
            along !in MID_BLOCK -> 0
            block % 2 == 0 -> -1
            else -> 1
        }

        override fun canCross(bridge: Bridge): Boolean = bridge.isStraight && !bridge.rises
    },

    /**
     * Up a rising bridge: a jump from the edge, the block under the landing placed at the top of the jump and a
     * sneak before landing; level parts of the bridge as Ninja, which is flatstacking. Rising bridges only.
     */
    ONE_STACK(xRot = 80f) {
        override fun canCross(bridge: Bridge): Boolean = bridge.rises
    },

    /**
     * Sneaking the whole way, diagonally on a straight bridge, which Swift Sneak III makes about as fast as walking.
     */
    SWIFT_SNEAK(xRot = 80f) {
        override val yRotOffset: Float
            get() = DIAGONAL

        override val sneaksThroughout: Boolean
            get() = true
    },

    /**
     * Sprinting forwards and jumping off the end of the floor, a turn round in the air to place the floor under and
     * behind the flight, and forwards again before landing; long straight level bridges only.
     */
    TELLY(xRot = 75f) {
        override val clicksPastEdge: Boolean
            get() = true

        override fun canCross(bridge: Bridge): Boolean =
            bridge.isStraight && !bridge.rises && bridge.length >= TELLY_LENGTH
    };

    /**
     * Degrees the view turns off straight back on a straight bridge, so S and a side key walk it.
     */
    internal open val yRotOffset: Float
        get() = 0f

    /**
     * Whether the player walks on past the edge and clicks before it drops, instead of sneaking there.
     */
    internal open val clicksPastEdge: Boolean
        get() = false

    internal open val sneaksThroughout: Boolean
        get() = false

    /**
     * Whether a reset ends with a hop onto the block just placed.
     */
    internal open val hopsOnReset: Boolean
        get() = false

    /**
     * Whether [block] is crossed as a reset of the timing: stopped at its edge by a sneak, as in Ninja.
     */
    internal open fun resets(block: Int): Boolean = false

    /**
     * The side key tapped on [block] [along] the way across it (0 at its back edge, 1 at the front), with the path
     * turning [turn] after the bridge: -1 to the left of the way the bridge goes, 1 to the right, 0 none.
     */
    internal open fun tap(block: Int, along: Double, turn: Int): Int = 0

    internal open fun canCross(bridge: Bridge): Boolean = true

    /**
     * The method crossing [bridge]: this one if it can, else Ninja, and for [AUTO] one by the shape of the bridge.
     * Moving fast or not, Ninja crosses staircases and short bridges unless [risky] methods are allowed; then a
     * player who [canSprint] tellies along long straight ones, else godbridges, Moonwalk drifts towards a turn after
     * the bridge, and Breezily keeps to the middle of the rest.
     */
    internal fun pick(bridge: Bridge, risky: Boolean, sneakingSpeed: Double, canSprint: Boolean): Bridging = when {
        this != AUTO -> takeIf { it.canCross(bridge) } ?: NINJA
        bridge.rises -> ONE_STACK
        sneakingSpeed >= SWIFT_SNEAKING -> SWIFT_SNEAK
        !risky || !bridge.isStraight || bridge.length <= SHORT_BRIDGE -> NINJA
        canSprint && TELLY.canCross(bridge) -> TELLY
        bridge.length >= GODBRIDGE_LENGTH -> GODBRIDGE
        bridge.turnAfter != 0 -> MOONWALK
        else -> BREEZILY
    }

    internal companion object {
        private const val DIAGONAL = 45f

        /**
         * Jump resets after blocks 7 and 15 of every 15, so every 7 or 8 blocks.
         */
        private const val JUMP_CYCLE = 15
        private val JUMP_BLOCKS = setOf(0, 7)
        const val SNEAK_RESET = 12

        /**
         * The middle of a block, where side keys are tapped; never at its edges.
         */
        private val MID_BLOCK = 0.35..0.6

        /**
         * Longest bridge still Ninja bridged when risky methods are allowed: not worth a run-up of a faster method.
         */
        const val SHORT_BRIDGE = 3

        /**
         * Shortest bridge worth godbridging; shorter straight ones are moonwalked, as players do under 15 blocks.
         */
        const val GODBRIDGE_LENGTH = 15

        /**
         * Shortest bridge worth a telly; across shorter gaps the jump lands before a block is placed.
         */
        const val TELLY_LENGTH = 10

        /**
         * Sneaking speed, as a share of walking, from which sneaking the whole way beats Ninja: Swift Sneak III.
         */
        const val SWIFT_SNEAKING = 0.7
    }
}
