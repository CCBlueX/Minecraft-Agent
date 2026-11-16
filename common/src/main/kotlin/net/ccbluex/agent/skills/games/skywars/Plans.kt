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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.games.WayCost
import java.util.Locale
import java.util.Random

/**
 * An enemy as a [SkyWars] decision sees it. [way] is the straight way there; [reachable] means on foot.
 *
 * @agent claude-opus-5-5
 */
internal class Foe(
    val id: Int,
    val position: Vec3,
    val health: Double,
    val gear: Double,
    val onGround: Boolean,
    val reachable: Boolean,
    val way: WayCost,
    /**
     * Someone not on its team stands close to it, so it is busy.
     */
    val fightingOther: Boolean,
    /**
     * It stands on a cage's island or the middle, rather than out on a bridge.
     */
    val onIsland: Boolean,
    val teammatesNear: Int,
)

/**
 * A place a [SkyWars] decision may go to, with the way there.
 *
 * @agent claude-opus-5-5
 */
internal class Place(val point: Vec3, val way: WayCost, val chests: List<BlockPos> = emptyList(), val id: Int? = null)

/**
 * Everything one [SkyWars] decision weighs, taken at once so the scores are plain arithmetic.
 *
 * @agent claude-opus-5-5
 */
internal class Situation(
    val position: Vec3,
    val island: Vec3,
    val health: Double,
    /**
     * Own [net.ccbluex.agent.skills.gear.GearScore.equipment].
     */
    val gear: Double,
    val blocks: Int,
    val hasWeapon: Boolean,
    val pearls: Int,
    val seconds: Int,
    val secondsToRefill: Int?,
    val doom: Boolean,
    /**
     * The middle's ground, with the way there.
     */
    val middle: Place?,
    val ownChests: List<BlockPos>,
    val middleChests: List<BlockPos>,
    /**
     * Islands without anyone on them whose chests nobody touched.
     */
    val raids: List<Place>,
    val enemies: List<Foe>,
    /**
     * Teammates far enough away to regroup with.
     */
    val strayTeammates: List<Place>,
    val digCandidate: BlockPos?,
    /**
     * The nearest wanted item lying around, by entity id.
     */
    val pickup: Int?,
    /**
     * Whether it opened any chest this round.
     */
    val looted: Boolean,
    val heldTicks: Int,
    val holdTicks: Int,
) {
    val nearestEnemy: Double = enemies.minOfOrNull { it.position.distanceTo(position) } ?: Double.MAX_VALUE
    val enemyGear: Double = if (enemies.isEmpty()) 0.0 else enemies.sumOf { it.gear } / enemies.size

    /**
     * Waiting gets less attractive the longer the round runs.
     */
    val pressure: Double = minOf(PRESSURE_CAP, seconds / PRESSURE_SECONDS)

    fun canGo(way: WayCost): Boolean = way.bridgeBlocks <= blocks

    /**
     * How dangerous [at] looks: enemies on their way or at it, the better geared counting more.
     */
    fun danger(at: Vec3): Double = enemies.sumOf {
        if (it.position.horizontalDistanceTo(at) >= DANGER_RANGE) 0.0 else if (it.gear > gear) 1.5 else 0.7
    }

    companion object {
        const val PRESSURE_CAP = 35.0
        const val PRESSURE_SECONDS = 8.0
        const val DANGER_RANGE = 18.0
    }
}

/**
 * Scores what a [SkyWars] agent can do now: its own chests first, then the middle, empty islands, players it can
 * get to and beat, two players fighting, digging for blocks, holding behind a wall, backing off to heal, its
 * teammates, and the middle once the border closes in. Distances are the [WayCost] in blocks of plain walk, so void
 * to bridge over and climbs count, and a place is only gone for when the blocks for its bridge are there.
 *
 * @agent claude-opus-5-5
 */
internal object Plans {

    /**
     * The way's cost as blocks of plain walk.
     */
    fun travel(way: WayCost): Double = way.value / WayCost.DISTANCE_COST

    /**
     * The option with the best score plus noise of [whim] and the commit bonus for [current]. While [current] heads
     * for a chest, no other chest plan takes its place. With no option left, [current] goes on until it ends itself.
     */
    fun pick(options: List<Plan>, current: Plan?, whim: Double, random: Random, chestUnderway: Boolean): Plan {
        val keepsChest = current != null && chestUnderway && current.activity.isChests &&
            options.any { current.same(it) }
        val candidates = if (keepsChest) options.filter { !it.activity.isChests || it.same(current) } else options
        return candidates.maxByOrNull {
            it.score + random.nextGaussian() * NOISE * whim + if (it.same(current)) COMMIT else 0.0
        } ?: current ?: Plan(Activity.HOLD, 0.0, reason = "nothing")
    }

    fun score(s: Situation, p: Personality): List<Plan> {
        val options = ArrayList<Plan>()
        val middle = s.middle
        if (s.doom && middle != null && middle.point.horizontalDistanceTo(s.position) > DOOM_DISTANCE) {
            val centre = Vec3(middle.point.x, s.position.y, middle.point.z)
            options += Plan(Activity.DOOM, DOOM_SCORE, point = centre, reason = "border closes in")
        }
        if (s.nearestEnemy < RETREAT_RANGE && s.health <= 6 + 8 * p.caution) {
            options += Plan(Activity.RETREAT, RETREAT_SCORE, reason = "hp=${s.health.toInt()}")
        }
        loot(s, p)?.let { options += it }
        middle(s, p)?.let { options += it }
        s.raids.maxByOrNull { raidScore(it, p) }?.let {
            options += Plan(Activity.RAID, raidScore(it, p), point = it.point, chests = it.chests, reason = "empty")
        }
        s.enemies.mapNotNullTo(options) { hunt(s, p, it) }
        gather(s, p)?.let { options += it }
        if (s.pickup != null && s.nearestEnemy > PICKUP_CLEAR) {
            options += Plan(Activity.PICKUP, PICKUP_SCORE, targetId = s.pickup, reason = "item")
        }
        s.strayTeammates.filter { s.canGo(it.way) }.mapTo(options) {
            Plan(Activity.REGROUP, 30 + 40 * (1 - p.aggression / 2), targetId = it.id, point = it.point)
        }
        if (s.heldTicks < s.holdTicks && s.enemies.size >= 2) {
            val geared = if (s.gear >= s.enemyGear + 2) 8.0 else 0.0
            options += Plan(Activity.HOLD, 10 + 35 * p.caution + geared - s.pressure * 1.2, reason = "enemies")
        }
        return options
    }

    private fun loot(s: Situation, p: Personality): Plan? {
        if (s.ownChests.isEmpty()) return null
        // A rusher leaves the last chests once it has a sword and blocks.
        val rush = s.looted && s.blocks >= RUSH_BLOCKS && s.hasWeapon
        val score = LOOT_SCORE - if (rush) 100 * maxOf(0.0, p.aggression - 0.6) else 0.0
        return Plan(Activity.LOOT, score, chests = s.ownChests, reason = "own chests")
    }

    private fun middle(s: Situation, p: Personality): Plan? {
        val middle = s.middle ?: return null
        if (!s.canGo(middle.way)) return null
        val danger = s.danger(middle.point)
        if (s.middleChests.isNotEmpty()) {
            val early = if (s.seconds < EARLY_MIDDLE) 10.0 else 0.0
            val needs = ((20 - s.gear) * 1.5).coerceIn(-15.0, 15.0)
            val score = 25 + 50 * p.greed + early + needs - danger * (10 + 15 * p.caution)
            return Plan(Activity.MIDDLE, score, point = middle.point, chests = s.middleChests, reason = "chests")
        }
        val refill = s.secondsToRefill ?: return null
        if (refill !in 0 until REFILL_WAIT) return null
        val score = 30 + 30 * p.greed - danger * 10
        return Plan(Activity.MIDDLE, score, point = middle.point, reason = "refill in ${refill}s")
    }

    fun raidScore(raid: Place, p: Personality): Double = 30 + 30 * p.greed - 0.4 * travel(raid.way)

    fun hunt(s: Situation, p: Personality, foe: Foe): Plan? {
        var pearl = false
        if (!foe.reachable && !s.canGo(foe.way)) {
            val spare = if (p.aggression > 0.6) 0 else 1
            if (s.pearls <= spare || foe.way.distance > PEARL_RANGE || !foe.onGround) return null
            pearl = true
        }
        val edge = (2 * (s.gear - foe.gear) * (0.5 + p.caution)).coerceIn(-25.0, 20.0)
        var score = 15 + 40 * p.aggression + edge + 10 * (1 - foe.health / 20) + 30 * (s.health / 20 - 0.6) -
            (if (s.health < 8) 20 else 0) - 0.6 * travel(foe.way) + s.pressure + if (s.enemies.size <= 2) 12 else 0
        if (foe.fightingOther) score += 30 * p.opportunism
        // Early on a rusher goes for a neighbour before they are geared up, the others loot first.
        if (s.seconds < EARLY_RAID && foe.onIsland) score += if (p.aggression > 0.65) 15 else -10
        score += 15 * foe.teammatesNear
        if (pearl) score -= 10
        val reason = buildString {
            append("edge=").append("%.1f".format(Locale.ROOT, edge))
            if (foe.fightingOther) append(" busy")
            if (pearl) append(" pearl")
        }
        return Plan(Activity.HUNT, score, targetId = foe.id, pearl = pearl, thirdParty = foe.fightingOther, reason = reason)
    }

    private fun gather(s: Situation, p: Personality): Plan? {
        val block = s.digCandidate ?: return null
        if (s.blocks >= GATHER_BELOW || s.position.horizontalDistanceTo(s.island) > SkyWars.ISLAND_RADIUS + 2) {
            return null
        }
        val score = 30 + 30 * p.caution + (if (s.blocks < 6) 25 else 0) - if (s.seconds > LATE_GATHER) 15 else 0
        return Plan(Activity.GATHER, score, point = Vec3.atCenterOf(block), reason = "blocks=${s.blocks}")
    }

    /**
     * Gaussian noise on every score, times the personality's whim.
     */
    const val NOISE = 6.0

    /**
     * What sticking with the current plan is worth: another plan has to be this much better to take over, so a plan
     * runs until it ends or something clearly beats it, and the agent does not flip between close choices.
     */
    const val COMMIT = 25.0

    const val DOOM_SCORE = 150.0
    const val DOOM_DISTANCE = 10.0
    const val RETREAT_SCORE = 120.0
    const val RETREAT_RANGE = 20.0
    const val LOOT_SCORE = 100.0
    const val RUSH_BLOCKS = 24
    const val EARLY_MIDDLE = 90
    const val REFILL_WAIT = 15
    const val PEARL_RANGE = 36.0
    const val EARLY_RAID = 60
    const val GATHER_BELOW = 16
    const val LATE_GATHER = 200
    const val PICKUP_SCORE = 75.0
    const val PICKUP_CLEAR = 8.0
}
