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

package net.ccbluex.agent.v26_3.fabric.gametest.singleplayer

import net.ccbluex.agent.v26_3.fabric.gametest.autoWalk
import net.ccbluex.agent.v26_3.fabric.gametest.autoWalkStages
import net.ccbluex.agent.v26_3.fabric.gametest.blockSight
import net.ccbluex.agent.v26_3.fabric.gametest.elytraTravel
import net.ccbluex.agent.v26_3.fabric.gametest.flightNavigation
import net.ccbluex.agent.v26_3.fabric.gametest.keyTiming
import net.ccbluex.agent.v26_3.fabric.gametest.parkourAssist
import net.ccbluex.agent.v26_3.fabric.gametest.parkourEdge
import net.ccbluex.agent.v26_3.fabric.gametest.playerMotion
import net.ccbluex.agent.v26_3.fabric.gametest.playerSimulation
import net.ccbluex.agent.v26_3.fabric.gametest.rideNavigation
import net.ccbluex.agent.v26_3.fabric.gametest.tunnelGlide
import net.ccbluex.agent.v26_3.fabric.gametest.turning
import net.ccbluex.agent.v26_3.fabric.gametest.walkBlocked
import net.ccbluex.agent.v26_3.fabric.gametest.walkClimb
import net.ccbluex.agent.v26_3.fabric.gametest.walkEnding
import net.ccbluex.agent.v26_3.fabric.gametest.walkNavigation
import net.ccbluex.agent.v26_3.fabric.gametest.walkParkour
import net.ccbluex.agent.v26_3.fabric.gametest.walkParkourSpeed
import net.ccbluex.agent.v26_3.fabric.gametest.walkSneak
import net.ccbluex.agent.v26_3.fabric.gametest.walkSwim
import net.ccbluex.gametest.harness.SingleplayerGameTest
import net.ccbluex.gametest.harness.TestWorld

// The scenarios of the parent package's server game tests, played in a singleplayer world where no anticheat
// watches. They run when GAMETEST_SERVER_ANTICHEAT is unset.

/**
 * @agent claude-opus-5-5
 */
public class WalkNavigationGameTest : SingleplayerGameTest(walkNavigation)

/**
 * @agent claude-opus-5-5
 */
public class WalkBlockedGameTest : SingleplayerGameTest(walkBlocked)

/**
 * @agent claude-opus-5-5
 */
public class WalkClimbGameTest : SingleplayerGameTest(walkClimb)

/**
 * @agent claude-opus-5-5
 */
public class WalkSneakGameTest : SingleplayerGameTest(walkSneak)

/**
 * @agent claude-opus-5-5
 */
public class WalkSwimGameTest : SingleplayerGameTest(walkSwim)

/**
 * @agent claude-opus-5-5
 */
public class WalkParkourGameTest : SingleplayerGameTest(walkParkour)

/**
 * @agent claude-opus-5-5
 */
public class WalkParkourSpeedGameTest : SingleplayerGameTest(walkParkourSpeed)

/**
 * @agent claude-opus-5-5
 */
public class AutoWalkGameTest : SingleplayerGameTest(autoWalk)

/**
 * @agent claude-opus-5-5
 */
public class AutoWalkStagesGameTest : SingleplayerGameTest(autoWalkStages)

/**
 * @agent claude-opus-5-5
 */
public class ParkourAssistGameTest : SingleplayerGameTest(parkourAssist)

/**
 * @agent claude-opus-5-5
 */
public class ParkourEdgeGameTest : SingleplayerGameTest(parkourEdge)

/**
 * @agent claude-opus-5-5
 */
public class FlightNavigationGameTest : SingleplayerGameTest(flightNavigation)

/**
 * @agent claude-opus-5-5
 */
public class ElytraTravelGameTest : SingleplayerGameTest(elytraTravel)

/**
 * @agent claude-opus-5-5
 */
public class TunnelGlideGameTest : SingleplayerGameTest(TestWorld.VOID, tunnelGlide)

/**
 * @agent claude-opus-5-5
 */
public class RideNavigationGameTest : SingleplayerGameTest(rideNavigation)

/**
 * @agent claude-opus-5-5
 */
public class PlayerMotionGameTest : SingleplayerGameTest(playerMotion)

/**
 * @agent claude-opus-5-5
 */
public class PlayerSimulationGameTest : SingleplayerGameTest(playerSimulation)

/**
 * @agent claude-opus-5-5
 */
public class BlockSightGameTest : SingleplayerGameTest(blockSight)

/**
 * @agent claude-opus-5-5
 */
public class WalkEndingGameTest : SingleplayerGameTest(walkEnding)

/**
 * @agent claude-opus-5-5
 */
public class TurningGameTest : SingleplayerGameTest(turning)

/**
 * @agent claude-opus-5-5
 */
public class KeyTimingGameTest : SingleplayerGameTest(keyTiming)
