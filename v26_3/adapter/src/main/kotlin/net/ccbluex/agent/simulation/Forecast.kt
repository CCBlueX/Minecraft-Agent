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

import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.world.phys.Vec3
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The coming ticks of [player], simulated when first asked for and kept, so every reader in a tick shares one
 * simulation. Tick 0 is the player as it was handed over; the forecast ticks [player] itself, nothing else may.
 * Safe to read from several threads.
 *
 * From LiquidBounce: [PlayerSimulationCache.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L139-L233)
 *
 * @agent claude-opus-5-5
 */
public class Forecast(private val player: SimulatedPlayer) {

    private val snapshots = ArrayList<PlayerSnapshot>().apply { add(PlayerSnapshot(player)) }
    private val lock = ReentrantReadWriteLock()

    /**
     * The keys the forecast holds.
     */
    public val input: SimulatedInput
        get() = player.input

    /**
     * Simulates up to [ticks] ticks ahead, unless that was done already.
     */
    public fun simulateUntil(ticks: Int) {
        require(ticks in 0..MAX_TICKS) { "ticks must be within 0..$MAX_TICKS, not $ticks" }
        if (lock.read { snapshots.size } > ticks) {
            return
        }

        lock.write {
            while (snapshots.size <= ticks) {
                player.tick()
                snapshots += PlayerSnapshot(player)
            }
        }
    }

    public fun getSnapshotAt(ticks: Int): PlayerSnapshot {
        simulateUntil(ticks)
        return lock.read { snapshots[ticks] }
    }

    /**
     * Every tick from tick 0 on, endless; each is simulated only when the sequence gets to it.
     */
    public fun simulate(): Sequence<PlayerSnapshot> = generateSequence(0) { it + 1 }.map(::getSnapshotAt)

    public fun getSnapshotsBetween(tickRange: IntRange): List<PlayerSnapshot> {
        if (tickRange.isEmpty()) {
            return emptyList()
        }

        simulateUntil(tickRange.last)
        return lock.read { snapshots.subList(tickRange.first, tickRange.last + 1).toList() }
    }

    /**
     * [getSnapshotsBetween], each tick simulated only when the sequence gets to it.
     */
    public fun simulateBetween(tickRange: IntRange): Sequence<PlayerSnapshot> {
        require(tickRange.isEmpty() || tickRange.last <= MAX_TICKS) { "$tickRange reaches past $MAX_TICKS ticks" }
        return tickRange.asSequence().map(::getSnapshotAt)
    }

    /**
     * A [PlayerSimulation] that steps through this forecast instead of simulating again.
     */
    public fun asSimulation(): PlayerSimulation = object : PlayerSimulation {
        private var ticks = 0

        override val predictedPos: net.ccbluex.agent.math.Vec3
            get() = getSnapshotAt(ticks).pos.toAgent()

        override fun tick() {
            ticks++
        }
    }

    private companion object {
        /**
         * A minute: further ahead, a forecast is never right.
         */
        const val MAX_TICKS = 60 * 20
    }
}
