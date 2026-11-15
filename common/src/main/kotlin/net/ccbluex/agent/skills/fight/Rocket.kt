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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.skills.gear.GearScore
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import kotlin.math.abs

/**
 * An elytra flight on fireworks: a jump that opens the elytra, a rocket up and over the target, then the elytra taken
 * off for a free-fall mace dive onto it. With a spear, passes through the target at its height with the charge held
 * out, which hurts by the flight speed. Starts on the ground 4 to 40 blocks away with an elytra worn and fireworks.
 *
 * @agent claude-opus-5-5
 */
internal class Rocket : Technique(Skill.ROCKET, 50, 80) {

    private enum class Phase { TAKEOFF, BOOST, CRUISE, PASS, DIVE }

    private var phase = Phase.TAKEOFF
    private var since = 0L
    private var phaseAt = 0L
    private var boostUntil = 0L
    private var passing = false
    private var passes = 0

    /**
     * The hotbar slot the elytra went to for the dive; null while it is worn.
     */
    private var stowed: Int? = null

    override fun can(fight: BuiltInFight): Boolean {
        if (!fight.player.onGround() || fight.horizontalDistance() !in MIN_RANGE..MAX_RANGE) return false
        return fight.player.getItemBySlot(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && hasFirework(fight) &&
            (fight.hands.has { it.isOf(Items.MACE) } || fight.hands.has { it.isSpear })
    }

    private fun hasFirework(fight: BuiltInFight): Boolean =
        fight.hands.offHand.isOf(Items.FIREWORK_ROCKET) || fight.hands.has { it.isOf(Items.FIREWORK_ROCKET) }

    /**
     * A rocket from the off hand, else from the inventory into the main hand; boosts while it burns.
     */
    private fun fire(fight: BuiltInFight): Boolean {
        val offHand = fight.hands.offHand.isOf(Items.FIREWORK_ROCKET)
        if (!offHand && !fight.hands.hold { it.isOf(Items.FIREWORK_ROCKET) }) return false
        val hand = if (offHand) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val flight = fight.player.getItemInHand(hand).get(DataComponents.FIREWORKS)?.flightDuration() ?: 0
        if (!fight.body.useItem(hand)) return false
        boostUntil = fight.tick + BOOST_TICKS * (1 + flight)
        return true
    }

    private fun boosting(fight: BuiltInFight): Boolean = fight.tick < boostUntil

    /**
     * An empty hotbar slot for the elytra, never the one the hands swap items through.
     */
    private fun freeSlot(fight: BuiltInFight): Int? =
        (0 until Hands.SCRATCH_SLOT).firstOrNull { fight.hands.stack(it).isEmpty }

    private fun canDive(fight: BuiltInFight): Boolean =
        fight.hands.has { it.isOf(Items.MACE) } && freeSlot(fight) != null

    private fun phase(fight: BuiltInFight, phase: Phase) {
        this.phase = phase
        phaseAt = fight.tick
    }

    override fun begin(fight: BuiltInFight) {
        since = fight.tick
        boostUntil = 0
        passes = 0
        stowed = null
        // Spear passes whenever there is no mace to dive with
        passing = fight.hands.has { it.isSpear } && (!fight.hands.has { it.isOf(Items.MACE) } ||
            fight.random.nextDouble() < minOf(1.0, fight.skillLevel.technique))
        phase(fight, Phase.TAKEOFF)
        fight.moveControlled = true
        fight.attackHeld = true
        fight.jump = true
        fight.forward = 1f
        fight.sprint = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.moveControlled = true
        fight.attackHeld = true
        val target = fight.target ?: return false
        if (fight.tick - since > MAX_TICKS) return false
        val player = fight.player
        val elapsed = fight.tick - phaseAt
        val flying = player.isFallFlying && !player.onGround()
        val eye = player.eyePosition
        val chest = target.position().add(0.0, 1.0, 0.0)
        val towards = Aiming.rotation(eye, target.position()).yRot
        when (phase) {
            Phase.TAKEOFF -> {
                fight.forward = 1f
                fight.sprint = true
                fight.lookAt(ViewRotation(towards, CLIMB_X_ROT), 3.0)
                when {
                    flying -> {
                        if (!fire(fight)) return false
                        phase(fight, Phase.BOOST)
                    }
                    elapsed > TAKEOFF_TICKS -> return false
                    player.onGround() -> fight.jump = true
                    // A fresh jump press in the air opens the elytra
                    else -> fight.jump = elapsed % 2 == 0L
                }
            }
            Phase.BOOST, Phase.CRUISE -> {
                if (!flying) return elapsed <= 5
                val height = player.y - target.y
                val distance = fight.horizontalDistance()
                val spear = passing && passes < MAX_PASSES && fight.hands.has { it.isSpear }
                val mace = canDive(fight)
                if (phase == Phase.CRUISE) {
                    if (!spear && !mace) return false
                    // The charge needs half a second held out before it hurts: raised well before contact
                    if (spear && abs(height) < PASS_HEIGHT && distance in PASS_MIN..PASS_MAX) {
                        if (!boosting(fight) && hasFirework(fight)) fire(fight)
                        fight.hands.hold { it.isSpear }
                        fight.body.startUsingItem(InteractionHand.MAIN_HAND)
                        phase(fight, Phase.PASS)
                        return true
                    }
                    if (mace && height > DIVE_HEIGHT && distance < height * 0.8 + 2) {
                        dive(fight)
                        return true
                    }
                }
                var xRot = if (phase == Phase.BOOST || height < CLIMB_HEIGHT) CLIMB_X_ROT else LEVEL_X_ROT
                // A pass comes in at the target's height
                if (phase == Phase.CRUISE && spear) {
                    xRot = Aiming.rotation(eye, chest).xRot.coerceIn(-PASS_X_ROT, PASS_X_ROT)
                }
                fight.lookAt(ViewRotation(towards, xRot), 3.0)
                if (!boosting(fight)) {
                    if (phase == Phase.BOOST) {
                        phase(fight, Phase.CRUISE)
                    } else if (height < REFUEL_HEIGHT && hasFirework(fight)) {
                        fire(fight)
                    }
                }
            }
            Phase.PASS -> {
                if (!flying) return elapsed <= 5
                if (!fight.hands.hold { it.isSpear }) return false
                if (!fight.body.isUsingItem) fight.body.startUsingItem(InteractionHand.MAIN_HAND)
                fight.lookAt(Aiming.rotation(eye, chest), 4.0)
                // A main hand rocket would lower the charge: only the off hand refuels mid-pass
                if (!boosting(fight) && fight.hands.offHand.isOf(Items.FIREWORK_ROCKET)) fire(fight)
                // Through or past the target: away for the next pass, or up for a dive
                val ahead = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0)
                val past = ahead.dot(player.deltaMovement.multiply(1.0, 0.0, 1.0)) <= 0 && ahead.length() < PAST_RANGE
                if (past || elapsed > PASS_TICKS) {
                    fight.body.releaseUsingItem()
                    fight.hands.holdPrimary()
                    passes++
                    phase(fight, Phase.CRUISE)
                }
            }
            Phase.DIVE -> {
                if (!fight.hands.hold { it.isOf(Items.MACE) } || player.onGround()) return false
                fight.forward = 1f
                if (player.fallDistance > SMASH_FALL && fight.inReach(target) && fight.aimedAt(target)) {
                    fight.strike(target)
                    return false
                }
            }
        }
        return true
    }

    // A mace only smashes out of a glide: the elytra comes off into the hotbar, as a player swaps it in the inventory
    private fun dive(fight: BuiltInFight) {
        val slot = freeSlot(fight) ?: return
        fight.body.swapWithHotbar(CHEST_INDEX, slot)
        stowed = slot
        fight.hands.hold { it.isOf(Items.MACE) }
        phase(fight, Phase.DIVE)
    }

    override fun end(fight: BuiltInFight) {
        if (fight.body.isUsingItem) fight.body.releaseUsingItem()
        stowed?.let { slot ->
            if (fight.hands.stack(slot).isOf(Items.ELYTRA) && fight.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty) {
                fight.body.swapWithHotbar(CHEST_INDEX, slot)
            }
        }
        stowed = null
        fight.hands.holdPrimary()
    }

    private companion object {
        val CHEST_INDEX = GearScore.wornIndex(EquipmentSlot.CHEST)
        const val MIN_RANGE = 4.0
        const val MAX_RANGE = 40.0
        const val MAX_TICKS = 240
        const val TAKEOFF_TICKS = 8
        const val MAX_PASSES = 3

        /**
         * Ticks a rocket burns per point of flight duration, plus one.
         */
        const val BOOST_TICKS = 10
        const val CLIMB_X_ROT = -35f
        const val LEVEL_X_ROT = 5f
        const val PASS_X_ROT = 25f
        const val CLIMB_HEIGHT = 12.0
        const val REFUEL_HEIGHT = 8.0
        const val DIVE_HEIGHT = 6.0
        const val PASS_HEIGHT = 4.0
        const val PASS_MIN = 12.0
        const val PASS_MAX = 24.0
        const val PASS_TICKS = 40
        const val PAST_RANGE = 6.0
        const val SMASH_FALL = 1.5
    }
}
