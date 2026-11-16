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

/**
 * A box aligned to the axes; the same tests and ray clips as the game's.
 *
 * @agent claude-opus-5-5
 */
public class AABB(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {

    @JvmField public val minX: Double = minOf(minX, maxX)
    @JvmField public val minY: Double = minOf(minY, maxY)
    @JvmField public val minZ: Double = minOf(minZ, maxZ)
    @JvmField public val maxX: Double = maxOf(minX, maxX)
    @JvmField public val maxY: Double = maxOf(minY, maxY)
    @JvmField public val maxZ: Double = maxOf(minZ, maxZ)

    public constructor(pos: BlockPos) :
        this(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)

    public constructor(from: Vec3, to: Vec3) : this(from.x, from.y, from.z, to.x, to.y, to.z)

    public val xsize: Double get() = maxX - minX
    public val ysize: Double get() = maxY - minY
    public val zsize: Double get() = maxZ - minZ

    public val center: Vec3
        get() = Vec3(Mth.lerp(0.5, minX, maxX), Mth.lerp(0.5, minY, maxY), Mth.lerp(0.5, minZ, maxZ))

    public val bottomCenter: Vec3 get() = Vec3(Mth.lerp(0.5, minX, maxX), minY, Mth.lerp(0.5, minZ, maxZ))

    public fun min(axis: Direction.Axis): Double = axis.choose(minX, minY, minZ)

    public fun max(axis: Direction.Axis): Double = axis.choose(maxX, maxY, maxZ)

    public fun setMinY(value: Double): AABB = AABB(minX, value, minZ, maxX, maxY, maxZ)

    public fun setMaxY(value: Double): AABB = AABB(minX, minY, minZ, maxX, value, maxZ)

    public fun move(x: Double, y: Double, z: Double): AABB =
        AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z)

    public fun move(offset: Vec3): AABB = move(offset.x, offset.y, offset.z)

    public fun move(pos: BlockPos): AABB = move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

    public fun inflate(x: Double, y: Double, z: Double): AABB =
        AABB(minX - x, minY - y, minZ - z, maxX + x, maxY + y, maxZ + z)

    public fun inflate(amount: Double): AABB = inflate(amount, amount, amount)

    public fun deflate(amount: Double): AABB = inflate(-amount)

    public fun expandTowards(x: Double, y: Double, z: Double): AABB = AABB(
        if (x < 0.0) minX + x else minX, if (y < 0.0) minY + y else minY, if (z < 0.0) minZ + z else minZ,
        if (x > 0.0) maxX + x else maxX, if (y > 0.0) maxY + y else maxY, if (z > 0.0) maxZ + z else maxZ,
    )

    public fun expandTowards(movement: Vec3): AABB = expandTowards(movement.x, movement.y, movement.z)

    public fun minmax(other: AABB): AABB = AABB(
        minOf(minX, other.minX), minOf(minY, other.minY), minOf(minZ, other.minZ),
        maxOf(maxX, other.maxX), maxOf(maxY, other.maxY), maxOf(maxZ, other.maxZ),
    )

    public fun intersect(other: AABB): AABB = AABB(
        maxOf(minX, other.minX), maxOf(minY, other.minY), maxOf(minZ, other.minZ),
        minOf(maxX, other.maxX), minOf(maxY, other.maxY), minOf(maxZ, other.maxZ),
    )

    public fun intersects(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean =
        this.minX < maxX && this.maxX > minX && this.minY < maxY && this.maxY > minY && this.minZ < maxZ &&
            this.maxZ > minZ

    public fun intersects(other: AABB): Boolean =
        intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ)

    public fun contains(x: Double, y: Double, z: Double): Boolean =
        x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ

    public fun contains(point: Vec3): Boolean = contains(point.x, point.y, point.z)

    public fun distanceToSqr(point: Vec3): Double {
        val dx = maxOf(maxOf(minX - point.x, point.x - maxX), 0.0)
        val dy = maxOf(maxOf(minY - point.y, point.y - maxY), 0.0)
        val dz = maxOf(maxOf(minZ - point.z, point.z - maxZ), 0.0)
        return Mth.lengthSquared(dx, dy, dz)
    }

    /**
     * Where the ray from [from] to [to] enters this box; null when it misses or starts inside.
     */
    public fun clip(from: Vec3, to: Vec3): Vec3? {
        val hit = Clip(from, to)
        hit.box(minX, minY, minZ, maxX, maxY, maxZ)
        return if (hit.face == null) null else hit.location()
    }

    override fun equals(other: Any?): Boolean = this === other || other is AABB && other.minX.compareTo(minX) == 0 &&
        other.minY.compareTo(minY) == 0 && other.minZ.compareTo(minZ) == 0 && other.maxX.compareTo(maxX) == 0 &&
        other.maxY.compareTo(maxY) == 0 && other.maxZ.compareTo(maxZ) == 0

    override fun hashCode(): Int =
        ((((minX.hashCode() * 31 + minY.hashCode()) * 31 + minZ.hashCode()) * 31 + maxX.hashCode()) * 31 +
            maxY.hashCode()) * 31 + maxZ.hashCode()

    override fun toString(): String = "AABB[$minX, $minY, $minZ] -> [$maxX, $maxY, $maxZ]"

    public companion object {
        @JvmStatic
        public fun ofSize(center: Vec3, x: Double, y: Double, z: Double): AABB = AABB(
            center.x - x / 2.0, center.y - y / 2.0, center.z - z / 2.0,
            center.x + x / 2.0, center.y + y / 2.0, center.z + z / 2.0,
        )

        @JvmStatic
        public fun unitCubeFromLowerCorner(pos: Vec3): AABB =
            AABB(pos.x, pos.y, pos.z, pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
    }
}

/**
 * A ray clipped against boxes: keeps the nearest entry and the face it crosses. The slab test of the game, with its
 * 1e-7 tolerances.
 *
 * @agent claude-opus-5-5
 */
public class Clip(private val from: Vec3, to: Vec3) {

    private val dx = to.x - from.x
    private val dy = to.y - from.y
    private val dz = to.z - from.z
    private var scale = 1.0

    public var face: Direction? = null
        private set

    public fun location(): Vec3 = from.add(scale * dx, scale * dy, scale * dz)

    public fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
        if (dx > EPSILON) {
            plane(dx, dy, dz, minX, minY, maxY, minZ, maxZ, Direction.WEST, from.x, from.y, from.z)
        } else if (dx < -EPSILON) {
            plane(dx, dy, dz, maxX, minY, maxY, minZ, maxZ, Direction.EAST, from.x, from.y, from.z)
        }
        if (dy > EPSILON) {
            plane(dy, dz, dx, minY, minZ, maxZ, minX, maxX, Direction.DOWN, from.y, from.z, from.x)
        } else if (dy < -EPSILON) {
            plane(dy, dz, dx, maxY, minZ, maxZ, minX, maxX, Direction.UP, from.y, from.z, from.x)
        }
        if (dz > EPSILON) {
            plane(dz, dx, dy, minZ, minX, maxX, minY, maxY, Direction.NORTH, from.z, from.x, from.y)
        } else if (dz < -EPSILON) {
            plane(dz, dx, dy, maxZ, minX, maxX, minY, maxY, Direction.SOUTH, from.z, from.x, from.y)
        }
    }

    public fun box(box: AABB) {
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
    }

    @Suppress("LongParameterList")
    private fun plane(
        da: Double, db: Double, dc: Double, point: Double, minB: Double, maxB: Double, minC: Double, maxC: Double,
        newFace: Direction, fromA: Double, fromB: Double, fromC: Double,
    ) {
        val s = (point - fromA) / da
        val pb = fromB + s * db
        val pc = fromC + s * dc
        if (0.0 < s && s < scale && minB - EPSILON < pb && pb < maxB + EPSILON && minC - EPSILON < pc &&
            pc < maxC + EPSILON
        ) {
            scale = s
            face = newFace
        }
    }

    private companion object {
        const val EPSILON = 1.0E-7
    }
}
