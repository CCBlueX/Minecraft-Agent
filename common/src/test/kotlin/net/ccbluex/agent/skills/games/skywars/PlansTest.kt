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

import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.games.WayCost
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * The Sky Wars activity scores, with void and climbs on the way counted as extra distance.
 *
 * @agent claude-opus-5-5
 */
class PlansTest {

    private val balanced = Personality(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0, FightingStyle.NONE)
    private val rusher = balanced.copy(aggression = 0.9, caution = 0.1)

    private fun plainWay(distance: Double) = WayCost(distance, 0.0, 0, 0.0, 0)

    private fun voidWay(distance: Double, void: Int) = WayCost(distance, 0.0, void, void.toDouble(), void)

    private fun foe(
        id: Int = 1, distance: Double = 10.0, way: WayCost = plainWay(distance), gear: Double = 10.0,
        health: Double = 20.0, reachable: Boolean = way.bridgeBlocks == 0, busy: Boolean = false,
    ) = Foe(id, Vec3(distance, 0.0, 0.0), health, gear, true, reachable, way, busy, false, 0)

    private fun situation(
        health: Double = 20.0, gear: Double = 10.0, blocks: Int = 32, seconds: Int = 120, pearls: Int = 0,
        enemies: List<Foe> = emptyList(), ownChests: List<BlockPos> = emptyList(), looted: Boolean = false,
        middle: Place? = null, middleChests: List<BlockPos> = emptyList(), refill: Int? = null, doom: Boolean = false,
        raids: List<Place> = emptyList(), heldTicks: Int = 0,
    ) = Situation(
        position = Vec3.ZERO, island = Vec3.ZERO, health = health, gear = gear, blocks = blocks, hasWeapon = true,
        pearls = pearls, seconds = seconds, secondsToRefill = refill, doom = doom, middle = middle,
        ownChests = ownChests, middleChests = middleChests, raids = raids, enemies = enemies,
        strayTeammates = emptyList(), digCandidate = null, pickup = null, looted = looted, heldTicks = heldTicks,
        holdTicks = 600,
    )

    private fun List<Plan>.of(activity: Activity) = filter { it.activity == activity }

    private fun hunt(s: Situation, p: Personality, foe: Foe): Plan = Plans.hunt(s, p, foe) ?: fail("no hunt")

    @Test
    fun ownChestsComeFirst() {
        val options = Plans.score(situation(ownChests = listOf(BlockPos(1, 1, 1))), balanced)
        assertEquals(Activity.LOOT, options.maxBy { it.score }.activity)
    }

    @Test
    fun rusherLeavesTheLastChestsOnceArmed() {
        val chests = listOf(BlockPos(1, 1, 1))
        val calm = Plans.score(situation(ownChests = chests, looted = true), balanced).of(Activity.LOOT).single()
        val rush = Plans.score(situation(ownChests = chests, looted = true), rusher).of(Activity.LOOT).single()
        assertEquals(Plans.LOOT_SCORE, calm.score)
        assertEquals(Plans.LOOT_SCORE - 30, rush.score, 1e-9)
    }

    @Test
    fun retreatsWhenHurtWithAnEnemyNear() {
        val near = listOf(foe(distance = 8.0))
        assertTrue(Plans.score(situation(health = 8.0, enemies = near), balanced).of(Activity.RETREAT).isNotEmpty())
        assertTrue(Plans.score(situation(health = 18.0, enemies = near), balanced).of(Activity.RETREAT).isEmpty())
    }

    @Test
    fun middleOnlyWithBlocksForItsBridge() {
        val middle = Place(Vec3(30.0, 0.0, 0.0), voidWay(30.0, 20))
        val chests = listOf(BlockPos(30, 1, 0))
        assertTrue(Plans.score(situation(blocks = 32, middle = middle, middleChests = chests), balanced)
            .of(Activity.MIDDLE).isNotEmpty())
        assertTrue(Plans.score(situation(blocks = 8, middle = middle, middleChests = chests), balanced)
            .of(Activity.MIDDLE).isEmpty())
    }

    @Test
    fun middleWaitsForADueRefill() {
        val middle = Place(Vec3(30.0, 0.0, 0.0), plainWay(30.0))
        assertTrue(Plans.score(situation(middle = middle, refill = 10), balanced).of(Activity.MIDDLE).isNotEmpty())
        assertTrue(Plans.score(situation(middle = middle, refill = 40), balanced).of(Activity.MIDDLE).isEmpty())
    }

    @Test
    fun voidOnTheWayLowersAHunt() {
        val plain = hunt(situation(), balanced, foe(way = plainWay(20.0)))
        val overVoid = hunt(situation(), balanced, foe(way = voidWay(20.0, 10)))
        assertEquals(0.6 * 10 * WayCost.VOID_COST / WayCost.DISTANCE_COST, plain.score - overVoid.score, 1e-9)
    }

    @Test
    fun outOfBlocksHuntsOnlyWithAPearl() {
        val far = foe(way = voidWay(20.0, 15))
        assertNull(Plans.hunt(situation(blocks = 4), balanced, far))
        assertTrue(hunt(situation(blocks = 4, pearls = 2), balanced, far).pearl)
    }

    @Test
    fun busyEnemiesDrawOpportunists() {
        val opportunist = balanced.copy(opportunism = 1.0)
        val alone = hunt(situation(), opportunist, foe())
        val busy = hunt(situation(), opportunist, foe(busy = true))
        assertEquals(30.0, busy.score - alone.score, 1e-9)
        assertTrue(busy.thirdParty)
    }

    @Test
    fun holdNeedsTwoEnemiesAndBudget() {
        val two = listOf(foe(id = 1, distance = 40.0), foe(id = 2, distance = 50.0))
        assertTrue(Plans.score(situation(enemies = two), balanced).of(Activity.HOLD).isNotEmpty())
        assertTrue(Plans.score(situation(enemies = two.take(1)), balanced).of(Activity.HOLD).isEmpty())
        assertTrue(Plans.score(situation(enemies = two, heldTicks = 600), balanced).of(Activity.HOLD).isEmpty())
    }

    @Test
    fun doomBeatsEverything() {
        val middle = Place(Vec3(40.0, 0.0, 0.0), plainWay(40.0))
        val options = Plans.score(situation(doom = true, middle = middle, ownChests = listOf(BlockPos.ZERO)), balanced)
        assertEquals(Activity.DOOM, options.maxBy { it.score }.activity)
    }

    @Test
    fun commitKeepsACloseCurrentPlan() {
        val current = Plan(Activity.HUNT, 50.0, targetId = 1)
        val other = Plan(Activity.HUNT, 55.0, targetId = 2)
        val calm = balanced.copy(whim = 0.0)
        val picked = Plans.pick(listOf(other, current.copy()), current, calm.whim, Random(1), chestUnderway = false)
        assertEquals(1, picked.targetId)
    }

    @Test
    fun chestUnderwayIsNotLeftForAnotherChest() {
        val current = Plan(Activity.LOOT, 60.0)
        val middle = Plan(Activity.MIDDLE, 100.0, point = Vec3(30.0, 0.0, 0.0))
        val hunt = Plan(Activity.HUNT, 100.0, targetId = 3)
        val kept = Plans.pick(listOf(current.copy(), middle), current, 0.0, Random(1), chestUnderway = true)
        assertEquals(Activity.LOOT, kept.activity)
        val left = Plans.pick(listOf(current.copy(), middle, hunt), current, 0.0, Random(1), chestUnderway = true)
        assertEquals(Activity.HUNT, left.activity)
        val free = Plans.pick(listOf(current.copy(), middle), current, 0.0, Random(1), chestUnderway = false)
        assertFalse(free.activity == Activity.LOOT)
    }

    @Test
    fun nothingToDoHolds() {
        assertEquals(Activity.HOLD, Plans.pick(emptyList(), null, 1.0, Random(1), false).activity)
    }
}
