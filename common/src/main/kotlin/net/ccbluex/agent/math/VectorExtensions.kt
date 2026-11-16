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
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.Vec3i
import java.util.Random
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The yaw a player faces when looking along this vector, from -180 to 180.
 *
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L172-L173)
 */
public val Vec3.yRot: Float
    get() = atan2(-x, z).toFloat().toDegrees()

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L175-L175)
 */
public fun Vec3.copy(x: Double = this.x, y: Double = this.y, z: Double = this.z): Vec3 = Vec3(x, y, z)

/**
 * This plus [scale] times [other], each axis rounded once.
 *
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L121-L125)
 */
public fun Vec3.fma(scale: Double, other: Vec3): Vec3 =
    Vec3(Math.fma(scale, other.x, x), Math.fma(scale, other.y, y), Math.fma(scale, other.z, z))

/**
 * This vector scaled to [newLength]; a vector too short to have a direction stays zero.
 *
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L145-L148)
 *
 * @agent claude-opus-5-5
 */
public fun Vec3.withLength(newLength: Double): Vec3 {
    val squared = lengthSqr()
    if (Mth.equal(squared, 0.0)) {
        return Vec3.ZERO
    }
    return scale(newLength / sqrt(squared))
}

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L205-L205)
 */
public fun Vec3.horizontalDistanceToSqr(x: Double, z: Double): Double = Mth.lengthSquared(this.x - x, this.z - z)

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L203-L203)
 */
public fun Vec3.horizontalDistanceToSqr(other: Vec3): Double = horizontalDistanceToSqr(other.x, other.z)

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L201-L201)
 */
public fun Vec3.horizontalDistanceToSqr(other: Vec3i): Double =
    horizontalDistanceToSqr(other.x.toDouble(), other.z.toDouble())

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L199-L199)
 */
public fun Vec3.horizontalDistanceTo(x: Double, z: Double): Double = sqrt(horizontalDistanceToSqr(x, z))

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L197-L197)
 */
public fun Vec3.horizontalDistanceTo(other: Vec3): Double = horizontalDistanceTo(other.x, other.z)

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L195-L195)
 */
public fun Vec3.horizontalDistanceTo(other: Vec3i): Double =
    horizontalDistanceTo(other.x.toDouble(), other.z.toDouble())

/**
 * Distance from this point to the closest point between [from] and [to]; to [from] when both are the same point.
 *
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L207-L210)
 *
 * @agent claude-opus-5-5
 */
public fun Vec3.distanceToSegment(from: Vec3, to: Vec3): Double {
    val alongX = to.x - from.x
    val alongY = to.y - from.y
    val alongZ = to.z - from.z
    val lengthSqr = alongX * alongX + alongY * alongY + alongZ * alongZ
    if (Mth.equal(lengthSqr, 0.0)) {
        return distanceTo(from)
    }

    val projected = (alongX * (x - from.x) + alongY * (y - from.y) + alongZ * (z - from.z)) / lengthSqr
    check(projected.isFinite()) { "Cannot project $this onto $from -> $to" }
    val share = projected.coerceIn(0.0, 1.0)
    val nearest = Vec3(
        Math.fma(share, alongX, from.x),
        Math.fma(share, alongY, from.y),
        Math.fma(share, alongZ, from.z),
    )
    return distanceTo(nearest)
}

/**
 * Squared distance between the block coordinates, without overflow.
 *
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L111-L112)
 *
 * @agent claude-opus-5-5
 */
public fun Vec3i.distSqr(x: Int, y: Int, z: Int): Long {
    val dx = this.x.toLong() - x
    val dy = this.y.toLong() - y
    val dz = this.z.toLong() - z
    return dx * dx + dy * dy + dz * dz
}

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L91-L91)
 */
public operator fun Vec3i.component1(): Int = x

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L92-L92)
 */
public operator fun Vec3i.component2(): Int = y

/**
 * From LiquidBounce: [MinecraftVectorExtensions.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/math/MinecraftVectorExtensions.kt#L93-L93)
 */
public operator fun Vec3i.component3(): Int = z

/**
 * A click lands at least this far from the middle of a face along each of its axes, and at most [CLICK_MOST].
 */
private const val CLICK_LEAST = 0.08
private const val CLICK_MOST = 0.35

/**
 * Where a hand clicks [face] of the block at [pos]: off its middle along both axes of the face, the way [seed] says,
 * so the look is never an exact angle such as straight along an axis, and never the same for two blocks.
 *
 * @agent claude-opus-5-5
 */
@InternalAgentApi
public fun clickPoint(pos: BlockPos, face: Direction, seed: Long): Vec3 {
    val random = Random(seed)
    fun along(axis: Direction.Axis, middle: Double): Double {
        if (face.axis == axis) return middle
        val offset = CLICK_LEAST + random.nextDouble() * (CLICK_MOST - CLICK_LEAST)
        return if (random.nextBoolean()) middle + offset else middle - offset
    }
    val middle = Vec3.atCenterOf(pos).add(face.stepX * HALF_BLOCK, face.stepY * HALF_BLOCK, face.stepZ * HALF_BLOCK)
    return Vec3(
        along(Direction.Axis.X, middle.x),
        along(Direction.Axis.Y, middle.y),
        along(Direction.Axis.Z, middle.z),
    )
}

private const val HALF_BLOCK = 0.5
