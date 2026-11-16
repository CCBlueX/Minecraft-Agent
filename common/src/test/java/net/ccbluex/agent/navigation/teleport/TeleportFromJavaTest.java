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

package net.ccbluex.agent.navigation.teleport;

import net.ccbluex.agent.math.AABB;
import net.ccbluex.agent.math.BlockPos;
import net.ccbluex.agent.math.Vec3;
import net.ccbluex.agent.navigation.Teleporter;
import net.ccbluex.agent.navigation.TestBody;
import net.ccbluex.agent.pathfinder.MinecraftBootstrap;
import net.ccbluex.agent.pathfinder.TestLevel;
import net.ccbluex.agent.pathfinder.TestWorld;
import net.ccbluex.agent.pathfinder.route.Outcome;
import net.ccbluex.agent.pathfinder.teleport.Route;
import net.ccbluex.agent.pathfinder.terrain.ReachKt;
import net.ccbluex.agent.pathfinder.terrain.Terrain;
import net.ccbluex.agent.v26_3.Conversions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The teleport API as Java calls it: plain calls and constructors, no Kotlin function types.
 *
 * @agent claude-sonnet-5-5
 */
final class TeleportFromJavaTest {

    private static final Vec3 START = TestWorld.Companion.feet(0, 1, 0);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.INSTANCE.ensureInitialized();
    }

    private static final class Recorder implements Teleporter {
        final List<Vec3> sent = new ArrayList<>();

        @Override
        public void send(@NotNull Vec3 position, boolean onGround) {
            sent.add(position);
        }

        @Override
        public void land(@NotNull Vec3 position) {
        }
    }

    private static TestWorld flat() {
        return TestWorld.Companion.flat((x, z) -> true);
    }

    @Test
    void pacingsAreBuiltWithoutKotlinDefaults() {
        assertEquals(new Pacing.Batches(Pacing.DEFAULT_PACKETS_PER_TICK), new Pacing.Batches());
        assertEquals(2, new Pacing.Batches(2).getPacketsPerTick());

        Options defaults = new Options();
        assertEquals(new Pacing.Batches(), defaults.getPacing());
        assertFalse(defaults.getShouldClaimGroundOnDescent());

        Options burst = new Options(Options.DEFAULT_STEP_LENGTH, Pacing.Burst.INSTANCE, true);
        assertSame(Pacing.Burst.INSTANCE, burst.getPacing());
        assertTrue(burst.getShouldClaimGroundOnDescent());
        assertSame(Pacing.Instant.INSTANCE, new Options(7.0, Pacing.Instant.INSTANCE).getPacing());
        assertEquals(7.0, new Options(7.0).getStepLength());
    }

    @Test
    void reachPositionsTakeTheSupportFlagOrNot() {
        Terrain terrain = flat().terrain(false);
        AABB box = new AABB(4.0, 3.0, 0.0, 5.0, 4.0, 1.0);
        double eyeHeight = 1.62;

        Vec3 from = box.getCenter();

        List<BlockPos> supported = ReachKt.reachPositions(terrain, box, 4.0, from);
        assertFalse(supported.isEmpty());
        assertEquals(supported, ReachKt.reachPositions(terrain, box, 4.0, from, null, eyeHeight, true));
        assertTrue(supported.stream().allMatch(pos -> pos.getY() == 1));

        List<BlockPos> anywhere = ReachKt.reachPositions(terrain, box, 4.0, from, null, eyeHeight, false);
        assertTrue(anywhere.stream().anyMatch(pos -> pos.getY() > 1));
    }

    @Test
    void routesAreSentAndTheServerIsSyncedBack() {
        Recorder recorder = new Recorder();
        Player player = new TestLevel(flat()).player(START);
        Teleports teleports = new Teleports(new TestBody(player, false, recorder));
        Owner owner = () -> true;
        Route route = Route.Companion.plan(flat().terrain(false), List.of(START, TestWorld.Companion.feet(15, 1, 0)), 5.0);
        assertNotNull(route);

        Teleport run = teleports.dispatch(owner, route, Teleport.Finish.HOLD, new Options(5.0, Pacing.Burst.INSTANCE, false));
        assertNotNull(run);
        teleports.tick();
        assertEquals(3, recorder.sent.size());
        assertEquals(route.getDestination(), teleports.getServerPosition());

        teleports.tick();
        teleports.syncPosition();
        assertEquals(Conversions.toAgent(player.position()), recorder.sent.get(recorder.sent.size() - 1));
        assertNull(teleports.getServerPosition());
    }

    @Test
    void aTripDispatchesRoutesAndPollsWithoutASearch() {
        Recorder recorder = new Recorder();
        Player player = new TestLevel(flat()).player(START);
        TestBody body = new TestBody(player, false, recorder);
        Trip trip = new Trip(() -> true, body, new Teleports(body));
        Route route = Route.Companion.plan(flat().terrain(false), List.of(START, TestWorld.Companion.feet(8, 1, 0)), 5.0);
        assertNotNull(route);

        assertSame(Outcome.Searching.INSTANCE, trip.poll());
        Teleport run = trip.dispatch(route, Teleport.Finish.HOLD);
        assertNotNull(run);
        assertSame(run, trip.getRun());
        assertTrue(trip.isAway());
    }
}
