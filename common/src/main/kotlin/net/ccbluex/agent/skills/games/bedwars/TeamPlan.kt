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

package net.ccbluex.agent.skills.games.bedwars

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import java.util.Random

/**
 * What an agent does for a few seconds, until it thinks again.
 *
 * @agent claude-opus-5-5
 */
internal enum class Activity { COVER, DEFEND, GEAR, GATHER, DIAMONDS, EMERALDS, UPGRADE, RUSH, HUNT, HELP }

/**
 * What a team's agents are up to: what each one does now (so two do not both do what one is enough for), whose bed
 * a rusher goes for, and the blocks they mean to place (the bed defence and each rusher's bridge). Agents are the
 * keys, compared by identity.
 *
 * @agent claude-opus-5-5
 */
internal class TeamPlan(val team: String, val shape: Shape) {

    constructor(team: String, random: Random) : this(team, Shape.roll(random))

    private val doing = HashMap<Any, Activity>()
    private val targets = HashMap<Any, String>()
    private val claims = HashMap<Any, BlockPos>()
    private val bridges = HashMap<Any, Collection<BlockPos>>()
    private val failures = HashMap<BlockPos, Int>()
    private val dropped = HashSet<BlockPos>()
    private var bed: BlockPos? = null

    /**
     * The defence by layer, then height; empty until the bed is known.
     */
    var defence: Map<BlockPos, Spot> = emptyMap()
        private set

    /**
     * Lays the defence out around the bed at [pos], once per bed.
     */
    fun locate(level: BlockGetter, pos: BlockPos, canPlace: (BlockPos) -> Boolean) {
        if (pos == bed) return
        bed = pos
        defence = defenceSpots(bedHalves(level, pos), shape, canPlace)
    }

    /**
     * Records what [member] does now; [target] is the team it goes for, if any.
     */
    fun doing(member: Any, activity: Activity, target: String?) {
        doing[member] = activity
        if (activity != Activity.COVER) claims.remove(member)
        if (target == null) targets.remove(member) else targets[member] = target
    }

    /**
     * How many members other than [except] do the activity.
     */
    fun count(activity: Activity, except: Any?): Int = doing.count { it.key !== except && it.value == activity }

    /**
     * How many members other than [except] go for the team.
     */
    fun goingFor(target: String, except: Any?): Int = targets.count { it.key !== except && it.value == target }

    fun others(except: Any): List<Any> = doing.keys.filter { it !== except }

    fun leave(member: Any) {
        doing.remove(member)
        targets.remove(member)
        claims.remove(member)
        bridges.remove(member)
    }

    /**
     * The innermost layer with an open block that has something to go against; 0 when there is none.
     */
    fun layer(level: BlockGetter): Int =
        defence.values.firstOrNull { isOpen(level, it) && support(level, it.pos) != null }?.layer ?: 0

    private fun isOpen(level: BlockGetter, spot: Spot) = spot.pos !in dropped && isOpen(level, spot.pos)

    /**
     * The defence block [member] places next: the one it already took while that is still open, else one of the
     * innermost unfinished layer no teammate took, lowest first, then nearest to [near]; null when there is none it
     * can place now.
     */
    fun nextDefence(level: BlockGetter, member: Any, near: Vec3, skip: (BlockPos) -> Boolean): Spot? {
        val layer = layer(level)
        val mine = claims[member]
        var best: Spot? = null
        var bestDistance = Double.MAX_VALUE
        for (spot in defence.values) {
            val pos = spot.pos
            if (spot.layer != layer || !isOpen(level, spot) || support(level, pos) == null || skip(pos) ||
                isTaken(pos, member)
            ) {
                continue
            }
            if (pos == mine) return spot
            val distance = near.distanceToSqr(Vec3.atCenterOf(pos))
            val current = best
            if (current == null || pos.y < current.pos.y || pos.y == current.pos.y && distance < bestDistance) {
                best = spot
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Marks [pos] as the block [member] is placing, so its teammates take others; null lets go of it.
     */
    fun claim(member: Any, pos: BlockPos?) {
        if (pos == null) claims.remove(member) else claims[member] = pos
    }

    private fun isTaken(pos: BlockPos, by: Any) = claims.any { it.key !== by && it.value == pos }

    /**
     * Counts a failed try at a defence block; the team gives up one that keeps failing.
     */
    fun failed(pos: BlockPos) {
        if (pos !in defence) return
        val times = (failures[pos] ?: 0) + 1
        failures[pos] = times
        if (times >= DROP_FAILURES) dropped += pos
    }

    /**
     * Whether standing in [cell] is in the way of placing [target]: a cell of its layer or one inside it.
     */
    fun isInTheWay(cell: BlockPos, target: BlockPos): Boolean {
        val spot = defence[cell] ?: return false
        val aim = defence[target] ?: return true
        return spot.layer <= aim.layer
    }

    /**
     * How many blocks of the unfinished layer can go down now.
     */
    fun openSpots(level: BlockGetter): Int {
        val layer = layer(level)
        return defence.values.count { it.layer == layer && isOpen(level, it) && support(level, it.pos) != null }
    }

    /**
     * The blocks of each fill the unfinished layer and the one after it still take.
     */
    fun lacking(level: BlockGetter): Map<Fill, Int> {
        val layer = layer(level)
        if (layer == 0) return emptyMap()
        return defence.values
            .filter { (it.layer == layer || it.layer == layer + 1) && isOpen(level, it) }
            .groupingBy { it.fill }
            .eachCount()
    }

    fun bridge(member: Any, cells: Collection<BlockPos>?) {
        if (cells.isNullOrEmpty()) bridges.remove(member) else bridges[member] = cells
    }

    /**
     * The blocks the team means to place and has not yet, for its players to see.
     */
    fun planned(level: BlockGetter, bedAlive: Boolean): Set<BlockPos> {
        val planned = LinkedHashSet<BlockPos>()
        if (bedAlive && count(Activity.COVER, null) > 0) {
            val layer = layer(level)
            for (spot in defence.values) if (spot.layer == layer && isOpen(level, spot)) planned += spot.pos
        }
        for (cells in bridges.values) cells.filterTo(planned) { isOpen(level, it) }
        return planned
    }

    private companion object {
        /**
         * Times a defence block may fail to go down before the team gives it up.
         */
        const val DROP_FAILURES = 3
    }
}
