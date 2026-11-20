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
package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.TestBody
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.minecraft.world.entity.Pose
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * A crossing held at the edge by a server that has yet to keep its block, with a host that starts sprinting on a
 * double tap of forward, as the game does.
 *
 * @agent claude-opus-5-5
 */
internal class CrossingTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `the press after the edge stop does not sprint the player off a rise turning aside`() {
        // Walking south out to the edge of a one-stack floor, as on the spiral of a 13 block wall on Grim, four ticks
        // after forward was pressed; the block beside never comes, so the player has to wait at the edge.
        val world = TestWorld()
        world.fill(0, 0, 0, 0, 0, 0)
        val start = Vec3(0.692, 1.0, 1.102)
        val player = TestLevel(world).player(start, 0f)
        val body = TestBody(player)
        val context = body.context()
        val actions = BlockActions(context)
        val stride = Stride()
        val crossing = Crossing(context, actions, StepCheck(context, actions), stride)
        val to = BlockPos(0, 2, 1)
        val steps = listOf(
            Step(
                BlockPos(0, 1, 0), to, Move.ASCEND, Vec3.atBottomCenterOf(to), emptyList(),
                listOf(BlockPos(0, 0, 1), BlockPos(0, 1, 1)), emptyList(), 1.0,
            ),
        )
        val motion = PlayerMotion(world.terrain(), MotionTraits.DEFAULT)
        motion.reset(start, Vec3(-0.067, PlayerMotion.RESTING.y, 0.080), onGround = true)
        player.deltaMovement = motion.deltaMovement.toMinecraft()
        player.setOnGround(true)

        var sprintTrigger = 3 // LocalPlayer.sprintTriggerTime
        var wasForward = true
        repeat(40) { tick ->
            stride.tick(context.body.player)
            assertTrue(crossing.takes(steps, 0))
            val keys = MovementKeys()
            crossing.tick(keys, player.yRot, steps, 0)
            body.looked?.let { player.yRot = it.yRot }

            // LocalPlayer.aiStep: forward pressed again within 7 ticks of the press before starts a sprint.
            val forward = keys.directionalInput.forward && !keys.sneak
            if (sprintTrigger > 0) sprintTrigger--
            var sprint = motion.isSprinting
            if (forward && !wasForward && motion.onGround && !sprint) {
                if (sprintTrigger > 0) sprint = true else sprintTrigger = 7
            }
            wasForward = forward

            val input = keys.directionalInput
            val ahead = (if (input.forward) 1 else 0) - (if (input.backward) 1 else 0)
            val strafe = (if (input.left) 1 else 0) - (if (input.right) 1 else 0)
            motion.tick(player.yRot, ahead, strafe, keys.jump, sprint, keys.sneak)
            player.setPos(motion.position.toMinecraft())
            player.deltaMovement = motion.deltaMovement.toMinecraft()
            player.setOnGround(motion.onGround)
            player.isSprinting = motion.isSprinting
            player.pose = if (motion.crouching) Pose.CROUCHING else Pose.STANDING
            player.tickCount++
            assertTrue(motion.position.y > 0.99, "Fell off the floor in tick $tick at ${player.position().toAgent()}")
        }
    }
}
