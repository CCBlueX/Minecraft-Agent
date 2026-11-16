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

package net.ccbluex.agent.skills.games.blitz

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.gear.GearScore

/**
 * What a [Blitz] agent goes for next.
 *
 * @agent claude-opus-5-5
 */
internal sealed interface Choice {
    val label: String

    class Loot(val chest: BlockPos) : Choice {
        override val label: String = "chest ${chest.x},${chest.y},${chest.z}"
    }

    class Hunt(val id: Int, name: String) : Choice {
        override val label: String = "player $name"
    }

    /**
     * Stays put until a refill.
     */
    class Hold(val point: Vec3) : Choice {
        override val label: String = "hold"
    }

    /**
     * Geared with nobody in sight: look for players.
     */
    data object Seek : Choice {
        override val label: String = "seek"
    }
}

/**
 * An opponent as [Plan] weighs it.
 *
 * @agent claude-opus-5-5
 */
internal class Rival(val id: Int, val name: String, val gear: GearScore, val way: WayCost, val busy: Boolean)

/**
 * A chest as [Plan] weighs it.
 *
 * @agent claude-opus-5-5
 */
internal class Cache(val pos: BlockPos, val way: WayCost)

/**
 * Picks what to go for among the chests and opponents, each scored as value minus way cost:
 * - chest: [CHEST_VALUE] + [CHEST_WEAK_VALUE] times the own weakness
 * - opponent: [OPPONENT_VALUE] + own gear score minus theirs, + [LOW_HP_VALUE] times their missing health,
 *   + [BUSY_VALUE] while another player stands next to them
 * - cost: [WayCost.value], so void on the straight way and climbs count
 *
 * While unarmed, chests within [UNARMED_CHEST_RANGE] come first. While every opponent outgears the agent by
 * [OUTGEARED_MARGIN], chests come first; with none left it holds when a refill is due within [HOLD_REFILL] seconds.
 * A chosen chest is kept until it is opened or out of reach, and only an opponent beating it by [SWITCH_MARGIN]
 * takes its place; any other choice gives way to one better by that margin. With exploring allowed, null means
 * looking for more chests or players.
 *
 * From LiquidBounce: [Plan.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Plan.kt#L60-L171), [Plan.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Plan.kt#L210-L238)
 *
 * @agent claude-opus-5-5
 */
internal class Plan {

    private class Option(val choice: Choice?, val score: Double, val reason: String)

    var choice: Choice? = null
        private set
    var reason: String = ""
        private set
    var score: Double = 0.0
        private set

    private var decidedAt = Long.MIN_VALUE / 2

    /**
     * Whether the last [next] changed the choice.
     */
    var changed: Boolean = false
        private set

    fun next(
        own: GearScore, rivals: List<Rival>, chests: List<Cache>, armed: Boolean, canExplore: Boolean,
        secondsToRefill: Int?, holdAt: Vec3, now: Long,
    ): Choice? {
        val current = choice
        changed = false
        if (now - decidedAt in 0 until DECIDE_TICKS && (current == null || isStill(current, rivals, chests))) {
            return current
        }
        decidedAt = now
        val next = decide(own, rivals, chests, armed, canExplore, secondsToRefill, holdAt)
        changed = next.choice?.label != current?.label || next.choice == null && next.reason != reason
        choice = next.choice
        reason = next.reason
        score = next.score
        return choice
    }

    fun clear() {
        choice = null
        reason = ""
        decidedAt = Long.MIN_VALUE / 2
    }

    private fun isStill(choice: Choice, rivals: List<Rival>, chests: List<Cache>) = when (choice) {
        is Choice.Loot -> chests.any { it.pos == choice.chest }
        is Choice.Hunt -> rivals.any { it.id == choice.id }
        is Choice.Hold, Choice.Seek -> true
    }

    private fun decide(
        own: GearScore, rivals: List<Rival>, chests: List<Cache>, armed: Boolean, canExplore: Boolean,
        secondsToRefill: Int?, holdAt: Vec3,
    ): Option {
        if (!armed) {
            keep(chests.filter { it.way.distance <= UNARMED_CHEST_RANGE }.map { loot(it, own, "unarmed") })
                ?.let { return it }
        }
        if (canExplore && rivals.isEmpty() && own.weakness <= GEARED_WEAKNESS) {
            return Option(Choice.Seek, 0.0, "geared nobody_in_sight")
        }

        val hunts = rivals.map { hunt(it, own) }
        val outgeared = rivals.isNotEmpty() && rivals.all { it.gear.score - own.score > OUTGEARED_MARGIN }
        val lootReason = if (outgeared) "outgeared" else "weakness=%.2f".format(java.util.Locale.ROOT, own.weakness)
        val loots = chests.map { loot(it, own, lootReason) }
        if (!outgeared) {
            return keep(loots + hunts) ?: Option(null, 0.0, "nothing")
        }
        return keep(loots) ?: when {
            secondsToRefill != null && secondsToRefill <= HOLD_REFILL ->
                Option(choice as? Choice.Hold ?: Choice.Hold(holdAt), 0.0, "outgeared refill_in=${secondsToRefill}s")
            canExplore -> Option(null, 0.0, "outgeared explore")
            else -> keep(hunts)?.let { Option(it.choice, it.score, "outgeared no_chests") }
                ?: Option(null, 0.0, "nothing")
        }
    }

    /**
     * The best option, or the current one when not beaten by [SWITCH_MARGIN]; a chest is never left for a chest.
     */
    private fun keep(options: List<Option>): Option? {
        val best = options.maxByOrNull { it.score } ?: return null
        val current = options.firstOrNull { it.choice?.label == choice?.label } ?: return best
        val committed = current.choice is Choice.Loot && best.choice is Choice.Loot
        return if (committed || best.score - current.score <= SWITCH_MARGIN) current else best
    }

    private fun loot(cache: Cache, own: GearScore, reason: String) =
        Option(Choice.Loot(cache.pos), CHEST_VALUE + CHEST_WEAK_VALUE * own.weakness - cache.way.value, reason)

    private fun hunt(rival: Rival, own: GearScore): Option {
        val advantage = own.score - rival.gear.score
        val missing = 1.0 - rival.gear.health
        val value = OPPONENT_VALUE + advantage + LOW_HP_VALUE * missing + if (rival.busy) BUSY_VALUE else 0.0
        val reason = "advantage=%.1f".format(java.util.Locale.ROOT, advantage) + if (rival.busy) " busy" else ""
        return Option(Choice.Hunt(rival.id, rival.name), value - rival.way.value, reason)
    }

    companion object {
        const val DECIDE_TICKS = 10
        const val CHEST_VALUE = 8.0
        const val CHEST_WEAK_VALUE = 32.0
        const val OPPONENT_VALUE = 20.0
        const val LOW_HP_VALUE = 12.0
        const val BUSY_VALUE = 8.0
        const val BUSY_RANGE = 5.0
        const val SWITCH_MARGIN = 6.0

        /**
         * Own weakness up to which the agent counts as geared, about full iron with an iron sword.
         */
        const val GEARED_WEAKNESS = 0.15
        const val OUTGEARED_MARGIN = 4.0
        const val UNARMED_CHEST_RANGE = 32.0
        const val HOLD_REFILL = 60
    }
}
