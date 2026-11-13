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
import net.ccbluex.agent.skills.buildingBlocks
import net.ccbluex.agent.skills.count
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.skills.loot.Wanted
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB

private const val TEAMMATE_NEAR = 8.0
private const val BUSY_RANGE = 4.5
private const val STRAY_TEAMMATE = 16.0
private const val OCCUPIED = SkyWars.ISLAND_RADIUS + 3
private const val ITEM_RANGE = 10.0
private const val ITEM_HEIGHT = 4.0

/**
 * What the agent sees now, for a decision.
 *
 * @agent claude-opus-5-5
 */
internal fun Round.situation(): Situation {
    val position = player.position()
    val teammates = teammates()
    val enemies = enemies()
    val middle = middle()
    val canLoot = agent.skills.loot != null
    return Situation(
        position = position,
        island = island,
        health = player.health.toDouble(),
        gear = GearScore.own(player).equipment,
        blocks = player.inventory.buildingBlocks(),
        hasWeapon = GearScore.bestWeapon(player.inventory) > 0.0,
        pearls = player.inventory.count(ItemMatch.of(Items.ENDER_PEARL)),
        seconds = senses.matchSeconds,
        secondsToRefill = senses.secondsToRefill,
        doom = senses.isDoom && middle != null && !isAvoided(BlockPos.containing(middle)),
        middle = middle?.let { Place(it, WayCost.of(terrain, position, it)) },
        ownChests = if (canLoot) lootable(chestsNear(island, OWN_CHEST_RANGE), true) else emptyList(),
        middleChests = if (canLoot && middle != null) lootable(middleChests(), true) else emptyList(),
        raids = if (canLoot) raids(enemies) else emptyList(),
        enemies = enemies.filter { !isAvoided(it.id) }.map { foe(it, teammates) },
        strayTeammates = teammates.filter { it.distanceTo(player) > STRAY_TEAMMATE }
            .map { Place(it.position(), WayCost.of(terrain, position, it.position()), id = it.id) },
        digCandidate = digCandidate(),
        pickup = nearbyLoot()?.id,
        looted = openedAny,
        heldTicks = heldTicks,
        holdTicks = holdTicks,
    )
}

/**
 * @agent claude-opus-5-5
 */
private fun Round.foe(enemy: Player, teammates: List<Player>): Foe {
    val way = WayCost.of(terrain, player.position(), enemy.position())
    val busy = agent.body.players().any {
        it !== player && it !== enemy && it.isAlive && !it.isSpectator && it.distanceTo(enemy) < BUSY_RANGE
    }
    return Foe(
        id = enemy.id,
        position = enemy.position(),
        health = enemy.health.toDouble(),
        gear = GearScore.of(enemy, weapons).equipment,
        onGround = enemy.onGround(),
        reachable = way.bridgeBlocks == 0,
        way = way,
        fightingOther = busy,
        onIsland = onIsland(enemy.position()),
        teammatesNear = teammates.count { it.distanceTo(enemy) < TEAMMATE_NEAR },
    )
}

/**
 * Islands of other cages nobody stands on whose chests no real player touched, not the own one.
 *
 * @agent claude-opus-5-5
 */
private fun Round.raids(enemies: List<Player>): List<Place> {
    val position = player.position()
    return senses.cages.filter { cage ->
        cage.horizontalDistanceTo(island) >= SkyWars.ISLAND_RADIUS + 2 &&
            !isAvoided(BlockPos.containing(cage)) &&
            enemies.none { it.position().horizontalDistanceTo(cage) <= OCCUPIED }
    }.mapNotNull { cage ->
        val chests = lootable(chestsNear(cage, OWN_CHEST_RANGE), false)
        if (chests.isEmpty()) null else Place(cage, WayCost.of(terrain, position, cage), chests)
    }
}

/**
 * The nearest item lying close by that the agent wants and did not give up on.
 *
 * @agent claude-opus-5-5
 */
private fun Round.nearbyLoot(): ItemEntity? {
    val box = AABB.ofSize(player.position(), ITEM_RANGE * 2, ITEM_HEIGHT * 2, ITEM_RANGE * 2)
    return agent.body.level.getEntitiesOfClass(ItemEntity::class.java, box) {
        it.isAlive && !isAvoided(it.id) && Wanted.DEFAULT.wants(player, it.item)
    }.minByOrNull { it.distanceToSqr(player) }
}
