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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Team spawns and beds as 1.8 servers show them: every bed arrives as a red bed, and the start is announced before
 * the teleport from the waiting lobby.
 *
 * @agent claude-sonnet-5-5
 */
internal class BedClaimsTest {

    /**
     * Both halves of the eight beds of a map, as a round recorded them.
     */
    private val beds = listOf(
        BlockPos(-91, 70, -47), BlockPos(-90, 70, -47), BlockPos(90, 70, 47), BlockPos(91, 70, 47),
        BlockPos(42, 70, 96), BlockPos(90, 70, -47), BlockPos(91, 70, -47), BlockPos(-42, 70, -96),
        BlockPos(-42, 70, -95), BlockPos(-91, 70, 47), BlockPos(-90, 70, 47), BlockPos(-42, 70, 96),
        BlockPos(42, 70, -96), BlockPos(42, 70, -95),
    )
    private val ownBed = setOf(BlockPos(-42, 70, -96), BlockPos(-42, 70, -95))
    private val island = Vec3(-42.0, 72.0, -110.0)

    /**
     * Every bed is red; the teams are named by their scoreboard colour.
     */
    private val redOnly: (String, BlockPos) -> Boolean = { team, _ -> team == "Red" }

    @Test
    fun theOwnBedOfATeamThatIsNotRedIsTheOneBySpawnWhenEveryBedShowsRed() {
        val claims = claimBeds(beds, listOf("Green"), byColour = false, redOnly, { island }, emptyMap())
        assertTrue(claims["Green"] in ownBed, "claimed ${claims["Green"]}")
    }

    @Test
    fun aRedTeamOnSuchAServerTakesItsOwnBedToo() {
        val claims = claimBeds(beds, listOf("Red"), byColour = false, redOnly, { island }, emptyMap())
        assertTrue(claims["Red"] in ownBed, "claimed ${claims["Red"]}")
    }

    @Test
    fun anotherTeamGetsTheBedByItsPlayersNeverTheOtherHalfOfTheOwnBed() {
        val seen = mapOf("Green" to island, "Blue" to Vec3(-42.0, 71.0, -93.0), "Pink" to Vec3(88.0, 71.0, 50.0))
        val claims = claimBeds(beds, listOf("Green", "Blue", "Pink"), byColour = false, redOnly, seen::get, emptyMap())
        assertTrue(claims["Green"] in ownBed)
        assertNull(claims["Blue"], "the other half of the own bed is not Blue's")
        assertTrue(claims["Pink"] in setOf(BlockPos(90, 70, 47), BlockPos(91, 70, 47)))
    }

    @Test
    fun aClaimedBedStaysItsTeamsWhenTheTeamWalkedOff() {
        val before = mapOf("Pink" to BlockPos(90, 70, 47))
        val claims = claimBeds(beds, listOf("Pink"), byColour = false, redOnly, { null }, before)
        assertEquals(BlockPos(90, 70, 47), claims["Pink"])
    }

    @Test
    fun colouredBedsGoByColourEvenWhenAnotherBedIsCloser() {
        val two = listOf(BlockPos(0, 70, 5), BlockPos(0, 70, 40))
        val claims = claimBeds(
            two, listOf("Green"), byColour = true, { _, bed -> bed.z == 40 }, { Vec3(0.0, 71.0, 0.0) }, emptyMap(),
        )
        assertEquals(BlockPos(0, 70, 40), claims["Green"])
    }

    @Test
    fun theSpawnIsTheIslandAfterTheTeleportFromTheWaitingLobby() {
        val lobby = Vec3(0.0, 145.0, 0.0)
        assertNull(islandSpawn(lobby, lobby, 0), "still in the lobby right after the start")
        assertEquals(island, islandSpawn(lobby, island, 0))
    }

    @Test
    fun theSpawnIsWhereTheRoundStartedOnAServerThatTeleportsFirst() {
        val spawn = Vec3(-73.5, 69.0, -39.5)
        assertNull(islandSpawn(spawn, spawn, 1))
        assertEquals(spawn, islandSpawn(spawn, spawn.add(1.0, 0.0, 0.0), 2))
    }
}
