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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.world.entity.Attributes
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs

/**
 * How fast the player really walks on the ground against vanilla: the push the keys gave it a tick, the move it made
 * beyond the motion it carried, over the push vanilla gives at its movement speed. A speed feature of the host or an
 * effect that started after the route was searched shows in it, and the jumps and run-ups are timed with it.
 *
 * @agent claude-opus-5-5
 */
internal class Stride {

    /**
     * The measured push over vanilla's; 1 until measured.
     */
    var factor = 1.0
        private set

    /**
     * The move the player made in the last tick, zero when that tick was not measured.
     */
    var moved: Vec3 = Vec3.ZERO
        private set

    private var lastPosition: Vec3? = null
    private var carried = Vec3.ZERO
    private var wasOnGround = false
    private var lastTick = 0

    /**
     * Measures the move [player] made since the last call; once a tick, before its movement.
     */
    fun tick(player: Player) {
        val position = player.position()
        val last = lastPosition
        moved = if (last != null && player.tickCount == lastTick + 1) position.subtract(last) else Vec3.ZERO
        if (last != null && player.tickCount == lastTick + 1 && wasOnGround && walksPlainly(player)) {
            val pushed = Mth.length(position.x - last.x - carried.x, position.z - last.z - carried.z)
            val vanilla = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * INPUT_SCALE
            if (vanilla > 0.0 && pushed > vanilla * MIN_SHARE) {
                factor = (factor + (pushed / vanilla - factor) * WEIGHT).coerceIn(MIN_FACTOR, MAX_FACTOR)
            }
        }
        lastPosition = position
        carried = player.deltaMovement
        wasOnGround = player.onGround()
        lastTick = player.tickCount
    }

    /**
     * [searched], the abilities a route was searched with, with [player] walking as fast as measured; [searched]
     * itself while that is within [SAME_PACE] of the walk it was searched for.
     */
    fun abilities(searched: WalkAbilities, player: Player): WalkAbilities {
        val now = MotionTraits.capture(player)
        val walkingSpeed = (now.walkingSpeed * factor).toFloat()
        val motion = searched.motion
        if (abs(walkingSpeed / motion.walkingSpeed - 1f) < SAME_PACE && now.jumpPower == motion.jumpPower) {
            return searched
        }
        return searched.withMotion(MotionTraits(walkingSpeed, now.jumpStrength, now.jumpBoostPower, now.mechanics))
    }

    /**
     * Whether the move just made was a plain walk on ground of normal friction, which vanilla pushes at the
     * movement speed.
     */
    private fun walksPlainly(player: Player): Boolean {
        if (!player.onGround() || player.horizontalCollision || player.isInWater || player.onClimbable()) {
            return false
        }
        if (player.isCrouching || player.isUsingItem) {
            return false
        }
        val below = player.level().getBlockState(player.blockPosBelowThatAffectsMyMovement)
        return below.block.friction == PLAIN_FRICTION
    }

    private companion object {
        const val INPUT_SCALE = 0.98
        const val PLAIN_FRICTION = 0.6f

        /**
         * Share of vanilla's push below which the keys were mostly released.
         */
        const val MIN_SHARE = 0.25
        const val MIN_FACTOR = 0.25
        const val MAX_FACTOR = 4.0
        const val WEIGHT = 0.5

        /**
         * Share by which the measured walk may differ from the searched one and still count as the same.
         */
        const val SAME_PACE = 0.05f
    }
}
