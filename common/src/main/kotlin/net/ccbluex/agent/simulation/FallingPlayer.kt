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

package net.ccbluex.agent.simulation

import net.ccbluex.agent.math.copy
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull

/**
 * Follows [player] holding [input], without jumping, until it lands. It starts with [motion] and moves facing
 * [yRot]; [continuesTick] and [hooks] are as for [SimulatedPlayer.fromPlayer].
 *
 * From LiquidBounce: [FallingPlayer.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/FallingPlayer.kt#L28-L149)
 *
 * @agent claude-opus-5-5
 */
public class FallingPlayer @JvmOverloads constructor(
    private val player: Player,
    private val input: SimulatedInput,
    private val continuesTick: Boolean,
    private val hooks: SimulationHooks = SimulationHooks.VANILLA,
    private val motion: Vec3 = player.deltaMovement,
    private val yRot: Float = player.yRot,
) {

    private fun simulation(descending: Boolean): SimulatedPlayer {
        val keys = input.keys.copy(jump = false, sneak = descending || input.keys.shift)
        val held = SimulatedInput(keys, input.forceStayOnGroundSurface, input.ignoresEdgeBackOff)
        return SimulatedPlayer.fromPlayer(player, held, continuesTick, hooks).apply {
            deltaMovement = motion
            yRot = this@FallingPlayer.yRot
        }
    }

    /**
     * The landing within [ticks], or null without one.
     */
    public fun findCollision(ticks: Int): Landing? {
        val simulation = simulation(descending = false)

        for (tick in 0 until ticks) {
            val positionBeforeMovement = simulation.position()
            simulation.tick()

            if (simulation.onGround()) {
                return Landing(simulation.mainSupportingBlockPos.getOrNull(), tick, positionBeforeMovement)
            }
        }

        return null
    }

    /**
     * Whether one of the next [ticks] movement ticks starts with the feet in [targetPos] before landing, sneaking
     * down with [forceDescending].
     */
    @JvmOverloads
    public fun canReachBeforeLanding(
        targetPos: BlockPos,
        ticks: Int,
        forceDescending: Boolean = false,
    ): Boolean {
        val simulation = simulation(forceDescending)

        repeat(ticks) {
            if (simulation.blockPosition() == targetPos) {
                return true
            }

            simulation.tick()
            if (simulation.onGround()) {
                return false
            }
        }

        return false
    }

    /**
     * Where the coming ticks take the player, one position per tick, up to the tick it has fallen further than
     * [distance]. Null if it is safe on the ground first, or has not fallen that far after [ticks].
     */
    public fun fallPath(distance: Float, ticks: Int): List<Vec3>? {
        val simulation = simulation(descending = false)
        val keys = input.keys
        val steered = keys.forward || keys.backward || keys.left || keys.right
        val positions = ArrayList<Vec3>()

        repeat(ticks) {
            simulation.tick()
            positions += simulation.position()

            if (simulation.fallDistance > distance) {
                return positions
            }

            // Landing is not the end: what is left of the momentum still carries a player that does not walk
            // itself over an edge
            if (simulation.onGround() && (steered || simulation.deltaMovement.horizontalDistanceSqr() == 0.0)) {
                return null
            }
        }

        return null
    }

    /**
     * Landing in movement tick [tick] (0 is the next one) on [pos], the block under the feet; [positionBeforeMovement]
     * is where the player is before that tick moves it, where it uses an item in that tick.
     *
     * @agent claude-opus-5-5
     */
    public data class Landing(
        public val pos: BlockPos?,
        public val tick: Int,
        public val positionBeforeMovement: Vec3,
    )
}
