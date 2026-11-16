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

import net.ccbluex.agent.skills.shop.Currency
import net.ccbluex.agent.world.entity.Player
import java.util.EnumMap
import java.util.Random

/**
 * What a think weighs, gathered from the round. A null field means that activity cannot be taken up now.
 *
 * @agent claude-opus-5-5
 */
internal data class Facts(
    val seconds: Int = 0,
    val bedAlive: Boolean = true,
    /**
     * 0 on the own island, else the distance to its spawn over 60, at most 1.
     */
    val away: Double = 0.0,
    val current: Activity? = null,
    /**
     * A rusher on its way with its kit.
     */
    val setOut: Boolean = false,

    /**
     * The layer of the next defence block, null when none can go down or its blocks are sold nowhere.
     */
    val coverLayer: Int? = null,
    val hasCoverBlocks: Boolean = false,
    val affordsCoverBlocks: Boolean = false,
    val coverers: Int = 0,
    val openSpots: Int = 0,

    /**
     * How far the intruder is from the base; null without one.
     */
    val intruderDistance: Double? = null,
    val defenders: Int = 0,
    val intruders: Int = 0,
    val nobodyHome: Boolean = false,
    /**
     * A rusher within a few blocks of the bed it goes for.
     */
    val almostThere: Boolean = false,

    /**
     * The next wish's urgency; null without a wish it can buy or gather for.
     */
    val wishUrgency: Double? = null,
    val wishIsWool: Boolean = false,
    val affordsWish: Boolean = false,

    /**
     * Whether a diamond trip is open: an upgrade to save for, a generator, no rest after the last trip.
     */
    val diamondTrip: Boolean = false,
    val diamondMates: Int = 0,
    val enoughDiamonds: Boolean = false,
    val hasSword: Boolean = true,

    val emeraldTrip: Boolean = false,
    val emeraldMates: Int = 0,
    val emeralds: Int = 0,
    val hasIronSword: Boolean = false,
    val hasChainArmor: Boolean = false,

    /**
     * Whether the upgrade it can buy is the one it saves for; null when it can buy none.
     */
    val upgradeIsWanted: Boolean? = null,

    val hasRushTarget: Boolean = false,
    val rushTargetAvoided: Boolean = false,

    val hasPrey: Boolean = false,
    val hunters: Int = 0,

    /**
     * How far the enemy of a teammate in a fight is; null without such a fight.
     */
    val brawlDistance: Double? = null,
    val brawlMateIsReal: Boolean = false,

    val strategy: Strategy = Strategy.ADAPTIVE,
)

/**
 * The facts of the round a think weighs, with [intruder] the enemy at or coming for the base.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.facts(intruder: Player?): Facts {
    val spot = if (team.isBedAlive) plan.nextDefence(level, game, self.position(), ::isSkipping) else null
    val coverOffer = spot?.let { defenceOffer(it.fill) }
    val hasCoverBlocks = spot != null && slotOf(blocks(spot.fill)) >= 0
    val wish = nextWish()
    val wanted = wantedUpgrade()
    val buy = upgradeToBuy()
    val target = rushTarget()
    val brawl = threats.brawl(self, team)
    return Facts(
        seconds = senses.matchSeconds,
        bedAlive = team.isBedAlive,
        away = away(),
        current = activity,
        setOut = setOut,
        coverLayer = spot?.layer?.takeIf { coverOffer != null || hasCoverBlocks },
        hasCoverBlocks = hasCoverBlocks,
        affordsCoverBlocks = affords(coverOffer),
        coverers = plan.count(Activity.COVER, game),
        openSpots = plan.openSpots(level),
        intruderDistance = intruder?.position()?.distanceTo(threats.base(team)),
        defenders = plan.count(Activity.DEFEND, game),
        intruders = threats.intruders(self, team),
        nobodyHome = isNobodyHome(),
        almostThere = isAlmostThere(),
        wishUrgency = wish?.urgency,
        wishIsWool = wish != null && isWool(wish),
        affordsWish = wish != null && affords(offer(wish)),
        diamondTrip = wanted != null && tick >= (oreAgainAt[Currency.DIAMOND] ?: 0L) &&
            neutralGenerator(Currency.DIAMOND) != null,
        diamondMates = plan.count(Activity.DIAMONDS, game),
        enoughDiamonds = wanted != null && count(Currency.DIAMOND) >= wanted.price,
        hasSword = hasSword(),
        emeraldTrip = tick >= (oreAgainAt[Currency.EMERALD] ?: 0L) && neutralGenerator(Currency.EMERALD) != null,
        emeraldMates = plan.count(Activity.EMERALDS, game),
        emeralds = count(Currency.EMERALD),
        hasIronSword = hasIronSword(),
        hasChainArmor = hasChainArmor(),
        upgradeIsWanted = buy?.let { it.upgrade == wanted?.upgrade },
        hasRushTarget = target != null,
        rushTargetAvoided = target != null && (avoided[target.name] ?: 0L) > tick,
        hasPrey = tick >= huntAgainAt && threats.prey(self, team) != null,
        hunters = plan.count(Activity.HUNT, game),
        brawlDistance = brawl?.let { self.distanceTo(it.enemy).toDouble() },
        brawlMateIsReal = brawl != null && !senses.isBot(brawl.mate),
        strategy = strategy,
    )
}

/**
 * What each activity it could take up is worth, about 0 to 1.5; one it cannot do is left out.
 *
 * @agent claude-opus-5-5
 */
internal fun scores(f: Facts, t: Traits): Map<Activity, Double> {
    val scores = EnumMap<Activity, Double>(Activity::class.java)

    // The bed's cover: early, the more so the more careful the agent; teammates share it once a layer has enough
    // blocks for them, and an outer layer waits until the agent has or can buy its blocks
    f.coverLayer?.let { layer ->
        var score = (if (layer == 1) 0.5 else 0.2) + 0.55 * t.caution + if (f.seconds < 90) 0.3 else 0.0
        if (layer > 1 && !f.hasCoverBlocks && !f.affordsCoverBlocks) score -= 0.3
        if (f.coverers >= maxOf(1, f.openSpots / COVER_SHARE)) score -= 0.45
        if (f.intruderDistance != null && f.intruderDistance > GUARD_RANGE) score += 0.3
        scores[Activity.COVER] = score - 0.6 * f.away
    }

    f.intruderDistance?.let { distance ->
        var score = (if (f.bedAlive) 1.15 else 0.6) + (if (distance < GUARD_RANGE) 0.25 else 0.0) + 0.2 * t.caution
        score -= f.away * (1.4 - 0.6 * t.caution)
        // A rusher out on its bridge leaves the base to whoever is at home
        if (f.current == Activity.RUSH && f.setOut && f.away > 0.25 && !f.nobodyHome) score -= 0.3
        if (f.defenders >= f.intruders) score -= 0.4 + 0.2 * (f.defenders - f.intruders)
        if (f.current == Activity.RUSH && f.almostThere) score -= 0.2 + 0.5 * t.aggression
        scores[Activity.DEFEND] = score
    }

    f.wishUrgency?.let { urgency ->
        // Gear matters more as the round goes on, wool for bridges stays the same
        val later = if (f.wishIsWool) 0.0 else minOf(0.3, f.seconds / 600.0)
        val score = urgency + later + 0.1 * t.caution + if (f.affordsWish) 0.35 else -0.15
        scores[Activity.GEAR] = score - 0.5 * f.away
    }

    scores[Activity.GATHER] = 0.15 + 0.2 * t.greed - 0.4 * f.away

    if (f.diamondTrip) {
        var score = 0.2 + 0.6 * t.greed + if (f.seconds < 25) -0.35 else 0.0
        if (f.diamondMates > 0) score -= 0.35
        if (f.enoughDiamonds) score -= 0.6
        if (!f.hasSword && f.seconds > 60) score -= 0.15
        scores[Activity.DIAMONDS] = score
    }

    if (f.seconds > 150 && f.emeraldTrip) {
        var score = 0.1 + 0.5 * t.greed + if (f.seconds > 360) 0.15 else 0.0
        if (f.emeraldMates > 0) score -= 0.35
        if (f.emeralds >= ENOUGH_EMERALDS) score -= 0.5
        if (!f.hasIronSword && !f.hasChainArmor) score -= 0.2
        scores[Activity.EMERALDS] = score
    }

    f.upgradeIsWanted?.let { wanted ->
        scores[Activity.UPGRADE] = (if (wanted) 0.95 else 0.5) + 0.2 * t.greed - 0.4 * f.away
    }

    if (f.hasRushTarget) {
        var score = 0.25 + 0.7 * t.aggression
        if (f.seconds < 60) score += 0.25 * t.aggression - 0.05
        if (f.seconds > 300) score += 0.2
        if (f.seconds > 600) score += 0.3
        if (f.bedAlive && f.nobodyHome) score -= 0.35 * t.caution
        if (!f.hasSword && f.seconds > 90) score -= 0.15
        if (f.rushTargetAvoided) score -= 0.5
        if (f.current == Activity.RUSH && f.setOut) score += 0.15
        scores[Activity.RUSH] = score
    }

    if (f.hasPrey) {
        var score = 0.35 + 0.5 * t.aggression + if (!f.hasRushTarget) 0.45 else 0.0
        if (f.hasRushTarget && f.hunters > 0) score -= 0.2
        scores[Activity.HUNT] = score
    }

    f.brawlDistance?.let { distance ->
        scores[Activity.HELP] = 0.6 + 0.3 * (1 - t.caution) + 0.2 * t.aggression +
            (if (f.brawlMateIsReal) 0.25 else 0.0) - distance / 50
    }

    if (f.strategy == Strategy.HOLD_BASE) scores.keys.removeAll(AWAY_ACTIVITIES)
    return scores
}

/**
 * The activity a think settles on, with its final score: each score plus the agent's taste for it and noise, the
 * current activity a bonus for sticking with it, [finished] left out; GATHER when nothing is left. While [committed],
 * the current activity stays as long as it can still be taken up, whatever else scores.
 *
 * @agent claude-opus-5-5
 */
internal fun choose(
    scores: Map<Activity, Double>,
    taste: Map<Activity, Double>,
    t: Traits,
    current: Activity?,
    finished: Activity?,
    committed: Boolean,
    random: Random,
): Pair<Activity, Double> {
    if (committed && current != null) scores[current]?.let { return current to it + (taste[current] ?: 0.0) }
    var best = Activity.GATHER
    var bestScore = -Double.MAX_VALUE
    for ((activity, base) in scores) {
        if (activity == finished) continue
        var score = base + (taste[activity] ?: 0.0) + random.nextGaussian() * t.noise
        if (activity == current) score += 0.15 + 0.3 * t.patience
        if (score > bestScore) {
            best = activity
            bestScore = score
        }
    }
    return best to bestScore
}

/**
 * Open defence blocks of a layer per agent covering the bed.
 */
internal const val COVER_SHARE = 6

/**
 * Enemies this close to the bed are fought by whoever is at home.
 */
internal const val GUARD_RANGE = 12.0

private const val ENOUGH_EMERALDS = 6

/**
 * What a [Strategy.HOLD_BASE] agent never leaves home for.
 */
private val AWAY_ACTIVITIES = setOf(Activity.RUSH, Activity.HUNT, Activity.DIAMONDS, Activity.EMERALDS)
