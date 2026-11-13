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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.walk.AssistedJump.Landing
import net.ccbluex.agent.pathfinder.walk.JumpTargets
import net.ccbluex.agent.pathfinder.walk.Jumps
import net.ccbluex.agent.simulation.PlayerMotion
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * The [best] jump [find] came across, with its score and intent, and the blocks out of reach from here.
 *
 * From LiquidBounce: [AssistedJump.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/AssistedJump.kt#L300-L341), [AssistedJump.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/AssistedJump.kt#L415-L460)
 *
 * @agent claude-opus-5-5
 */
internal class Landings {
    var best: Landing? = null
    var bestScore = Double.MAX_VALUE
    var bestIntent = Double.MAX_VALUE
    val missed = ArrayList<Missed>()

    /**
     * A block clearly headed for more than [best] and out of reach from here, jumped to from its [runUp].
     */
    fun runUp(runUp: (Missed) -> Landing?): Landing? {
        missed.sortBy(Missed::intent)
        for (candidate in missed.take(MAX_RUN_UPS)) {
            if (candidate.intent > bestIntent - INTENT_SLACK) {
                break
            }
            runUp(candidate)?.let { return it }
        }
        return null
    }

    class Missed(val pos: BlockPos, val top: Double, val intent: Double)

    companion object {
        private const val MAX_DROP = 4
        private const val MAX_BOUNCE_DROP = 12

        /**
         * How far a fall onto slime has to be for the bounce to get higher than a jump.
         */
        private const val BOOST_DROP = 1.0
        private const val DROP_PENALTY = 0.5

        /**
         * How much more a block out of reach from here has to be headed for to be run up to instead.
         */
        private const val INTENT_SLACK = 0.5
        private const val MAX_RUN_UPS = 3

        /**
         * The jumps from [feet], standing at [height], to the blocks within [maxAngle] of [heading].
         */
        fun find(
            targets: JumpTargets,
            jumps: Jumps,
            state: PlayerMotion,
            yRot: Float,
            heading: Float,
            maxAngle: Float,
            feet: BlockPos,
            height: Double,
        ): Landings {
            val terrain = targets.terrain
            val position = state.position
            val found = Landings()
            targets.forEach(
                feet.x, feet.y, feet.z, height, MAX_DROP, MAX_BOUNCE_DROP, state.deltaY, around = true,
            ) { x, y, z, top ->
                val angle = angle(position, Vec3(x + 0.5, top, z + 0.5), heading)
                if (angle > maxAngle) {
                    return@forEach true
                }

                // Below a course is where a missed jump ends, so any block level with it or higher comes first. Slime
                // and beds bounce back up.
                val restitution = terrain.restitution(x, top, z)
                val bounces = restitution > 0.0
                val drop = maxOf(0.0, height - top)
                val intent = angle / maxAngle + if (bounces) 0.0 else drop * DROP_PENALTY
                val pos = BlockPos(x, y, z)
                val jump = jumps.fromHere(state, yRot, pos, top)
                if (jump == null) {
                    found.missed += Missed(pos, top, intent)
                } else if (intent - jump.margin < found.bestScore) {
                    // The bounce gets as high as the fall times the restitution squared.
                    val takesOff = bounces && drop * restitution * restitution >= BOOST_DROP
                    found.best = Landing(pos, top, jump, bounces = bounces, takesOff = takesOff)
                    found.bestScore = intent - jump.margin
                    found.bestIntent = intent
                }
                true
            }
            return found
        }
    }
}

/**
 * How far the way from [from] to [to] turns away from [heading], in degrees.
 *
 * From LiquidBounce: [AssistedJump.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/AssistedJump.kt#L465-L465)
 *
 * @agent claude-opus-5-5
 */
internal fun angle(from: Vec3, to: Vec3, heading: Float) = abs(Mth.wrapDegrees(to.subtract(from).yRot - heading))
