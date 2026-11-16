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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.Throws
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.item.Items

/**
 * The built-in [VoidRescue]. On the ground it holds back moves that would drop the agent into the void, as little as
 * keeps it up ([EdgeHold]), except while the navigator bridges on purpose. That includes ticks without keys: a
 * player let go of at an edge, such as when a plan changes, slides on with its momentum. Once a fall into the void is under way,
 * knocked off or stepped off, it puts blocks under the fall ([Clutch]), else steers back towards the last safe ground
 * in the air and throws an ender pearl back to it or its spawn.
 *
 * From LiquidBounce: [VoidSafety.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/VoidSafety.kt#L67-L73)
 *
 * @agent claude-opus-5-5
 */
internal class VoidSafety : VoidRescue {

    private var safeGround: Vec3? = null
    private var falling = false
    private var pearlAt = 0L
    private val freeFall = FreeFall()

    override val isRescuing: Boolean
        get() = falling

    override fun tick(agent: Agent) {
        val body = agent.body
        val player = body.player
        freeFall.tick(player.y)
        if (isFlying(agent)) {
            endFall(agent)
            return
        }
        val position = player.position()
        if (player.onGround()) {
            if (!EdgeHold.isOverVoid(player, position)) safeGround = position
            endFall(agent)
            return
        }
        // Caught while still above the edge, even rising from a knockback, so a block below the feet can still go in.
        // A drop that lands, such as one onto a ledge the walk planned, stays the walk's: it steers the landing.
        if (!falling && EdgeHold.fallsIntoVoid(body, agent.navigator.input, untilLanding = true)) {
            falling = true
            agent.logger.debug("Falling into the void at {}", position)
        }
        if (!falling) return

        agent.takeMovement()
        if (!Clutch.tick(agent)) {
            safeGround?.let { agent.press(steering(player.position(), it, body.inputYRot), exact = true) }
            throwPearlBack(agent)
        }
    }

    override fun guard(agent: Agent, input: Input): Input {
        val body = agent.body
        val player = body.player
        if (falling || isFlying(agent) || !player.onGround() || agent.navigator.isBuilding) return input
        // No keys and no momentum: nothing moves the player off the edge
        if (input == Input.EMPTY && player.deltaMovement.horizontalDistanceSqr() < AT_REST) return input
        val hold = EdgeHold.of(body, input)
        if (hold == EdgeHold.NONE) return input
        body.safeWalk = true
        agent.logger.debug("Held at a void edge: {}", hold)
        return when (hold) {
            EdgeHold.NONE, EdgeHold.SAFE_WALK -> input
            EdgeHold.NO_JUMP -> with(EdgeHold) { input.withoutJump() }
            EdgeHold.STOP -> Input(false, false, false, false, false, input.shift, false)
        }
    }

    /**
     * Aims a pearl back towards the last safe ground or the spawn, whichever is nearer, onto whatever ground a throw
     * that way comes down on; throws once the sent rotation lands one.
     */
    private fun throwPearlBack(agent: Agent) {
        val body = agent.body
        val player = body.player
        val ground = safeGround ?: return
        if (agent.ticks < pearlAt || player.y > ground.y - PEARL_DROP) return
        val slot = player.inventory.slotOf(PEARL)
        if (slot < 0) return

        val at = player.position()
        val place = listOfNotNull(ground, agent.senses?.ownSpawn)
            .filter { horizontal(it, at) <= PEARL_RANGE }
            .minByOrNull { horizontal(it, at) }
            ?: return
        val players = body.players()
        val aim = Throws.pearlToGround(body.level, player, place, players) ?: return
        body.hold(slot)
        body.look(aim, LookPurpose.AIM)
        val lands = Throws.pearlLands(body.level, player, body.sentRotation, players)
        if (lands && body.useItem(InteractionHand.MAIN_HAND)) {
            pearlAt = agent.ticks + PEARL_TICKS
            agent.decide("Pearl back", 1.0, "towards ${BlockPos.containing(place)}")
        }
    }

    private fun endFall(agent: Agent) {
        if (!falling) return
        falling = false
        agent.body.releaseHotbarSlot()
    }

    private fun horizontal(a: Vec3, b: Vec3) = Math.hypot(a.x - b.x, a.z - b.z)

    companion object {
        /**
         * Blocks below a player searched for something to land on.
         */
        const val VOID_DEPTH = 40.0

        /**
         * Squared blocks a tick below which a player on the ground counts as standing still.
         */
        private const val AT_REST = 1.0E-6

        private const val PEARL_RANGE = 36.0
        private const val PEARL_TICKS = 40L

        /**
         * How far below the last safe ground a pearl is thrown, once a block below the feet no longer goes in.
         */
        private const val PEARL_DROP = 1.0
        private val PEARL = ItemMatch.of(Items.ENDER_PEARL)

        /**
         * The keys a player falling at [here] holds for air control towards [ground], moving along [yRot].
         */
        fun steering(here: Vec3, ground: Vec3, yRot: Float): Input = getDirectionalInputTowards(ground.subtract(here), yRot)
    }

    /**
     * Creative flight, an elytra glide, or the host's flight such as a fly module while it holds the player up: one
     * that is on while the player falls freely does not count, so the fall is caught as any other.
     */
    private fun isFlying(agent: Agent): Boolean {
        val player = agent.body.player
        val hostFlies = agent.body.isFlying && !freeFall.isFalling
        return player.abilities.flying || player.isFallFlying || hostFlies
    }
}
