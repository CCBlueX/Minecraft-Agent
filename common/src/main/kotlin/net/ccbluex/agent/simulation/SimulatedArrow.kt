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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.world.EntityHitResult
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.entity.Entity

/**
 * An arrow flying from [pos] at [deltaMovement] as AbstractArrow moves it, until it hits a block of [blocks] or one of
 * [entities], when given. The level stays untouched.
 *
 * From LiquidBounce: [SimulatedArrow.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/SimulatedArrow.kt#L33-L128)
 *
 * @agent claude-opus-5-5
 */
public class SimulatedArrow(
    private val blocks: BlockGetter,
    private val entities: Level?,
    pos: Vec3,
    deltaMovement: Vec3,
) {

    /**
     * An arrow in [level], hitting its entities only with [collideEntities].
     */
    @JvmOverloads
    public constructor(level: Level, pos: Vec3, deltaMovement: Vec3, collideEntities: Boolean = true) :
        this(level, level.takeIf { collideEntities }, pos, deltaMovement)

    public var pos: Vec3 = pos
        private set

    public var deltaMovement: Vec3 = deltaMovement
        private set

    /**
     * It hit something and stays where it hit.
     */
    public var isInGround: Boolean = false
        private set

    /**
     * Flies one tick; what it hit in that tick, else null.
     */
    public fun tick(): HitResult? {
        if (isInGround) {
            return null
        }

        val next = pos.add(deltaMovement)
        // shortcut: the stronger drag in water (0.6) is left out, as in LiquidBounce; add it when arrows in water matter
        deltaMovement = Vec3(deltaMovement.x * DRAG, deltaMovement.y * DRAG - GRAVITY, deltaMovement.z * DRAG)

        val hit = hitBetween(pos, next)
        if (hit != null) {
            pos = hit.location
            isInGround = true
            return hit
        }

        pos = next
        return null
    }

    private fun hitBetween(from: Vec3, to: Vec3): HitResult? {
        val block = blocks.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, null))
            .takeIf { it.type != HitResult.Type.MISS }
        return entities?.let { entityHit(it, from, block?.location ?: to) } ?: block
    }

    /**
     * ProjectileUtil.getEntityHitResult for an arrow in its first tick: the nearest box crossed, without margin.
     */
    private fun entityHit(entities: Level, from: Vec3, to: Vec3): EntityHitResult? {
        val area = AABB(from, from).inflate(SEARCH_SIZE).expandTowards(to.subtract(from)).inflate(1.0)
        var nearest = Double.MAX_VALUE
        var hit: EntityHitResult? = null
        for (entity in entities.getEntities(null, area, ::canHit)) {
            val at = entity.boundingBox.clip(from, to) ?: continue
            val distance = from.distanceToSqr(at)
            if (distance < nearest) {
                nearest = distance
                hit = EntityHitResult(entity, at)
            }
        }
        return hit
    }

    private fun canHit(entity: Entity): Boolean = !entity.isSpectator && entity.isAlive && entity.isPickable

    private companion object {
        const val DRAG = 0.99
        const val GRAVITY = 0.05000000074505806
        const val SEARCH_SIZE = 0.45
    }
}
