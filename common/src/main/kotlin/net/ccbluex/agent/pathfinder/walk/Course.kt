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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_HORIZONTAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.BASE_VERTICAL_AIR_DRAG
import net.ccbluex.agent.world.entity.LivingEntity.Companion.DEFAULT_BASE_GRAVITY

/**
 * The jump [Jumps] works out now, from the start to the target block: where it runs, where it aims and what the
 * ground on the way allows. Not thread-safe.
 *
 * From LiquidBounce: [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L278-L367), [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L616-L728), [Jumps.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Jumps.kt#L505-L556)
 *
 * @agent claude-opus-5-5
 */
internal class Course(private val terrain: Terrain, private val abilities: WalkAbilities) {

    var fromX = 0.0
        private set
    var fromY = 0.0
        private set
    var fromZ = 0.0
        private set
    var target: BlockPos = BlockPos.ZERO
        private set
    var targetX = 0.0
        private set
    var targetZ = 0.0
        private set
    var targetHeight = 0.0
        private set

    /**
     * Whether the target is a ladder or vine the jump catches rather than lands on.
     */
    var catches = false
        private set
    var dirX = 0.0
        private set
    var dirZ = 0.0
        private set
    var distance = 0.0
        private set
    var aimX = 0.0
    var aimZ = 0.0

    /**
     * How far ahead of the middle of the block jumped from its floor reaches, which hops must land short of.
     */
    var edge = Double.MAX_VALUE

    /**
     * Whether a run or flight simulated since [set] ran into something short of the target.
     */
    var obstructed = false

    fun set(fromX: Double, fromY: Double, fromZ: Double, to: BlockPos, toHeight: Double) {
        this.fromX = fromX
        this.fromY = fromY
        this.fromZ = fromZ
        target = to
        targetX = to.x + 0.5
        targetZ = to.z + 0.5
        targetHeight = toHeight
        catches = terrain.isClimbable(to.x, to.y, to.z)
        aimX = targetX
        aimZ = targetZ
        edge = Double.MAX_VALUE
        obstructed = false
        dirX = targetX - fromX
        dirZ = targetZ - fromZ
        distance = Mth.length(dirX, dirZ)
        if (distance > 1.0E-6) {
            dirX /= distance
            dirZ /= distance
        }
    }

    /**
     * Aims the run [offset] blocks to the left of the middle of the target, to get around something in the way.
     */
    fun aimBeside(offset: Double) {
        aimX = targetX - dirZ * offset
        aimZ = targetZ + dirX * offset
    }

    /**
     * The spot [distance] blocks behind where the jump starts, away from the target; ahead of it when negative.
     */
    fun behind(distance: Double) = Vec3(fromX - dirX * distance, fromY, fromZ - dirZ * distance)

    /**
     * Whether the body fits all along the line from [runUp] to the aim, at least [JUMP_CLEARANCE] up from the line to
     * the target height: what the jump has to clear on any arc.
     */
    fun isWayClear(runUp: Vec3): Boolean {
        val length = Mth.length(aimX - runUp.x, aimZ - runUp.z)
        val samples = Mth.ceil(length / WAY_STEP)
        for (sample in 1 until samples) {
            val along = sample.toDouble() / samples
            val y = Mth.lerp(along, fromY, targetHeight) + JUMP_CLEARANCE
            val box = terrain.box(Mth.lerp(along, runUp.x, aimX), y, Mth.lerp(along, runUp.z, aimZ))
            if (!terrain.isClear(box.setMaxY(box.maxY - JUMP_CLEARANCE))) {
                return false
            }
        }
        return true
    }

    fun runUpLength(max: Double): Double {
        var length = 0.0
        while (length + RUN_UP_STEP <= max && canStandAt(behind(length + RUN_UP_STEP))) {
            length += RUN_UP_STEP
        }
        return length
    }

    fun canStandAt(feet: Vec3) = terrain.isSupported(feet) && terrain.isClear(terrain.box(feet))

    fun edgeLength(): Double {
        var length = 0.0
        while (length + RUN_UP_STEP <= MAX_EDGE && terrain.isSupported(behind(-(length + RUN_UP_STEP)))) {
            length += RUN_UP_STEP
        }
        return length
    }

    /**
     * Margin of the landing [motion], just off the ground, makes with nothing in the way; NaN when it misses.
     */
    @Suppress("CognitiveComplexMethod")
    fun estimate(motion: PlayerMotion): Double {
        var x = motion.x
        var y = motion.y
        var z = motion.z
        var deltaX = motion.deltaX
        var deltaY = motion.deltaY
        var deltaZ = motion.deltaZ
        val acceleration = if (motion.isSprinting) SPRINT_AIR_ACCELERATION else AIR_ACCELERATION

        repeat(MAX_AIR_TICKS) {
            val toX = targetX - x
            val toZ = targetZ - z
            val length = Mth.length(toX, toZ)
            if (length > 1.0E-4) {
                val speed = (deltaX * toX + deltaZ * toZ) / length
                val push = if (catches || !overshoots(length, speed, y, deltaY)) acceleration else -AIR_ACCELERATION
                deltaX += toX / length * push
                deltaZ += toZ / length * push
            }

            val nextY = y + deltaY
            if (!catches && deltaY < 0.0 && nextY <= targetHeight && y >= targetHeight - LANDING_PROBE) {
                return if (isOver(x, z)) margin(x + deltaX, z + deltaZ) else Double.NaN
            }
            x += deltaX
            y = nextY
            z += deltaZ
            if (catches && isInTarget(x, y, z)) {
                return margin(x, z)
            }
            // Rising against the side, the player slides up it; only sinking into it misses.
            val hitsSide = deltaY <= 0.0 && y < targetHeight && isOver(x, z)
            if (hitsSide || y < targetHeight - MAX_BELOW) {
                return Double.NaN
            }

            deltaX *= BASE_HORIZONTAL_AIR_DRAG
            deltaY = (deltaY - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG
            deltaZ *= BASE_HORIZONTAL_AIR_DRAG
        }
        return Double.NaN
    }

    fun isOver(x: Double, z: Double) =
        x > target.x - PlayerMotion.HALF_WIDTH && x < target.x + 1.0 + PlayerMotion.HALF_WIDTH &&
            z > target.z - PlayerMotion.HALF_WIDTH && z < target.z + 1.0 + PlayerMotion.HALF_WIDTH

    fun isInTarget(x: Double, y: Double, z: Double) =
        Mth.floor(x) == target.x && Mth.floor(y) == target.y && Mth.floor(z) == target.z

    fun margin(x: Double, z: Double) =
        minOf(x - target.x, target.x + 1.0 - x, z - target.z, target.z + 1.0 - z)

    /**
     * Whether pushing on at [speed] carries a player past the target, [remaining] blocks away, before it is down.
     */
    fun overshoots(remaining: Double, speed: Double, height: Double, deltaY: Double): Boolean {
        var forward = speed
        var y = height
        var vertical = deltaY
        var travel = 0.0
        repeat(MAX_AIR_TICKS) {
            forward += SPRINT_AIR_ACCELERATION
            travel += forward
            y += vertical
            if (travel > remaining + BRAKE_SLACK) {
                return true
            }
            if (vertical < 0.0 && y <= targetHeight) {
                return false
            }
            forward *= BASE_HORIZONTAL_AIR_DRAG
            vertical = (vertical - DEFAULT_BASE_GRAVITY) * BASE_VERTICAL_AIR_DRAG
        }
        return false
    }

    /**
     * Whether every column between the start and the target catches a fall the walker survives.
     */
    fun isSafe(): Boolean {
        var travelled = HALF_BLOCK
        while (travelled < distance - HALF_BLOCK) {
            val x = Mth.floor(fromX + dirX * travelled)
            val z = Mth.floor(fromZ + dirZ * travelled)
            if (!catchesFall(x, Mth.floor(fromY), z)) {
                return false
            }
            travelled += SAFETY_STEP
        }
        return true
    }

    private fun catchesFall(x: Int, top: Int, z: Int): Boolean {
        for (y in top downTo maxOf(top - abilities.safeFall - 1, terrain.minY)) {
            val stand = terrain.standHeight(x, y, z)
            when {
                terrain.isWater(x, y, z) -> return true
                !stand.isNaN() -> return fromY - stand <= abilities.safeFall
                !terrain.isPassable(x, y, z) -> return true
            }
        }
        return false
    }

    companion object {
        const val RUN_UP_STEP = 0.1
        const val MAX_AIR_TICKS = 60
        const val MAX_BELOW = 4.0
        const val LANDING_PROBE = 1.0E-4
        const val HALF_BLOCK = 0.5

        private const val MAX_EDGE = 1.5

        /**
         * How far past the middle of the target a landing may go before the air control holds back.
         */
        private const val BRAKE_SLACK = 0.25
        private const val SAFETY_STEP = 0.5

        /**
         * How high above the straight line a jump gets for long enough to pass over what is lower.
         */
        private const val JUMP_CLEARANCE = 0.5
        private const val WAY_STEP = 0.25

        private const val AIR_ACCELERATION = PlayerMotion.AIR_SPEED * PlayerMotion.INPUT_FRICTION
        private const val SPRINT_AIR_ACCELERATION = PlayerMotion.SPRINT_AIR_SPEED * PlayerMotion.INPUT_FRICTION
    }

}
