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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.skills.games.WayCost
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Who the agent has to deal with: whoever hit it, enemies close by or at its bed, enemies at the base or coming at
 * it over the void, teammates with an enemy on them, and players of bedless teams to hunt.
 *
 * @agent claude-opus-5-5
 */
internal class Threats(private val senses: Senses) {

    /**
     * A teammate in a fight, and the enemy they fight.
     */
    class Brawl(val mate: Player, val enemy: Player)

    private var attacker: Player? = null
    private var attackerUntil = 0L

    /**
     * How far each enemy was from the base at the last look, and at the one before.
     */
    private val approach = HashMap<Player, DoubleArray>()
    private var lastIntruder: Player? = null
    private var intruderAt = -ALARM_TICKS.toLong()

    fun damaged(attacker: Entity?, tick: Long) {
        if (attacker !is Player) return
        this.attacker = attacker
        attackerUntil = tick + HURT_TICKS
    }

    fun forget() {
        attacker = null
        approach.clear()
        lastIntruder = null
    }

    /**
     * Players of other teams who still play and are not waiting to respawn.
     */
    fun enemies(self: Player, team: BedWarsTeam): List<Player> =
        senses.teams.filter { it.name != team.name }.flatMap { it.members }.filter { isEnemy(self, team, it) }

    fun isEnemy(self: Player, team: BedWarsTeam, player: Player?): Boolean {
        if (player == null || player === self || !player.isAlive || player.isSpectator) return false
        if (player.level() !== self.level() || senses.isRespawning(player)) return false
        val other = senses.teamOf(player) ?: return false
        return other.name != team.name
    }

    /**
     * Who to fight now: whoever hit it lately, an enemy close by, or one by the bed when it is at home; only one it
     * can hit or walk up to. One across a gap is for the bridge or the edge of the island, not for a standoff.
     */
    fun threat(self: Player, team: BedWarsTeam, guardsBed: Boolean, terrain: Terrain, tick: Long): Player? {
        val attacker = attacker
        if (tick < attackerUntil && isEnemy(self, team, attacker) && attacker != null &&
            self.distanceTo(attacker) < REVENGE_RANGE && isEngageable(self, attacker, terrain)
        ) {
            return attacker
        }
        val bed = team.bed?.takeIf { team.isBedAlive && guardsBed }?.let(Vec3::atCenterOf)
        return enemies(self, team)
            .filter { enemy ->
                val distance = self.distanceTo(enemy)
                distance < FIGHT_RANGE && self.hasLineOfSight(enemy) ||
                    bed != null && enemy.position().distanceTo(bed) < GUARD_RANGE
            }
            .filter { isEngageable(self, it, terrain) }
            .minByOrNull { self.distanceToSqr(it) }
    }

    /**
     * In sword reach, or reachable on foot: not standing over the void, with ground all the way to it. The void check
     * is the client's; it keeps the agent from chasing an enemy out over a bridge it would fall from.
     */
    fun isEngageable(self: Player, enemy: Player, terrain: Terrain): Boolean {
        val here = self.position()
        val there = enemy.position()
        if (here.horizontalDistanceTo(there) <= MELEE && abs(there.y - here.y) < 2.5) return true
        if (WayCost.isVoidBelow(terrain, there.x, there.y + 0.5, there.z)) return false
        return abs(there.y - here.y) < MAX_CLIMB && WayCost.of(terrain, here, there).voidColumns == 0
    }

    /**
     * What the agent protects: the bed while it stands, else the team spawn.
     */
    fun base(team: BedWarsTeam): Vec3 = team.bed?.takeIf { team.isBedAlive }?.let(Vec3::atCenterOf) ?: team.spawn

    fun trackApproach(self: Player, team: BedWarsTeam) {
        val base = base(team)
        approach.keys.removeIf { !isEnemy(self, team, it) }
        for (enemy in enemies(self, team)) {
            val distance = enemy.position().distanceTo(base)
            val seen = approach[enemy]
            approach[enemy] = doubleArrayOf(distance, seen?.get(0) ?: distance)
        }
    }

    private fun isAlarming(enemy: Player, seen: DoubleArray): Boolean {
        val distance = seen[0]
        val below = enemy.blockPosition().below(4)
        val closing = distance < APPROACH_RANGE && seen[1] - distance >= 1.5 && !isSolid(enemy.level(), below)
        return distance < ALERT_RANGE || closing
    }

    /**
     * The enemy nearest the base among those at it or coming at it over the void, and the last one for a while after
     * it stops closing in while it is still about; null for none.
     */
    fun intruder(self: Player, team: BedWarsTeam, tick: Long): Player? {
        val best = approach.entries
            .filter { isEnemy(self, team, it.key) && isAlarming(it.key, it.value) }
            .minByOrNull { it.value[0] }?.key
        if (best != null) {
            lastIntruder = best
            intruderAt = tick
            return best
        }
        val last = lastIntruder ?: return null
        val near = last.position().distanceTo(base(team)) < APPROACH_RANGE
        return last.takeIf { tick - intruderAt < ALARM_TICKS && isEnemy(self, team, it) && near }
    }

    fun intruders(self: Player, team: BedWarsTeam): Int =
        approach.count { isEnemy(self, team, it.key) && isAlarming(it.key, it.value) }

    /**
     * The nearest teammate within reach who has an enemy on them; null for none.
     */
    fun brawl(self: Player, team: BedWarsTeam): Brawl? {
        val enemies = enemies(self, team)
        var best: Brawl? = null
        var bestDistance = HELP_RANGE
        for (mate in team.members) {
            if (mate === self || !mate.isAlive || mate.isSpectator || mate.level() !== self.level() ||
                senses.isRespawning(mate)
            ) {
                continue
            }
            val distance = self.distanceTo(mate).toDouble()
            if (distance > bestDistance) continue
            val enemy = enemies.firstOrNull { it.distanceTo(mate) < BRAWL_RANGE } ?: continue
            best = Brawl(mate, enemy)
            bestDistance = distance
        }
        return best
    }

    /**
     * The nearest enemy of a team without its bed, or of any team once no bed is left; null for none.
     */
    fun prey(self: Player, team: BedWarsTeam): Player? {
        val beds = senses.teams.any { it.name != team.name && it.isBedAlive && it.members.isNotEmpty() }
        return enemies(self, team)
            .filter { !beds || senses.teamOf(it)?.isBedAlive == false }
            .minByOrNull { self.distanceToSqr(it) }
    }

    /**
     * Whether blocks placed in the round guard the bed at [bed].
     */
    fun isCovered(self: Player, bed: BlockPos): Boolean {
        val level = self.level()
        return COVER_SIDES.any { offset ->
            val next = bed.offset(offset)
            isSolid(level, next) && !isBed(level, next) && senses.isPlayerPlaced(next)
        }
    }

    companion object {
        /**
         * Enemies this close are fought whatever the agent does.
         */
        const val FIGHT_RANGE = 5.0
        const val REVENGE_RANGE = 10.0

        /**
         * Enemies this close, and in reach of a sword, are fought even across a gap.
         */
        const val MELEE = 3.2
        const val ALERT_RANGE = 20.0
        const val APPROACH_RANGE = 36.0
        const val APPROACH_TICKS = 20
        const val ALARM_TICKS = 100
        const val HURT_TICKS = 100
        const val HELP_RANGE = 30.0
        const val BRAWL_RANGE = 6.0
        private const val MAX_CLIMB = 4.0

        private val COVER_SIDES = listOf(
            BlockPos(0, 1, 0), BlockPos(0, 0, -1), BlockPos(0, 0, 1), BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
        )
    }
}
