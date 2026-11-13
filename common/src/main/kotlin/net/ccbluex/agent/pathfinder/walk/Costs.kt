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

import net.ccbluex.agent.simulation.PlayerMotion
import net.minecraft.world.entity.LivingEntity.BASE_HORIZONTAL_AIR_DRAG
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SlimeBlock

/**
 * Durations in ticks, after Baritone's action costs.
 *
 * From LiquidBounce: [Costs.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Costs.kt#L45-L81)
 *
 * @agent claude-opus-5-5
 */
public object Costs {
    public const val WALK: Double = 20.0 / 4.317
    public const val SPRINT: Double = 20.0 / 5.612
    public const val SWIM: Double = 20.0 / 2.2
    public const val CLIMB_UP: Double = 20.0 / 2.35
    public const val CLIMB_DOWN: Double = 20.0 / 3.0
    public const val SNEAK: Double = 20.0 / 1.295
    public const val SPRINT_SWIM: Double = 20.0 / 3.918
    public const val PLACE: Double = 20.0

    /**
     * On top of walking: a Scaffold bridge crossed at about 3 blocks per second.
     */
    public const val RUN_BRIDGE: Double = 3.0
    public const val BREAK_OVERHEAD: Double = 2.0
    public const val AVOID: Double = 50.0
    public const val JUMP_PENALTY: Double = 20.0
    public const val OPEN: Double = 10.0

    private const val SLIME_BOUNCES = 2
    private const val SLIME_SLOWDOWN = 0.4
    private const val GROUND_ACCELERATION = 0.216

    private val fallTicks = DoubleArray(257).also { ticks ->
        var distance = 0.0
        var velocity = 0.0
        var tick = 0
        for (blocks in ticks.indices) {
            while (distance < blocks) {
                velocity = (velocity + 0.08) * 0.98
                distance += velocity
                tick++
            }
            ticks[blocks] = tick.toDouble()
        }
    }

    public fun fall(blocks: Int): Double = fallTicks[blocks.coerceIn(0, fallTicks.lastIndex)]

    public val JUMP: Double = fall(2) - fall(1)

    private val plainSpeed = groundSpeed(PlayerMotion.PLAIN_FRICTION, slowdown = 1.0)

    /**
     * How many times longer walking over [block] takes than over plain ground. Ice gets as fast, so it is plain.
     */
    public fun floorFactor(block: Block): Double {
        val slowdown = block.speedFactor * if (block is SlimeBlock) SLIME_SLOWDOWN else 1.0
        return if (slowdown < 1.0) plainSpeed / groundSpeed(block.friction, slowdown) else 1.0
    }

    public fun bounce(block: Block, blocks: Int): Double =
        if (block is SlimeBlock) 2 * SLIME_BOUNCES * fall(blocks) else 0.0

    /**
     * Top ground speed, relative to the movement speed, on a floor that scales the speed by [slowdown] every tick.
     */
    private fun groundSpeed(friction: Float, slowdown: Double): Double {
        val drag = (friction * BASE_HORIZONTAL_AIR_DRAG).toDouble()
        return GROUND_ACCELERATION / (drag * drag * drag) / (1.0 - slowdown * drag)
    }
}
