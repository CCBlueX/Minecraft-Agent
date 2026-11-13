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

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.MovementKeys
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.withKeys
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.Move
import net.ccbluex.agent.pathfinder.walk.Step
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

private const val RISE_MARGIN = 0.01
private const val JUMP_REACH = 1.5
private const val PREDICT_TICKS = 20

/**
 * Whether [mover] jumps on [step]: up a rise in reach, up ladders and out of water level with it.
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun wantsJump(step: Step, mover: Player): Boolean =
    wantsJump(step, mover.position(), mover.isStable, mover.isInWater)

/**
 * Whether a mover at [position], [stable] or [inWater], jumps on [step].
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L414-L427)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun wantsJump(step: Step, position: Vec3, stable: Boolean, inWater: Boolean): Boolean {
    val keepsLevel = step.to.y >= step.from.y
    return when (step.move) {
        Move.SWIM -> keepsLevel
        Move.CLIMB_UP -> true
        Move.CLIMB_DOWN, Move.FALL, Move.DOWNWARD -> false
        else -> {
            val rises = step.feet.y - position.y > Terrain.STEP_HEIGHT + RISE_MARGIN
            val inReach = position.horizontalDistanceTo(step.feet) < JUMP_REACH
            (rises && inReach && stable) || (inWater && keepsLevel)
        }
    }
}

/**
 * Whether walking on with [keys] and [movement] gets closer to where it aims, moved as the game moves the player.
 * A walk that the terrain holds up is given up right away instead of after the stuck ticks. Without
 * [BodyTraits.exactSimulation][net.ccbluex.agent.navigation.BodyTraits.exactSimulation], the walk is predicted with
 * [PlayerMotion] over the live terrain instead of the game's player movement.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L251-L273)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.getsAhead(keys: MovementKeys, frameYRot: Float, step: Step, movement: Movement): Boolean {
    val aim = movement.target?.takeUnless { movement.dive } ?: return true
    val closer = body.player.position().distanceTo(aim) - PROGRESS
    return if (body.traits.exactSimulation) {
        simulatesAhead(keys, frameYRot, step, movement, aim, closer)
    } else {
        movesAhead(keys, frameYRot, step, movement, aim, closer)
    }
}

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L251-L273)
 */
@Suppress("LongParameterList")
private fun NavigationContext.simulatesAhead(
    keys: MovementKeys,
    frameYRot: Float,
    step: Step,
    movement: Movement,
    aim: Vec3,
    closer: Double,
): Boolean {
    val sprint = movement.sprint
    val simulated = SimulatedPlayer.fromPlayer(
        body.player,
        SimulatedInput(keys.toInput(sprint), forceStayOnGroundSurface = movement.safeWalk),
        continuesTick = body.traits.navigatesInMovementTick,
        hooks = body.simulationHooks,
    )
    simulated.yRot = frameYRot

    repeat(PREDICT_TICKS) {
        simulated.tick()
        val position = simulated.position()
        if (position.distanceTo(aim) < closer) {
            return true
        }
        val next = centerInput(position, aim, frameYRot).withKeys(wantsJump(step, simulated), keys.sneak, sprint)
        simulated.input = SimulatedInput(next, forceStayOnGroundSurface = movement.safeWalk)
    }
    return false
}

/**
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L251-L273)
 */
@Suppress("LongParameterList")
private fun NavigationContext.movesAhead(
    keys: MovementKeys,
    frameYRot: Float,
    step: Step,
    movement: Movement,
    aim: Vec3,
    closer: Double,
): Boolean {
    val sprint = movement.sprint
    val state = PlayerMotion(Terrain.live(body.level), MotionTraits.capture(body.player))
    state.reset(body.player)
    var input = keys.toInput(sprint)

    repeat(PREDICT_TICKS) {
        state.tick(frameYRot, input.forwardImpulse, input.strafeImpulse, input.jump(), sprint, keys.sneak)
        val position = state.position
        if (position.distanceTo(aim) < closer) {
            return true
        }
        val jump = wantsJump(step, position, stable = state.onGround, inWater = false)
        input = centerInput(position, aim, frameYRot).withKeys(jump, keys.sneak, sprint)
    }
    return false
}

private val Input.forwardImpulse: Int
    get() = (if (forward()) 1 else 0) - (if (backward()) 1 else 0)

private val Input.strafeImpulse: Int
    get() = (if (left()) 1 else 0) - (if (right()) 1 else 0)
