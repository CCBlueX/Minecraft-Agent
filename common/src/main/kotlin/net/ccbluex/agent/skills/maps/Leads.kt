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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.entity.ArmorStand
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.entity.Player
import kotlin.math.abs

/**
 * Things on a map worth trying, best first: the villagers and hitboxes maps make buttons of, people with a name over
 * their head, buttons and levers, zones and openings that just came up, and figures on show. What was tried before,
 * how many alike led nowhere, what the signs nearby say and where the chat sends the player weigh in.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Leads(private val switches: Switches = Switches.CHUNKS) {

    private val tried = HashMap<Any, Tried>()

    /**
     * Entity types that sat the player down: maps make seats of invisible mounts, all alike.
     */
    private val seats = HashSet<String>()
    private val avoided = HashSet<Any>()

    /**
     * Where each entity was first seen and when: one that walks about is part of a figure, never a button.
     */
    private val seen = HashMap<Int, Seen>()

    /**
     * Tries of each kind of thing since the player last got somewhere new: after a few switches of a panel did
     * nothing, the rest of the panel waits.
     */
    private val misses = HashMap<String, Int>()

    /**
     * The kind of thing tried last, and the kind that last got the player somewhere new: the other suits on the
     * rack hand out the suit worn already.
     */
    private var lastFamily: String? = null
    private var spent: String? = null
    private var openedAt = 0

    /**
     * Notes where the entities around [player] are, every few ticks.
     */
    fun observe(player: Player, ticks: Int) {
        if (ticks % OBSERVE_TICKS != 0) return
        for (entity in player.level().entitiesNear(player.position(), RANGE)) {
            val last = seen[entity.id]
            if (last == null) {
                seen[entity.id] = Seen(entity.position(), ticks)
            } else if (!last.moved && last.position.distanceTo(entity.position()) > MOVED) {
                last.moved = true
            }
        }
    }

    /**
     * The best lead around [player] now, or null when nothing is left to try. [tried] is where the last thing was
     * tried: what comes up near it right after is what it turned on.
     */
    fun best(player: Player, changes: Changes, signs: List<KnownMap.Sign>, now: Int, tried: Vec3?): Lead? {
        val position = player.position()
        // Something opened since the last look: what could not be reached may be now
        changes.removed.lastOrNull()?.takeIf { it.tick > openedAt }?.let { opened ->
            openedAt = opened.tick
            this.tried.values.removeIf { it.unreachable }
        }
        val hint = changes.recentChat(HINT_TICKS).joinToString(" ").lowercase()
        val talking = changes.isTalking
        return find(player, changes, tried, now)
            // What only answered is worth another go later, from another place, as a lift's button pressed outside it
            .filter { lead -> lead.key !in avoided && this.tried[lead.key]?.let { now - it.tick < if (it.worked) ANSWERED_RETRY_TICKS else RETRY_TICKS } != true }
            // A scene that talks goes on nearby, as a countdown before a drop: nothing far is tried meanwhile
            .filter { lead -> !talking || lead.isNew || lead.position.distanceTo(position) <= TALK_RANGE }
            .minByOrNull { score(it, position, signs, hint) }
    }

    fun tried(lead: Lead, now: Int, worked: Boolean, unreachable: Boolean = false) {
        val last = tried[lead.key]
        tried[lead.key] = Tried(now, (last?.attempts ?: 0) + 1, worked || last?.worked == true, unreachable)
        // A course's plates follow one another; one stepped on says nothing of the next
        if (lead.kind != Lead.Kind.STEP_INTO) misses.merge(lead.family, 1, Int::plus)
        lastFamily = lead.family
    }

    /**
     * The player got somewhere new: every kind of thing is worth a try again there, but the one that got it there.
     */
    fun progressed() {
        misses.clear()
        spent = lastFamily
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

    private fun find(player: Player, changes: Changes, tried: Vec3?, now: Int): List<Lead> {
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
                isButton(entity, now) -> Lead(Lead.Kind.USE_ENTITY, entity.uuid, entity.position(), entity, null, came != null)
                // Came up after something was tried, as a teleporter that turns on
                came != null -> Lead(Lead.Kind.STEP_INTO, entity.uuid, entity.position(), entity, null, true)
                isFigure(entity) -> standingPlace(level, entity.position())
                    ?.let { Lead(Lead.Kind.WALK_UP, entity.uuid, it, entity, null, false) } ?: continue
                else -> continue
            }
        }
        switches.forEach(level, player.blockPosition()) { pos, block ->
            leads += if (block.`is`(BlockKind.PRESSURE_PLATE)) {
                // A plate is there to be stood on, as a course's start and checkpoints
                Lead(Lead.Kind.STEP_INTO, pos, Vec3.atBottomCenterOf(pos), null, pos, false, block.id)
            } else {
                Lead(Lead.Kind.USE_BLOCK, pos, Vec3.atCenterOf(pos), null, pos, false, block.id)
            }
        }
        opening(level, changes, position)?.let { leads += it }
        return leads
    }

    /**
     * A way the map opened lately at the player's feet: a floor taken away with a drop under it, as a hatch, or a
     * wall taken away down to a floor, as a door.
     */
    private fun opening(level: Level, changes: Changes, position: Vec3): Lead? {
        fun isFree(pos: BlockPos) = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
        val feet = BlockPos.containing(position).y
        return changes.removed
            .filter { changes.ticks - it.tick < NEW_TICKS * 3 && it.position.y <= position.y + 2 && it.position.distanceTo(position) < OPENING_RANGE }
            .map { BlockPos.containing(it.position) }
            .mapNotNull { pos ->
                when {
                    (1..DROP_DEPTH).all { isFree(pos.below(it)) } -> pos
                    // A door: the lowest free block of the opened column, standing on a floor near the feet
                    else -> generateSequence(pos) { it.below() }.take(DOOR_HEIGHT).firstOrNull { !isFree(it.below()) }
                        ?.takeIf { isFree(it) && isFree(it.above()) && abs(it.y - feet) <= 1 }
                }
            }
            .minByOrNull { Vec3.atCenterOf(it).distanceTo(position) }
            ?.let { Lead(Lead.Kind.STEP_INTO, "opening@${it.x},${it.y},${it.z}", Vec3.atBottomCenterOf(it), null, null, true) }
    }

    /**
     * What maps make buttons of: villagers standing still (often invisible) and interaction hitboxes. A villager
     * walking about animates a figure; one seen only for a moment is not judged yet.
     */
    private fun isButton(entity: Entity, now: Int): Boolean {
        if (entity.typeId == "minecraft:interaction") return true
        if (entity.typeId != "minecraft:villager" && entity.typeId != "minecraft:wandering_trader") return false
        val seen = seen[entity.id] ?: return false
        return !seen.moved && now - seen.tick >= SETTLE_TICKS
    }

    /**
     * Something to walk up to: someone with a name over the head, or a figure on show such as a suit on a stand.
     */
    private fun isFigure(entity: Entity) = entity.isCustomNameVisible && entity.customName != null ||
        entity is ArmorStand && !entity.isMarker && !entity.isInvisible

    private fun score(lead: Lead, position: Vec3, signs: List<KnownMap.Sign>, hint: String): Double {
        var score = lead.position.distanceTo(position)
        tried[lead.key]?.let { score += it.attempts * TRIED_COST + if (it.unreachable) UNREACHABLE_COST else 0.0 }
        score += (misses[lead.family] ?: 0) * MISS_COST + if (lead.family == spent) SPENT_COST else 0.0
        if (lead.isNew) score -= NEW_BONUS
        if (lead.isNamed) score -= NAMED_BONUS else if (lead.kind == Lead.Kind.WALK_UP) score += WALK_UP_COST
        val words = signs.filter { it.near(lead.position, SIGN_RANGE) }.joinToString(" ") { it.text.lowercase() }
        if (AVOID.any(words::contains)) score += AVOID_COST
        if (GO.any(words::contains)) score -= GO_BONUS
        val above = lead.position.y - position.y
        if (UP.any(hint::contains) && above > 2) score -= GO_BONUS
        if (DOWN.any(hint::contains) && above < -2) score -= GO_BONUS
        if (INTERACT.any(hint::contains) && lead.kind == Lead.Kind.USE_ENTITY) score -= GO_BONUS
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
        val blockId: String? = null,
    ) {

        val isNamed: Boolean
            get() = entity?.isCustomNameVisible == true && entity.customName != null

        /**
         * The kind of thing this is, as a stone button or an invisible villager: alike things tend to do alike.
         */
        val family: String
            get() = "${kind.label} " + when {
                block != null -> blockId ?: "block"
                entity != null -> typeOf(entity) + if (isNamed) " named" else ""
                else -> "place"
            }

        fun describe(): String =
            "${kind.label} ${entity?.let(::typeOf) ?: "block"}" +
                (entity?.customName?.takeIf { isNamed }?.let { " \"$it\"" } ?: "") +
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

    private class Seen(val position: Vec3, val tick: Int) {
        var moved = false
    }

    companion object {

        fun typeOf(entity: Entity): String = entity.typeId

        /**
         * Where a player stands at [pos]: the first free place with a floor at or below it, as under a name tag.
         */
        fun standingPlace(level: Level, pos: Vec3): Vec3? {
            val start = BlockPos.containing(pos)
            for (depth in 0..STAND_DEPTH) {
                val feet = start.below(depth)
                val floor = feet.below()
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty &&
                    !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty
                ) {
                    return Vec3(pos.x, feet.y.toDouble(), pos.z)
                }
            }
            return null
        }

        private const val RANGE = 160.0
        private const val SIGN_RANGE = 5.0
        private const val CAUSE_RANGE = 16.0
        private const val TALK_RANGE = 24.0
        private const val RETRY_TICKS = 20 * 45
        private const val ANSWERED_RETRY_TICKS = 20 * 90
        private const val NEW_TICKS = 20 * 10
        private const val HINT_TICKS = 20 * 40
        private const val OBSERVE_TICKS = 10
        private const val SETTLE_TICKS = 40
        private const val MOVED = 1.5
        private const val STAND_DEPTH = 6
        private const val DROP_DEPTH = 3
        private const val DOOR_HEIGHT = 4
        private const val OPENING_RANGE = 32.0
        private const val TRIED_COST = 40.0
        private const val UNREACHABLE_COST = 60.0
        private const val MISS_COST = 40.0
        private const val SPENT_COST = 80.0
        private const val NEW_BONUS = 40.0
        private const val NAMED_BONUS = 40.0
        private const val WALK_UP_COST = 30.0
        private const val AVOID_COST = 80.0
        private const val GO_BONUS = 40.0

        // What signs say by the things maps let a player switch, as opposed to what moves the map on
        private val AVOID = listOf("toggle", "caption", "music", "voice", "test", "setting", "credits", "reset", "speedrun", "advert")
        private val GO = listOf("start", "play", "begin", "teleport", "enter", "go ")
        private val UP = listOf("upstairs", "up the", "above", "climb")
        private val DOWN = listOf("downstairs", "down to", "below", "descend")
        private val INTERACT = listOf("interact", "right-click", "right click", "talk to")
    }
}
