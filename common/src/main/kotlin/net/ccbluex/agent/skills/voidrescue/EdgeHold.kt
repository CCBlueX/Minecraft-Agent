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

package net.ccbluex.agent.skills.voidrescue

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.agent.skills.AgentBody
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * How a tick's keys are held back at a void edge, from the least to the most.
 *
 * From LiquidBounce: [VoidSafety.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/VoidSafety.kt#L197-L213)
 *
 * @agent claude-opus-5-5
 */
internal enum class EdgeHold {
    NONE,
    SAFE_WALK,
    NO_JUMP,
    STOP,
    ;

    companion object {

        private const val SIM_TICKS = 16
        private const val DROP = 1.0

        /**
         * The least hold that keeps [body] from dropping into the void with [input] within [SIM_TICKS].
         */
        fun of(body: AgentBody, input: Input): EdgeHold = when {
            !fallsIntoVoid(body, input, safeWalk = false) -> NONE
            !fallsIntoVoid(body, input, safeWalk = true) -> SAFE_WALK
            !fallsIntoVoid(body, input.withoutJump(), safeWalk = true) -> NO_JUMP
            else -> STOP
        }

        fun Input.withoutJump() = Input(forward(), backward(), left(), right(), false, shift(), sprint())

        /**
         * Whether moving on with [input] drops the player more than [DROP] into the void. The game's own movement on
         * a copy of the player where the body allows it, else the lighter land model, where safe walk is a sneak.
         * [untilLanding] looks no further than the first ground the player comes down on: what the keys do after
         * landing is for the hold on the ground to judge.
         */
        fun fallsIntoVoid(
            body: AgentBody,
            input: Input,
            safeWalk: Boolean = false,
            untilLanding: Boolean = false,
        ): Boolean {
            val player = body.player
            // Sprint is decided after the keys; assuming it overestimates the way, which is the safe side
            val sprint = player.isSprinting || !input.shift() && input.forward()
            val keys = Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift(), sprint)
            if (body.traits.exactSimulation) {
                return simulateExactly(body, keys, safeWalk, untilLanding)
            }
            val motion = PlayerMotion(Terrain.live(body.level), MotionTraits.capture(player))
            motion.reset(player)
            return simulateOnLand(motion, body.inputYRot, keys, safeWalk, untilLanding) { isOverVoid(player, it) }
        }

        private fun simulateExactly(body: AgentBody, keys: Input, safeWalk: Boolean, untilLanding: Boolean): Boolean {
            val drop = body.player.y - DROP
            val simulated = SimulatedPlayer.fromPlayer(
                body.player,
                SimulatedInput(keys, forceStayOnGroundSurface = safeWalk),
                continuesTick = body.traits.navigatesInMovementTick,
                hooks = body.simulationHooks,
            )
            simulated.yRot = body.inputYRot
            repeat(SIM_TICKS) {
                simulated.tick()
                if (untilLanding && simulated.onGround()) return false
                if (simulated.y < drop && !simulated.onGround() && isOverVoid(body.player, simulated.position())) {
                    return true
                }
            }
            return false
        }

        /**
         * [fallsIntoVoid] on the land model, from where [motion] is now; [isOverVoid] tells a feet position over the
         * void.
         */
        fun simulateOnLand(
            motion: PlayerMotion,
            yRot: Float,
            keys: Input,
            safeWalk: Boolean,
            untilLanding: Boolean,
            isOverVoid: (Vec3) -> Boolean,
        ): Boolean {
            val drop = motion.y - DROP
            val forward = axis(keys.forward(), keys.backward())
            val strafe = axis(keys.left(), keys.right())
            repeat(SIM_TICKS) {
                motion.tick(yRot, forward, strafe, keys.jump(), keys.sprint(), keys.shift() || safeWalk)
                if (untilLanding && motion.onGround) return false
                if (motion.y < drop && !motion.onGround && isOverVoid(motion.position)) return true
            }
            return false
        }

        private fun axis(positive: Boolean, negative: Boolean) = when {
            positive == negative -> 0
            positive -> 1
            else -> -1
        }

        /**
         * No block to land on within [VoidSafety.VOID_DEPTH] below [player] standing at [pos].
         */
        fun isOverVoid(player: Player, pos: Vec3): Boolean {
            val level = player.level()
            val box = player.boundingBox.move(pos.subtract(player.position()))
            val bottom = maxOf(level.minY.toDouble(), pos.y - VoidSafety.VOID_DEPTH)
            return level.noBlockCollision(player, box.setMinY(bottom))
        }
    }
}
