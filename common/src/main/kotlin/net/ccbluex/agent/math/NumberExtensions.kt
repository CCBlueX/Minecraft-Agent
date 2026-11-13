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

package net.ccbluex.agent.math

import net.ccbluex.agent.InternalAgentApi
import net.minecraft.util.Mth

// Angle conversions go through Mth's float constants, so results match the game's own conversions bit for bit.

/**
 * From LiquidBounce: [MathExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MathExtensions.kt#L29-L29)
 */
internal fun Float.toRadians(): Float = this * Mth.DEG_TO_RAD

/**
 * From LiquidBounce: [MathExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MathExtensions.kt#L31-L31)
 */
internal fun Float.toDegrees(): Float = this * Mth.RAD_TO_DEG

/**
 * From LiquidBounce: [MathExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MathExtensions.kt#L32-L32)
 */
public fun Double.toDegrees(): Double = this * Mth.RAD_TO_DEG

/**
 * Mth.sin, the game's lookup table sine.
 *
 * From LiquidBounce: [MathExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MathExtensions.kt#L39-L39)
 *
 * @agent claude-opus-5-5
 */
internal fun Float.fastSin(): Float = Mth.sin(toDouble())

/**
 * Mth.cos, the game's lookup table cosine.
 *
 * From LiquidBounce: [MathExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MathExtensions.kt#L41-L41)
 *
 * @agent claude-opus-5-5
 */
internal fun Float.fastCos(): Float = Mth.cos(toDouble())

/**
 * This, or [fallback] when it is NaN.
 *
 * From LiquidBounce: [NumberExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/NumberExtensions.kt#L43-L43)
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public inline fun Double.ifNaN(fallback: () -> Double): Double = if (isNaN()) fallback() else this

/**
 * This, or [fallback] when it is NaN.
 *
 * From LiquidBounce: [NumberExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/NumberExtensions.kt#L45-L45)
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public inline fun Float.ifNaN(fallback: () -> Float): Float = if (isNaN()) fallback() else this
