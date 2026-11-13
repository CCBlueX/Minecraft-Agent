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

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityFluidInteraction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

/**
 * Vanilla entity fields without accessors that a copy of a player needs. The shared modules link against the plain
 * game on Fabric and on Paper, where no access widener applies, so they are reached by their Mojang names.
 *
 * @agent claude-opus-5-5
 */
internal object EntityFields {

    private val noJumpDelay = field(LivingEntity::class.java, "noJumpDelay", Int::class.javaPrimitiveType)
    private val stuckSpeedMultiplier = field(Entity::class.java, "stuckSpeedMultiplier", Vec3::class.java)
    private val fluidInteraction = field(Entity::class.java, "fluidInteraction", EntityFluidInteraction::class.java)

    /**
     * LivingEntity.noJumpDelay: ticks until holding jump jumps again.
     */
    fun noJumpDelay(entity: LivingEntity): Int = noJumpDelay.get(entity) as Int

    fun setNoJumpDelay(entity: LivingEntity, ticks: Int) {
        noJumpDelay.set(entity, ticks)
    }

    /**
     * Entity.stuckSpeedMultiplier: the slowdown of a cobweb or berry bush the entity is in, zero outside one.
     */
    fun stuckSpeedMultiplier(entity: Entity): Vec3 = stuckSpeedMultiplier.get(entity) as Vec3

    fun fluidInteraction(entity: Entity): EntityFluidInteraction =
        fluidInteraction.get(entity) as EntityFluidInteraction

    private fun field(owner: Class<*>, name: String, type: Class<*>?): VarHandle {
        val lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
        return lookup.findVarHandle(owner, name, requireNotNull(type))
    }
}
