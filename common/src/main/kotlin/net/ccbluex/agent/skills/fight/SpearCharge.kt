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

import net.minecraft.world.InteractionHand
import net.minecraft.world.item.enchantment.Enchantments

/**
 * The spear's rhythm. Far: a jump and a Lunge stab mid-air towards the target, then the charge held out at a sprint,
 * which hurts by how fast the two close in. Close: the charge held while the stab recharges, the stab when it is
 * ready, which doubles the attack rate.
 *
 * @agent claude-opus-5-5
 */
internal class SpearCharge : Technique(Skill.SPEAR_CHARGE, 30, 40) {

    private enum class Phase { LUNGE, CHARGE, CLOSE }

    private var phase = Phase.CHARGE
    private var since = 0L
    private var phaseAt = 0L
    private var skilled = false

    override val interruptible: Boolean
        get() = phase == Phase.CLOSE

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        if (fight.body.isCombat1_8 || !fight.player.onGround() || !fight.hands.has { it.isSpear } ||
            !fight.hasLineOfSight(target)
        ) {
            return false
        }
        if (fight.horizontalDistance() in FAR_MIN..FAR_MAX) return true
        // Close: right after a stab, while it recharges
        return fight.distance() in CLOSE_MIN..CLOSE_MAX && fight.body.attackStrength < fight.skillLevel.strength
    }

    override fun begin(fight: BuiltInFight) {
        since = fight.tick
        phaseAt = fight.tick
        skilled = fight.random.nextDouble() < minOf(1.0, fight.skillLevel.technique)
        val distance = fight.horizontalDistance()
        fight.hands.hold { it.isSpear }
        val lunge = fight.hands.mainHand.enchantLevel(Enchantments.LUNGE) > 0
        when {
            distance < FAR_MIN -> phase = Phase.CLOSE
            skilled && distance in LUNGE_MIN..LUNGE_MAX && lunge -> {
                phase = Phase.LUNGE
                run(fight)
                fight.jump = true
            }
            else -> charge(fight)
        }
    }

    private fun run(fight: BuiltInFight) {
        fight.moveControlled = true
        fight.attackHeld = true
        fight.forward = 1f
        fight.sprint = true
    }

    private fun charge(fight: BuiltInFight) {
        phase = Phase.CHARGE
        phaseAt = fight.tick
        fight.body.startUsingItem(InteractionHand.MAIN_HAND)
    }

    override fun tick(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        val body = fight.body
        return when (phase) {
            Phase.LUNGE -> {
                run(fight)
                if (fight.player.onGround()) {
                    fight.jump = true
                    if (fight.tick - phaseAt > 4) charge(fight)
                } else if (fight.aimedAt(target) || fight.tick - phaseAt > 8) {
                    // Lunge pushes further in the air; the hold starts straight after
                    body.stab()
                    charge(fight)
                }
                true
            }
            Phase.CHARGE -> {
                run(fight)
                // Charged far enough: the stab takes over before the target is inside the spear's minimum reach
                fight.distance() >= fight.minReach() + 0.6 && fight.tick - phaseAt <= CHARGE_TICKS
            }
            Phase.CLOSE -> {
                if (!skilled || fight.distance() > CLOSE_RANGE || fight.tick - since > CLOSE_TICKS ||
                    !fight.hasLineOfSight(target)
                ) {
                    return false
                }
                fight.hands.hold { it.isSpear }
                if (body.attackStrength >= fight.skillLevel.strength) {
                    // The stab is ready: the charge comes down and the fighter's own attack stabs
                    if (body.isUsingItem) body.releaseUsingItem()
                } else {
                    fight.attackHeld = true
                    if (!body.isUsingItem) body.startUsingItem(InteractionHand.MAIN_HAND)
                }
                true
            }
        }
    }

    override fun end(fight: BuiltInFight) {
        if (fight.body.isUsingItem) fight.body.releaseUsingItem()
        fight.hands.holdPrimary()
        // Back to the close rhythm soon after a charge or a break in it
        if (phase != Phase.LUNGE) readyAt = minOf(readyAt, fight.tick + RHYTHM_TICKS)
    }

    private companion object {
        const val FAR_MIN = 6.0
        const val FAR_MAX = 14.0
        const val CLOSE_MIN = 2.0
        const val CLOSE_MAX = 4.5
        const val LUNGE_MIN = 7.0
        const val LUNGE_MAX = 10.0
        const val CLOSE_RANGE = 5.5
        const val CHARGE_TICKS = 50
        const val CLOSE_TICKS = 200
        const val RHYTHM_TICKS = 10
    }
}
