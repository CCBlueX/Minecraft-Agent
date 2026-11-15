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

package net.ccbluex.agent.skills.maps

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.Vec3

/**
 * Things on a map worth trying, best first: NPCs and the invisible villagers maps make buttons of, buttons and
 * levers, entities and openings that just came up, and figures to walk up to. What was tried before, what the
 * signs nearby say and where the chat sends the player weigh in.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Leads {

    private val tried = HashMap<Any, Tried>()

    /**
     * Entity types that sat the player down: maps make seats of invisible mounts, all alike.
     */
    private val seats = HashSet<String>()
    private val avoided = HashSet<Any>()

    /**
     * The best lead around [player] now, or null when nothing is left to try. [tried] is where the last thing was
     * tried: what comes up near it right after is what it turned on.
     */
    fun best(player: Player, changes: Changes, signs: List<KnownMap.Sign>, now: Int, tried: Vec3?): Lead? {
        val position = player.position()
        val hint = changes.lastChat?.takeIf { changes.isTalking }.orEmpty().lowercase()
        return find(player, changes, tried)
            .filter { lead -> lead.key !in avoided && this.tried[lead.key]?.let { it.worked || now - it.tick < RETRY_TICKS } != true }
            .minByOrNull { score(it, position, signs, hint, now) }
    }

    fun tried(lead: Lead, now: Int, worked: Boolean, unreachable: Boolean = false) {
        val last = tried[lead.key]
        tried[lead.key] = Tried(now, (last?.attempts ?: 0) + 1, worked || last?.worked == true, unreachable)
    }

    /**
     * Gives every lead tried without luck another go, as when the map moved on in a scene.
     */
    fun retry() {
        tried.entries.removeIf { !it.value.worked }
    }

    fun seat(lead: Lead) {
        lead.entity?.let { seats += typeOf(it) }
    }

    /**
     * Never tries [lead] again, as a switch that turns the map's captions off.
     */
    fun avoid(lead: Lead) {
        avoided += lead.key
    }

    private fun find(player: Player, changes: Changes, tried: Vec3?): List<Lead> {
        val level = player.level()
        val position = player.position()
        val leads = ArrayList<Lead>()
        val appeared = changes.appeared
            .filter { changes.ticks - it.tick < NEW_TICKS && tried != null && it.position.distanceTo(tried) < CAUSE_RANGE }
            .associateBy { it.entity }
        for (entity in level.entitiesNear(position, RANGE)) {
            if (entity is Player || !entity.isAlive || entity.position().distanceTo(position) > RANGE) continue
            if (entity.isInvisible && typeOf(entity) in seats) continue
            val came = appeared[entity.id]
            leads += when {
                entity is Interaction || entity is LivingEntity && entity !is ArmorStand &&
                    (entity.isInvisible || entity.hasCustomName() || entity.type.toString().contains("villager")) ->
                    Lead(Lead.Kind.USE_ENTITY, entity.uuid, entity.position(), entity, null, came != null)
                // Came up after something was tried, as a teleporter that turns on
                came != null -> Lead(Lead.Kind.STEP_INTO, entity.uuid, entity.position(), entity, null, true)
                entity is ArmorStand && !entity.isMarker && !entity.isInvisible ->
                    Lead(Lead.Kind.WALK_UP, entity.uuid, entity.position(), entity, null, false)
                else -> continue
            }
        }
        val center = player.blockPosition()
        for (pos in BlockPos.betweenClosed(center.offset(-BLOCK_RANGE, -BLOCK_RANGE / 2, -BLOCK_RANGE), center.offset(BLOCK_RANGE, BLOCK_RANGE / 2, BLOCK_RANGE))) {
            val block = level.getBlockState(pos).block
            if (block is ButtonBlock || block is LeverBlock) {
                val immutable = pos.immutable()
                leads += Lead(Lead.Kind.USE_BLOCK, immutable, Vec3.atCenterOf(immutable), null, immutable, false)
            }
        }
        // A floor the map took away, as a hatch that opens
        changes.removed.lastOrNull()?.let { opening ->
            val below = BlockPos.containing(opening.position)
            if (level.getBlockState(below.below()).isAir) {
                leads += Lead(Lead.Kind.STEP_INTO, "opening@${below.x},${below.z}", opening.position, null, null, true)
            }
        }
        return leads
    }

    private fun score(lead: Lead, position: Vec3, signs: List<KnownMap.Sign>, hint: String, now: Int): Double {
        var score = lead.position.distanceTo(position)
        tried[lead.key]?.let { score += it.attempts * TRIED_COST + if (it.unreachable) UNREACHABLE_COST else 0.0 }
        if (lead.isNew) score -= NEW_BONUS
        if (lead.kind == Lead.Kind.WALK_UP) score += WALK_UP_COST
        val words = signs.filter { it.near(lead.position, SIGN_RANGE) }.joinToString(" ") { it.text.lowercase() }
        if (AVOID.any(words::contains)) score += AVOID_COST
        if (GO.any(words::contains)) score -= GO_BONUS
        val above = lead.position.y - position.y
        if (UP.any(hint::contains) && above > 2) score -= GO_BONUS
        if (DOWN.any(hint::contains) && above < -2) score -= GO_BONUS
        return score
    }

    private fun KnownMap.Sign.near(pos: Vec3, range: Double) =
        Vec3(x + 0.5, y + 0.5, z + 0.5).distanceTo(pos) <= range

    /**
     * @agent claude-opus-5-5
     */
    data class Lead(
        val kind: Kind,
        val key: Any,
        val position: Vec3,
        val entity: Entity?,
        val block: BlockPos?,
        val isNew: Boolean,
    ) {

        fun describe(): String =
            "${kind.label} ${entity?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() } ?: "block"}" +
                (entity?.customName?.string?.let { " \"$it\"" } ?: "") +
                " at ${position.x.toInt()},${position.y.toInt()},${position.z.toInt()}"

        /**
         * @agent claude-opus-5-5
         */
        enum class Kind(val label: String) {
            USE_ENTITY("interact"),
            USE_BLOCK("use"),
            STEP_INTO("step into"),
            WALK_UP("walk up to"),
        }
    }

    private data class Tried(val tick: Int, val attempts: Int, val worked: Boolean, val unreachable: Boolean)

    companion object {

        fun typeOf(entity: Entity): String = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

        private const val RANGE = 96.0
        private const val BLOCK_RANGE = 12
        private const val SIGN_RANGE = 5.0
        private const val CAUSE_RANGE = 16.0
        private const val RETRY_TICKS = 20 * 45
        private const val NEW_TICKS = 20 * 10
        private const val TRIED_COST = 40.0
        private const val UNREACHABLE_COST = 60.0
        private const val NEW_BONUS = 40.0
        private const val WALK_UP_COST = 30.0
        private const val AVOID_COST = 80.0
        private const val GO_BONUS = 40.0

        // What signs say by the things maps let a player switch, as opposed to what moves the map on
        private val AVOID = listOf("toggle", "caption", "music", "voice", "test", "setting", "credits", "reset", "speedrun", "advert")
        private val GO = listOf("start", "play", "begin", "teleport", "enter", "go ")
        private val UP = listOf("upstairs", "up the", "above", "climb")
        private val DOWN = listOf("downstairs", "down to", "below", "descend")
    }
}
