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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.count
import net.ccbluex.agent.skills.fight.OutnumberedPlan
import net.ccbluex.agent.skills.games.WayCost
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import kotlin.math.abs

/**
 * Whom a [Round] fights at once, whatever it was doing: whoever hit it lately or comes close, as long as it can walk
 * there; whoever is on a teammate; someone out on a bridge within a shot, or across a gap for an agent that likes
 * shooting. A shootout across the void nobody wins is paused, and a fight in which neither side takes damage is
 * left for a while.
 *
 * @agent claude-opus-5-5
 */
internal class Threats(private val round: Round) {

    private val stalled = HashMap<Int, Long>()
    private var shootingSince = -1L
    private var shootingPausedUntil = 0L
    private var fightTarget: Player? = null
    private var fightSince = 0L
    private var ownHealth = 0f
    private var theirHealth = 0f

    fun pick(): Player? {
        val now = round.ticks
        var enemy = select(now)
        val fight = round.agent.skills.fight
        if (enemy == null && fight?.isBusy == true) enemy = fight.target as? Player

        val player = round.player
        if (enemy != null && enemy.position().horizontalDistanceTo(player.position()) > MELEE) {
            if (shootingSince < 0) shootingSince = now
            if (now - shootingSince > SHOOTING_TICKS && fight?.isBusy != true) {
                shootingPausedUntil = now + SHOOTING_TICKS
                shootingSince = -1
                enemy = null
            }
        } else {
            shootingSince = -1
        }
        if (enemy != null && isStale(enemy, now)) {
            stalled[enemy.id] = now + Round.AVOID_TICKS
            enemy = null
        }
        if (enemy == null) fightTarget = null
        return enemy
    }

    fun forgetStalled() {
        stalled.values.removeIf { it <= round.ticks }
    }

    fun isStalled(enemy: Player): Boolean = (stalled[enemy.id] ?: 0L) > round.ticks

    private fun select(now: Long): Player? {
        val player = round.player
        val canShoot = canShoot(player)
        val focus = (round.outnumbered.plan as? OutnumberedPlan.Focus)?.target
        round.targets.lastAttacker(ATTACKER_TICKS)?.let { attacker ->
            if (attacker.isAlive && round.senses.isOpponent(attacker) && attacker.distanceToSqr(player) < ATTACKER_RANGE_SQR &&
                (canShoot || isReachable(attacker)) && round.outnumbered.allows(attacker)
            ) {
                return attacker
            }
        }
        if (focus != null && focus.isAlive && focus.distanceTo(player) <= FOCUS_RANGE) return focus

        val teammates = round.teammates()
        val ranged = round.agent.personality.ranged
        return round.enemies()
            .filter { !isStalled(it) && round.outnumbered.allows(it) }
            .map { it to it.position().horizontalDistanceTo(player.position()) }
            .filter { (enemy, distance) ->
                val close = distance <= MELEE && abs(enemy.y - player.y) < CLOSE_HEIGHT && isReachable(enemy)
                val helping = distance < HELP_RANGE && teammates.any { it.distanceTo(enemy) < HELP_NEAR } &&
                    isReachable(enemy)
                val bridging = !round.onIsland(enemy.position())
                val shot = !close && now >= shootingPausedUntil && distance > MELEE && distance <= RANGED &&
                    canShoot && player.hasLineOfSight(enemy) &&
                    if (bridging) distance <= 12 + 16 * ranged else ranged > 0.6 && distance < RANGED_ISLAND
                close || helping || shot
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun isReachable(enemy: Player): Boolean =
        WayCost.of(round.terrain, round.player.position(), enemy.position()).bridgeBlocks == 0

    private fun isStale(enemy: Player, now: Long): Boolean {
        val own = round.player.health
        val theirs = enemy.health
        if (enemy !== fightTarget || own < ownHealth - HEALTH_CHANGE || theirs < theirHealth - HEALTH_CHANGE) {
            fightTarget = enemy
            fightSince = now
        }
        ownHealth = own
        theirHealth = theirs
        return now - fightSince > STALE_TICKS
    }

    private companion object {
        const val MELEE = 5.0
        const val CLOSE_HEIGHT = 3.0
        const val RANGED = 28.0
        const val RANGED_ISLAND = 18.0
        const val HELP_RANGE = 18.0
        const val HELP_NEAR = 6.0
        const val FOCUS_RANGE = 8.0
        const val ATTACKER_TICKS = 100
        const val ATTACKER_RANGE_SQR = 64.0
        const val SHOOTING_TICKS = 200
        const val STALE_TICKS = 200
        const val HEALTH_CHANGE = 0.1f

        fun canShoot(player: Player): Boolean {
            val inventory = player.inventory
            if (inventory.count(ItemMatch.of(Items.SNOWBALL, Items.EGG)) > 0) return true
            return inventory.count(ItemMatch.of(Items.BOW)) > 0 && inventory.count(ItemMatch.of(Items.ARROW)) > 0
        }
    }
}
