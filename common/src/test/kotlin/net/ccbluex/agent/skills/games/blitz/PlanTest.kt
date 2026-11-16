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
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.world.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * The Blitz chooser: chests against players by value minus way cost, with its margins and fallbacks.
 *
 * @agent claude-opus-5-5
 */
class PlanTest {

    private val naked = GearScore(0.0, 0.0, 1.0)
    private val geared = GearScore(15.0, 6.0, 1.0)
    private val mid = GearScore(8.0, 5.0, 1.0)

    private fun way(distance: Double, void: Int = 0) = WayCost(distance, 0.0, void, void.toDouble(), void)

    private fun chest(x: Int, distance: Double = x.toDouble(), void: Int = 0) = Cache(BlockPos(x, 64, 0), way(distance, void))

    private fun rival(id: Int, gear: GearScore, distance: Double = 10.0, busy: Boolean = false) =
        Rival(id, "p$id", gear, way(distance), busy)

    private fun Plan.pick(
        own: GearScore, rivals: List<Rival> = emptyList(), chests: List<Cache> = emptyList(), armed: Boolean = true,
        canExplore: Boolean = true, refill: Int? = null, now: Long = 0,
    ) = next(own, rivals, chests, armed, canExplore, refill, Vec3.ZERO, now)

    @Test
    fun weakAgentLootsBeforeHunting() {
        val choice = Plan().pick(naked, listOf(rival(1, naked)), listOf(chest(10)), canExplore = false)
        assertInstanceOf(Choice.Loot::class.java, choice)
    }

    @Test
    fun gearedAgentHuntsAWeakerPlayer() {
        val choice = Plan().pick(geared, listOf(rival(1, naked)), listOf(chest(10)))
        assertInstanceOf(Choice.Hunt::class.java, choice)
    }

    @Test
    fun voidMakesAChestCostlier() {
        val choice = Plan().pick(naked, chests = listOf(chest(10, void = 9), chest(-12)), canExplore = false)
        assertEquals(BlockPos(-12, 64, 0), (choice as Choice.Loot).chest)
    }

    @Test
    fun unarmedLootsNearChestsFirst() {
        val far = chest(40)
        val near = chest(20)
        val choice = Plan().pick(naked, listOf(rival(1, naked, distance = 3.0)), listOf(far, near), armed = false)
        assertEquals(near.pos, (choice as Choice.Loot).chest)
    }

    @Test
    fun outgearedHoldsForADueRefill() {
        val choice = Plan().pick(naked, listOf(rival(1, geared)), refill = 30, canExplore = false)
        assertInstanceOf(Choice.Hold::class.java, choice)
        assertNull(Plan().pick(naked, listOf(rival(1, geared)), refill = 300))
    }

    @Test
    fun gearedWithNobodyAroundSeeks() {
        assertSame(Choice.Seek, Plan().pick(geared))
    }

    @Test
    fun chestIsNotLeftForAnotherChest() {
        val plan = Plan()
        val first = plan.pick(mid, chests = listOf(chest(12)), canExplore = false)
        val again = plan.pick(mid, chests = listOf(chest(12), chest(2)), canExplore = false, now = Plan.DECIDE_TICKS.toLong())
        assertEquals((first as Choice.Loot).chest, (again as Choice.Loot).chest)
    }

    @Test
    fun aCloseBetterOptionDoesNotSwitch() {
        val plan = Plan()
        plan.pick(geared, listOf(rival(1, mid, distance = 20.0)))
        val next = plan.pick(geared, listOf(rival(1, mid, distance = 20.0), rival(2, mid, distance = 12.0)),
            now = Plan.DECIDE_TICKS.toLong())
        assertEquals("player p1", next?.label)
    }

    @Test
    fun blitzWaitsBeforeItsFirstTick() {
        val senses = object : Senses {
            override val state = RoundState.LOBBY
            override val countdownSeconds: Int? = null
            override val matchSeconds = 0
            override fun isOpponent(entity: Entity) = true
            override fun isTeammate(entity: Entity) = false
            override val isSpectator = false
            override val ownSpawn: Vec3? = null
            override val chests = emptyList<BlockPos>()
            override val refills = 0
            override val secondsToRefill: Int? = null
            override val kitDelaySeconds: Int? = 10
            override val secondsToDeathmatch: Int? = null
            override val isDeathmatchStarted = false
            override val compassTarget: BlockPos? = null
        }
        val game = Blitz(senses)
        assertEquals("Waiting", game.phase)
        assertSame(senses, game.senses)
    }
}
