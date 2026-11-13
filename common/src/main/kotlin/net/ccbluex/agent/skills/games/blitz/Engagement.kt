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

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.skills.fight.OutnumberedPlan
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.gear.GearScore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val FIGHT_RANGE = 6.0
private const val FIGHT_MARGIN = 2.0
private const val CALM_TICKS = 20
private const val RETREAT_RADIUS = 1.0
private const val OPPONENT_RADIUS = 3.0

/**
 * Enough armor and at least a stone sword to go after players.
 */
private const val MIN_ARMOR = 8.0
private const val MIN_WEAPON = 5.0
private const val WEAKER_MARGIN = 4.0

/**
 * Without a weapon: how close a player may get before it is hit back, and one that counts as coming.
 */
private const val CORNERED_RANGE = 2.0
private const val CLOSE_RANGE = 3.0

/**
 * Blocks per tick towards the agent.
 */
private const val APPROACH_SPEED = 0.05
private const val PICKUP_RANGE = 8.0
private const val PICKUP_RADIUS = 0.5

/**
 * @agent claude-opus-5-5
 */
internal val Round.isArmed: Boolean
    get() = GearScore.bestWeapon(player.inventory) > 0.0

/**
 * @agent claude-opus-5-5
 */
internal val Round.isEquipped: Boolean
    get() = isArmed && GearScore.own(player).let { it.armor >= MIN_ARMOR && it.weapon >= MIN_WEAPON }

/**
 * @agent claude-opus-5-5
 */
internal fun Round.isWeaker(other: Player): Boolean =
    GearScore.of(other, weapons).score + WEAKER_MARGIN <= GearScore.own(player).score

/**
 * The opponents [Plan] weighs, those matching [filter] that were not given up on.
 *
 * @agent claude-opus-5-5
 */
internal fun Round.rivals(filter: (Player) -> Boolean): List<Rival> {
    val terrain = Terrain.live(agent.body.level)
    val position = player.position()
    val players = agent.body.players()
    return targets.around().filter { it.isAlive && isReachable(playerLabel(it)) && filter(it) }.map { rival ->
        val busy = players.any {
            it !== rival && it !== player && !it.isSpectator && it.distanceTo(rival) <= Plan.BUSY_RANGE
        }
        Rival(rival.id, rival.scoreboardName, GearScore.of(rival, weapons), WayCost.of(terrain, position, rival.position()), busy)
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun Round.caches(): List<Cache> {
    val loot = agent.skills.loot ?: return emptyList()
    val terrain = Terrain.live(agent.body.level)
    val position = player.position()
    return loot.chests.unopened().filter { isReachable("chest ${it.x},${it.y},${it.z}") }
        .map { Cache(it, WayCost.of(terrain, position, Vec3.atCenterOf(it))) }
}

/**
 * @agent claude-opus-5-5
 */
internal fun Round.choose(rivals: List<Rival>, caches: List<Cache>, armed: Boolean, canExplore: Boolean): Choice? {
    val own = GearScore.own(player)
    val choice = plan.next(own, rivals, caches, armed, canExplore, senses.secondsToRefill, player.position(), ticks)
    if (plan.changed) agent.decide("plan " + (choice?.label ?: "none"), plan.score, plan.reason)
    return choice
}

/**
 * @agent claude-opus-5-5
 */
internal fun Round.chase(choice: Choice.Hunt) {
    val prey = agent.body.level.getEntity(choice.id) as? Player ?: return resetTarget()
    walkTo(choice.label, prey.position(), OPPONENT_RADIUS)
}

/**
 * Fights the closest opponent in reach, retreats when outnumbered, or keeps away while under-equipped unless the
 * opponent is clearly weaker; true while a fight or retreat goes on.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L678-L722)
 *
 * @agent claude-opus-5-5
 */
internal fun Round.engage(): Boolean {
    (outnumbered.plan as? OutnumberedPlan.Retreat)?.let {
        stopFight("outnumbered")
        agent.walkTo(it.point, RETREAT_RADIUS)
        goal = "Backing away from a group"
        return true
    }
    val fight = agent.skills.fight
    if (!isArmed || fight == null) return evade()
    val equipped = phase == Phase.DEATHMATCH || isEquipped
    val current = targets.around()
        .filter {
            val range = if (it === opponent) FIGHT_RANGE + FIGHT_MARGIN else FIGHT_RANGE
            it.isAlive && it.distanceTo(player) <= range && (equipped || isWeaker(it)) && outnumbered.allows(it)
        }
        .minByOrNull { it.distanceToSqr(player) }
    if (current == null && !equipped) return evade()
    evading = false

    if (current == null) {
        val last = opponent ?: return false
        if (++calmTicks >= CALM_TICKS) {
            stopFight("calm")
            return false
        }
        fight.engage(last)
        return true
    }
    calmTicks = 0
    if (current !== opponent) startFight(current)
    fight.engage(current)
    goal = "Fighting " + current.scoreboardName
    return true
}

/**
 * While unarmed or under-equipped nobody is fought: players coming close are walked away from, and only one too
 * close to get away from is hit back. False while nobody comes.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L729-L756)
 *
 * @agent claude-opus-5-5
 */
private fun Round.evade(): Boolean {
    val threats = targets.around().filter { isComing(it) }
    val closest = threats.minByOrNull { it.distanceToSqr(player) }
    val away = if (threats.isEmpty()) null else outnumbered.retreatPoint(agent, threats)
    val fight = agent.skills.fight
    if (closest != null && away == null && fight != null && closest.distanceTo(player) <= CORNERED_RANGE) {
        if (closest !== opponent) startFight(closest)
        calmTicks = 0
        fight.engage(closest)
        goal = "Cornered by " + closest.scoreboardName
        return true
    }
    stopFight("under-equipped")
    if (closest == null || away == null) {
        evading = false
        return false
    }
    if (!evading) {
        evading = true
        agent.decide("evade", 0.0, playerLabel(closest) + if (isArmed) " under-equipped" else " unarmed")
    }
    agent.walkTo(away, RETREAT_RADIUS)
    goal = "Keeping away from " + closest.scoreboardName
    return true
}

/**
 * Within fight range and walking towards the agent, or already close.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L759-L767)
 *
 * @agent claude-opus-5-5
 */
private fun Round.isComing(other: Player): Boolean {
    val distance = other.distanceTo(player).toDouble()
    if (distance > FIGHT_RANGE) return false
    val step = other.position().subtract(other.xo, other.yo, other.zo)
    val toAgent = player.position().subtract(other.position()).normalize()
    return distance <= CLOSE_RANGE || step.dot(toAgent) >= APPROACH_SPEED
}

/**
 * From LiquidBounce: [GameBotBedWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBedWars.kt#L833-L840)
 *
 * @agent claude-opus-5-5
 */
private fun Round.startFight(target: Player) {
    opponent = target
    agent.listener.engaged(target)
    agent.decide("fight", GearScore.of(target, weapons).score, playerLabel(target))
}

/**
 * @agent claude-opus-5-5
 */
internal fun Round.stopFight(reason: String) {
    if (opponent == null) return
    opponent = null
    calmTicks = 0
    agent.skills.fight?.disengage()
    agent.logger.debug("Fight over: {}", reason)
    resetTarget()
}

/**
 * Walks over a weapon or armor lying within [PICKUP_RANGE] that beats the agent's own.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L770-L781)
 *
 * @agent claude-opus-5-5
 */
internal fun Round.pickUpGear(): Boolean {
    val box = AABB.ofSize(player.position(), PICKUP_RANGE * 2, PICKUP_RANGE * 2, PICKUP_RANGE * 2)
    val item = agent.body.level.getEntitiesOfClass(ItemEntity::class.java, box) {
        it.isAlive && it.position().closerThan(player.position(), PICKUP_RANGE) && isReachable(itemLabel(it)) &&
            GearScore.isUpgrade(player.inventory, it.item)
    }.minByOrNull { it.distanceToSqr(player) } ?: return false
    walkTo(itemLabel(item), item.position(), PICKUP_RADIUS)
    return true
}

/**
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L791-L791)
 *
 * @agent claude-opus-5-5
 */
private fun itemLabel(item: ItemEntity) = "item ${BuiltInRegistries.ITEM.getKey(item.item.item).path}#${item.id}"

/**
 * From LiquidBounce: [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L918-L918)
 *
 * @agent claude-opus-5-5
 */
internal fun playerLabel(player: Player): String = "player ${player.scoreboardName}"
