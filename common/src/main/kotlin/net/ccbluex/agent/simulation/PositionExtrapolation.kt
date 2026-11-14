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

import net.ccbluex.agent.math.fma
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.round

/**
 * Where something is in a number of ticks, which may be a fraction.
 *
 * From LiquidBounce: [PositionExtrapolation.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PositionExtrapolation.kt#L33-L70)
 *
 * @agent claude-opus-5-5
 */
public fun interface PositionExtrapolation {

    public fun getPositionInTicks(ticks: Double): Vec3

    public companion object {
        /**
         * Further ahead a player's forecast stays at this tick.
         */
        private const val MAX_FORECAST_TICKS = 30.0

        /**
         * A player by its forecast from [forecasts], anything else by [linear].
         */
        @JvmStatic
        public fun of(target: Entity, forecasts: Forecasts): PositionExtrapolation =
            if (target is Player) of(forecasts.of(target)) else linear(target)

        /**
         * The feet of [forecast] at the nearest whole tick, from 0 up to [MAX_FORECAST_TICKS].
         */
        @JvmStatic
        public fun of(forecast: Forecast): PositionExtrapolation = PositionExtrapolation { ticks ->
            forecast.getSnapshotAt(round(ticks.coerceAtMost(MAX_FORECAST_TICKS)).toInt().coerceAtLeast(0)).pos
        }

        /**
         * [entity] keeps moving as far per tick as in its last one.
         */
        @JvmStatic
        public fun linear(entity: Entity): PositionExtrapolation =
            linear(entity.position(), entity.position().subtract(entity.oldPosition()))

        @JvmStatic
        public fun linear(position: Vec3, deltaMovement: Vec3): PositionExtrapolation =
            PositionExtrapolation { ticks -> position.fma(ticks, deltaMovement) }

        @JvmStatic
        public fun constant(position: Vec3): PositionExtrapolation = PositionExtrapolation { position }
    }
}
