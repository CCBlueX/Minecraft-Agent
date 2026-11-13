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

package net.ccbluex.agent.v26_3.fabric.gametest

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.agent.v26_3.fabric.mixin.ClientInputAccessor
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.math.getInputYRot
import net.ccbluex.agent.math.isMoving
import net.ccbluex.agent.math.turned
import net.ccbluex.agent.navigation.walk.AssistedJump
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.pathfinder.walk.JumpTargets
import net.ccbluex.agent.pathfinder.walk.Jumps
import net.ccbluex.agent.pathfinder.walk.LeapInput
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import java.util.function.Consumer
import java.util.function.ToDoubleFunction

/**
 * LiquidBounce's Parkour module on the library's [AssistedJump]: jumps at the very edge of a block, and with [assist]
 * picks the block across the gap the player heads for, jumps at the tick that lands best, turns the jump towards it,
 * steers in the air and stops on small landings.
 *
 * Vanilla has one rotation, so the turn towards a jump is the player's own; [viewYRot] is where the player looks
 * otherwise, and the way the held keys lead is taken from it. Only on the client thread.
 *
 * From LiquidBounce: [ModuleParkour.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/ModuleParkour.kt#L57-L195), [AssistedJumpTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/test/kotlin/net/ccbluex/liquidbounce/utils/navigation/AssistedJumpTest.kt#L67-L119)
 *
 * @agent claude-opus-5-5
 */
internal object Parkour {

    private const val MAX_ANGLE = 35f
    private const val SAFE_FALL = 3
    private const val WATER_FALL = 23
    private const val MIN_SPRINT_FOOD = 6

    private val assisted = AssistedJump()

    private var assist = true
    private var aimYRot = Float.NaN

    /**
     * Where the player looks while no jump turns it.
     */
    var viewYRot = 0f

    private val tickHandler = Consumer<LocalPlayer>(::tick)

    fun enable(context: ClientGameTestContext, assist: Boolean = true) = context.onClient { mc ->
        viewYRot = checkNotNull(mc.player).yRot
        this.assist = assist
        GameTicks.afterInput.addIfAbsent(tickHandler)
    }

    fun disable(context: ClientGameTestContext) = context.onClient { mc ->
        if (GameTicks.afterInput.remove(tickHandler)) {
            mc.player?.yRot = viewYRot
        }
        assisted.reset()
        aimYRot = Float.NaN
    }

    /**
     * Right after the input read the keys, before the player moves.
     */
    private fun tick(player: LocalPlayer) {
        val held = player.input.keyPresses
        val yRot = turn(player)
        val keys = held.turned(viewYRot, yRot)

        val running = held.isMoving && !held.shift()
        val free = !player.isInWater && !player.onClimbable() && !player.abilities.flying
        val pressed = if (!assist || !running || !free) {
            release()
            if (running) jumpAtEdge(player, keys) else keys
        } else {
            val leap = assist(player, held, yRot)
            if (assisted.target == null) {
                aimYRot = Float.NaN
            }
            when (leap) {
                null -> jumpAtEdge(player, keys)
                else -> Input(
                    leap.forward > 0, leap.forward < 0, leap.strafe > 0, leap.strafe < 0,
                    leap.jump, leap.shift, held.sprint() || leap.sprint && leap.isMoving,
                )
            }
        }

        player.input.keyPresses = pressed
        (player.input as ClientInputAccessor).agentSetMoveVector(SimulatedInput(pressed).moveVector)
    }

    /**
     * Turns the player towards the jump it goes for, else to [viewYRot]; the yaw it moves with.
     */
    private fun turn(player: LocalPlayer): Float {
        if (assisted.target != null) {
            val yRot = assisted.aimYRot(player.position(), player.deltaMovement, player.onGround())
            if (!yRot.isNaN()) {
                aimYRot = yRot
            }
        }

        val wanted = if (assisted.target != null && !aimYRot.isNaN()) aimYRot else viewYRot
        // Whole turns are left out, as a player turning the mouse never makes them
        player.yRot += Mth.wrapDegrees(wanted - player.yRot)
        return player.yRot
    }

    /**
     * The keys the assist presses this tick, or null when there is nothing to assist.
     */
    private fun assist(player: LocalPlayer, held: Input, yRot: Float): LeapInput? {
        val terrain = Terrain.live(player.level())
        val abilities = abilities(player)
        val state = PlayerMotion(terrain, abilities.motion)
        state.reset(player)

        val keys = LeapInput(
            forward = (if (held.forward()) 1 else 0) - (if (held.backward()) 1 else 0),
            strafe = (if (held.left()) 1 else 0) - (if (held.right()) 1 else 0),
            jump = held.jump(),
            sprint = player.isSprinting,
        )
        val heading = getInputYRot(viewYRot, held)
        val jumps = Jumps(terrain, abilities, holdsYRot = true)
        val targets = JumpTargets(terrain, abilities.motion)
        return assisted.tick(state, yRot, heading, keys, MAX_ANGLE, jumps, targets)
    }

    /**
     * Jumps on the last tick before running off a block, but not off a slime bounce, which leaves it anyway.
     */
    private fun jumpAtEdge(player: LocalPlayer, keys: Input): Input {
        if (!player.onGround() || Minecraft.getInstance().options.keyJump.isDown || player.deltaMovement.y > 0.0) {
            return keys
        }

        val sprint = player.isSprinting || player.input.keyPresses.sprint()
        val input = SimulatedInput(keys.copy(jump = false, sprint = sprint))
        val simulation = SimulatedPlayer.fromPlayer(player, input, continuesTick = true)
        simulation.tick()
        if (simulation.onGround()) {
            return keys
        }
        return keys.copy(jump = true)
    }

    private fun release() {
        assisted.release()
        aimYRot = Float.NaN
    }

    private fun abilities(player: LocalPlayer) = WalkAbilities(
        canBreak = false,
        canPlace = false,
        placeableBlocks = 0,
        canParkour = true,
        riskyJumps = true,
        canSprint = player.foodData.foodLevel > MIN_SPRINT_FOOD,
        safeFall = SAFE_FALL,
        waterFall = WATER_FALL,
        breakTicks = ToDoubleFunction { Double.POSITIVE_INFINITY },
        avoid = LongOpenHashSet(),
        motion = MotionTraits.capture(player),
    )

    private val LeapInput.isMoving: Boolean
        get() = forward != 0 || strafe != 0

}
