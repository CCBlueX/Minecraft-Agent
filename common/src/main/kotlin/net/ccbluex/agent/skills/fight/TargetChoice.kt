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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.skills.Agent
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * A hit this recent turns the fighter to the attacker.
 */
internal const val TURN_TICKS = 40

/**
 * Another opponent this many blocks closer than the current target takes over.
 */
internal const val CLOSER = 2.0

/**
 * The target to fight: [engaged] wherever it is; else the [attacker] when it hit within [TURN_TICKS] ticks; when
 * [hunts], the [current] target unless [nearest] is more than [CLOSER] blocks closer; else the attacker. Only
 * candidates [inArea] count besides the engaged one. Callers pass only valid candidates in range.
 *
 * @agent claude-opus-5-5
 */
internal fun <T : Any> chooseTarget(
    engaged: T?,
    attacker: T?,
    sinceHit: Long,
    current: T?,
    nearest: T?,
    hunts: Boolean,
    inArea: (T) -> Boolean,
    distance: (T) -> Double,
): T? {
    if (engaged != null) return engaged
    val hitter = attacker?.takeIf(inArea)
    if (hitter != null && sinceHit <= TURN_TICKS) return hitter
    if (!hunts) return hitter
    val closest = nearest?.takeIf(inArea)
    val kept = current?.takeIf(inArea)
    if (kept != null && (closest == null || distance(kept) <= distance(closest) + CLOSER)) return kept
    return closest ?: hitter
}

/**
 * Whether [entity] stands in the rules' area; true without one.
 *
 * @agent claude-opus-5-5
 */
internal fun BuiltInFight.inArea(entity: Entity): Boolean = rules.area?.contains(entity.position()) != false

/**
 * The nearest opponent in sight, within the rules' range and area.
 *
 * @agent claude-opus-5-5
 */
internal fun BuiltInFight.nearestOpponent(agent: Agent): LivingEntity? {
    val senses = agent.senses
    return body.players()
        .filter { it !== player && it.distanceToSqr(player) <= rules.range * rules.range && valid(it) }
        .filter { senses?.isOpponent(it) ?: !it.isInvisible }
        .filter { inArea(it) && player.hasLineOfSight(it) }
        .minByOrNull { it.distanceToSqr(player) }
}
