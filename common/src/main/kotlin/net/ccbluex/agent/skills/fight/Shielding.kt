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

import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.item.Items
import kotlin.math.acos

/**
 * Shield, axe and totem: raising the own shield, breaking the target's, and a new totem after a pop.
 *
 * @agent claude-opus-5-5
 */
internal object Shielding {

    /**
     * Vanilla keeps an axed shield down for 5 seconds; a little less so the fighter is not late.
     */
    const val STUN_TICKS = 95

    fun hasShield(fight: BuiltInFight): Boolean =
        fight.hands.offHand.isOf(Items.SHIELD) || fight.hands.has { it.isOf(Items.SHIELD) }

    fun shieldReady(fight: BuiltInFight): Boolean {
        return !fight.player.cooldowns.isOnCooldown(Items.SHIELD)
    }

    /**
     * Raises a shield from the off hand, or from the inventory in the main hand; false without one.
     */
    fun raise(fight: BuiltInFight): Boolean {
        if (fight.hands.offHand.isOf(Items.SHIELD)) return fight.body.startUsingItem(InteractionHand.OFF_HAND)
        if (!fight.hands.hold { it.isOf(Items.SHIELD) }) return false
        return fight.body.startUsingItem(InteractionHand.MAIN_HAND)
    }

    fun targetBlocking(fight: BuiltInFight): Boolean = fight.target?.isBlocking == true

    /**
     * Whether [entity] looks at the fighter within [degrees].
     */
    fun facesMe(fight: BuiltInFight, entity: LivingEntity, degrees: Double): Boolean {
        val towards = fight.player.eyePosition.subtract(entity.eyePosition)
        if (towards.lengthSqr() < 1.0E-4) return true
        val cos = entity.getViewVector(1f).dot(towards.normalize()).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos)) <= degrees
    }

    fun holdsAxe(fight: BuiltInFight): Boolean = fight.hands.mainHand.isAxe || fight.hands.has { it.isAxe }

    fun holdAxe(fight: BuiltInFight): Boolean = fight.hands.hold { it.isAxe }
}

/**
 * Puts a totem in the empty off hand, as after a pop, a moment later as a player's hotkey would.
 *
 * @agent claude-opus-5-5
 */
internal class Totem : Technique(Skill.TOTEM, 100, 0) {

    private var equipAt = 0L

    override fun can(fight: BuiltInFight): Boolean =
        fight.hands.offHand.isEmpty && fight.hands.has { it.isOf(Items.TOTEM_OF_UNDYING) }

    override fun begin(fight: BuiltInFight) {
        equipAt = fight.tick + fight.random.nextInt(3, 11) + fight.slow(0, 15)
    }

    override fun tick(fight: BuiltInFight): Boolean {
        if (fight.tick < equipAt) return true
        fight.hands.find { it.isOf(Items.TOTEM_OF_UNDYING) }?.let(fight.body::swapWithOffhand)
        return false
    }
}

/**
 * Raises the shield. Reactive: while the target closes in facing the fighter and its own hit is not ready, lowered
 * to hit. Always ([Rules.alwaysGuard]): up whenever the shield works and the target's shield is not stunned.
 *
 * @agent claude-opus-5-5
 */
internal class Guard(private val always: Boolean) : Technique(Skill.GUARD, 40, if (always) 0 else 10) {

    override val interruptible: Boolean
        get() = true

    override fun can(fight: BuiltInFight): Boolean =
        Shielding.hasShield(fight) && Shielding.shieldReady(fight) && fight.distance() < RANGE && wanted(fight)

    private fun wanted(fight: BuiltInFight): Boolean {
        if (always) return fight.tick >= fight.stunUntil
        val target = fight.target ?: return false
        val hitReady = fight.body.attackStrength >= fight.skillLevel.strength && fight.inReach(target)
        // A good player drops the shield against an axe rather than eat the stun
        val axed = fight.skillLevel == SkillLevel.HARD && target.mainHandItem.isAxe
        return !hitReady && !axed && Shielding.facesMe(fight, target, FACING)
    }

    override fun begin(fight: BuiltInFight) {
        Shielding.raise(fight)
        fight.attackHeld = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        if (!Shielding.shieldReady(fight) || fight.distance() >= RANGE || !wanted(fight)) return false
        if (!fight.body.isUsingItem) Shielding.raise(fight)
        fight.attackHeld = true
        return true
    }

    override fun end(fight: BuiltInFight) {
        fight.body.releaseUsingItem()
        fight.hands.holdPrimary()
    }

    private companion object {
        const val RANGE = 6.0
        const val FACING = 50.0
    }
}

/**
 * The target blocks: the axe into the hand and a hit that disables the shield, then back to the weapon.
 *
 * @agent claude-opus-5-5
 */
internal class ShieldBreak : Technique(Skill.SHIELD_BREAK, 80, 8) {

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        return Shielding.targetBlocking(fight) && Shielding.holdsAxe(fight) && fight.inReach(target) &&
            fight.aimedAt(target) && fight.hasLineOfSight(target)
    }

    override fun begin(fight: BuiltInFight) {
        if (fight.body.isUsingItem) fight.body.releaseUsingItem()
        Shielding.holdAxe(fight)
        fight.target?.let { if (fight.inReach(it)) fight.strike(it) }
        fight.attackHeld = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        if (!Shielding.targetBlocking(fight)) fight.stunUntil = fight.tick + Shielding.STUN_TICKS
        return false
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()
}
