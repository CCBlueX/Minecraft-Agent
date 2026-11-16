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

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * What the map did around the player, tick by tick: teleports, chat, armour and effects, blocks set and taken
 * away, entities that came up. Something tried is judged by what happened since a [mark].
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Changes {

    var ticks = 0
        private set

    private var lastPosition: Vec3? = null
    private var lastPlayer: Player? = null
    private var lastArmour: List<String> = emptyList()
    private val seenEffects = HashSet<String>()
    private var lastEntities: Set<Int> = emptySet()
    private var wasFallFlying = false
    private val visited = ArrayDeque<Pair<Int, Vec3>>()

    private val events = ArrayList<Event>()

    /**
     * Tick the map last put the player back where it already was.
     */
    var returnedAt = -1
        private set

    /**
     * Tick of the last chat line from the server.
     */
    var lastChatTick = -CHAT_QUIET
        private set

    val lastChat: String?
        get() = events.lastOrNull { it.kind == Kind.CHAT }?.detail

    /**
     * The chat lines of the last [ticks] ticks, oldest first.
     */
    fun recentChat(ticks: Int): List<String> = events.filter { it.kind == Kind.CHAT && this.ticks - it.tick < ticks }.map { it.detail }

    /**
     * How far [pos] is from every place the player was lately.
     */
    fun unvisited(pos: Vec3): Double = visited.minOfOrNull { it.second.distanceTo(pos) } ?: Double.MAX_VALUE

    /**
     * Places of blocks the server took away near the player, newest last.
     */
    val removed: List<Event>
        get() = events.filter { it.kind == Kind.REMOVED }

    /**
     * Entities that came up near the player, newest last.
     */
    val appeared: List<Event>
        get() = events.filter { it.kind == Kind.APPEARED }

    fun tick(player: Player) {
        ticks++
        val position = player.position()
        // A respawn makes a new player, put back where the map wants it: no teleport of the map's doing
        if (player !== lastPlayer || !player.isAlive) lastPosition = null
        lastPlayer = player
        lastPosition?.let { last ->
            val moved = position.distanceTo(last)
            // A fall or a glide is fast too, but never this fast in one tick; the place left is where it was set off
            if (moved > TELEPORT_DISTANCE && !player.isFallFlying) {
                // Put back where it already was, as by a map that keeps players from the wrong way: no way on
                val back = visited.any { (tick, at) -> ticks - tick > RETURN_AGE && at.distanceTo(position) < RETURN_RANGE }
                if (back) returnedAt = ticks
                add(if (back) Kind.RETURNED else Kind.TELEPORT, last, "to ${position.x.toInt()},${position.y.toInt()},${position.z.toInt()}")
            }
        }
        lastPosition = position
        if (ticks % VISIT_TICKS == 0) {
            visited.addLast(ticks to position)
            if (visited.size > MAX_VISITED) visited.removeFirst()
        }
        if (player.isFallFlying && !wasFallFlying) add(Kind.GLIDE, position, "took off")
        wasFallFlying = player.isFallFlying
        val armour = EquipmentSlot.entries.filter { it.isArmor }.map { BuiltInRegistries.ITEM.getKey(player.getItemBySlot(it).item).toString() }
        if (armour != lastArmour && ticks > 1) {
            add(Kind.ARMOUR, position, armour.joinToString())
        }
        lastArmour = armour
        // Maps hand out the same effects over and over; only one never had before tells something happened
        for (effect in player.activeEffects) {
            val name = effect.effect.registeredName
            if (seenEffects.add(name) && ticks > 1) add(Kind.EFFECT, position, name)
        }
        if (ticks % ENTITY_SCAN_TICKS == 0) {
            // Maps put back their NPCs, seats and figures all the time; a zone or marker coming up is news
            val near = player.level().entitiesNear(position, NEAR).filter { it !is LivingEntity }
            val ids = near.mapTo(HashSet()) { it.id }
            if (lastEntities.isNotEmpty()) {
                near.filter { it.id !in lastEntities }.forEach {
                    add(Kind.APPEARED, it.position(), BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString(), it.id)
                }
            }
            lastEntities = ids
        }
        events.removeIf { ticks - it.tick > KEEP_TICKS }
    }

    fun chat(line: String) {
        lastChatTick = ticks
        add(Kind.CHAT, lastPosition ?: Vec3.ZERO, ChatFormatting.stripFormatting(line) ?: "")
    }

    fun blockChanged(pos: BlockPos, state: BlockState, near: Vec3) {
        val center = Vec3.atCenterOf(pos)
        if (center.distanceTo(near) > NEAR) return
        add(if (state.isAir) Kind.REMOVED else Kind.SET, center, BuiltInRegistries.BLOCK.getKey(state.block).toString())
    }

    /**
     * Now, to look back at with [since].
     */
    fun mark(): Int = ticks

    /**
     * What happened since [mark], the first of each kind, ignoring [ignored] kinds.
     */
    fun since(mark: Int, ignored: Set<Kind> = emptySet()): List<Event> =
        events.filter { it.tick > mark && it.kind !in ignored }.distinctBy { it.kind }

    /**
     * Whether the map has been talking lately, as in a scene that plays.
     */
    val isTalking: Boolean
        get() = ticks - lastChatTick < CHAT_QUIET

    private fun add(kind: Kind, position: Vec3, detail: String, entity: Int = -1) {
        events += Event(kind, ticks, position, detail, entity)
    }

    /**
     * @agent claude-opus-5-5
     */
    enum class Kind(val label: String) {
        TELEPORT("teleport"),
        CHAT("chat"),
        ARMOUR("armour"),
        EFFECT("effect"),
        SET("blocks set"),
        REMOVED("blocks removed"),
        APPEARED("entity appeared"),
        GLIDE("glide"),
        RETURNED("teleport back"),
    }

    /**
     * @agent claude-opus-5-5
     */
    data class Event(val kind: Kind, val tick: Int, val position: Vec3, val detail: String, val entity: Int)

    private companion object {
        const val TELEPORT_DISTANCE = 6.0
        const val VISIT_TICKS = 20
        const val MAX_VISITED = 600
        const val RETURN_AGE = 200
        const val RETURN_RANGE = 6.0
        const val NEAR = 48.0
        const val ENTITY_SCAN_TICKS = 5
        const val KEEP_TICKS = 20 * 60

        /**
         * A scene goes on while the map wrote within this many ticks.
         */
        const val CHAT_QUIET = 20 * 20
    }
}
