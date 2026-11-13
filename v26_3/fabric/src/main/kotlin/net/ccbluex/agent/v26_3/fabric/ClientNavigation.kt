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

package net.ccbluex.agent.v26_3.fabric

import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.navigation.Options
import net.minecraft.world.phys.Vec3

/**
 * Runs a [Navigator] for the local player: every player tick, right after the input read the keys and before the
 * player moves, it walks to the goal of a [DestinationSource] or to a fixed goal, until [stop]. Call it on the client thread.
 *
 * Block updates, setbacks and level changes reach the navigator while it runs.
 *
 * @agent claude-opus-5-5
 */
public class ClientNavigation @JvmOverloads constructor(
    public val body: ClientBody = ClientBody(),
    options: Options = Options(),
    listener: Listener = Listener.NONE,
) {

    public val navigator: Navigator = Navigator(body, options, listener)

    /**
     * Read every tick; a new copy takes effect at once.
     */
    public var options: Options by navigator::options

    private var source: DestinationSource? = null

    public val isRunning: Boolean
        get() = source != null

    /**
     * Walks to [goal] and stops once the player, or what it rides, is within [radius] of it.
     */
    @JvmOverloads
    public fun moveTo(goal: Vec3, radius: Double = DestinationSource.DEFAULT_RADIUS) {
        run(FixedGoal(goal, radius))
    }

    /**
     * Walks to the goal [source] gives every tick, standing still while it gives none, until [stop].
     */
    public fun follow(source: DestinationSource) {
        run(source)
    }

    public fun stop() {
        if (source == null) {
            return
        }
        source = null
        runningNavigations.remove(this)
        navigator.stop()
        body.release()
    }

    private fun run(source: DestinationSource) {
        this.source = source
        runningNavigations.addIfAbsent(this)
    }

    internal fun tick() {
        val source = source ?: return
        val goal = source.goal()
        when {
            this.source == null -> Unit
            goal == null -> navigator.halt()
            else -> navigator.navigate(goal, source.radius())
        }
    }

    /**
     * Arrived once the root vehicle is within [radius], which ends the run.
     */
    private inner class FixedGoal(private val goal: Vec3, private val radius: Double) : DestinationSource {

        override fun goal(): Vec3? {
            if (body.player.rootVehicle.position().distanceTo(goal) > radius) {
                return goal
            }
            stop()
            return null
        }

        override fun radius(): Double = radius
    }

}

/**
 * Where a [ClientNavigation] walks, asked once per tick.
 *
 * @agent claude-opus-5-5
 */
public fun interface DestinationSource {

    /**
     * The goal of this tick, or null to stand still.
     */
    public fun goal(): Vec3?

    /**
     * How close to the goal is close enough, in blocks.
     */
    public fun radius(): Double = DEFAULT_RADIUS

    public companion object {
        public const val DEFAULT_RADIUS: Double = 1.0
    }

}
