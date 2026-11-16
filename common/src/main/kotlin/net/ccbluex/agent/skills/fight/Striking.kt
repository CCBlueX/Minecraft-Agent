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
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The fighter's clicks: when the next hit is ready (a click rate under 1.8 rules, the attack charge otherwise), crit
 * jumps, misses, the return hit after taking one, and noticing which hits landed for the tally and the w-tap.
 *
 * @agent claude-opus-5-5
 */
internal class Striking(private val fight: BuiltInFight) {

    private class Pending(val entity: LivingEntity, val at: Long, val life: Double, var hurtTime: Int)

    var lastAttack = Long.MIN_VALUE / 2
        private set
    var nextAttack = 0L
    private var swingAfter = 0L

    /**
     * The attack charge the next hit waits for under 1.9 rules.
     */
    var threshold = fight.skillLevel.strength
        private set
    private var wantCrit = false
    private var critJump = false
    private var critJumpAt = 0L

    /**
     * How much shorter than the difficulty's longest swing the next one is.
     */
    var reachSlack = 0.0
        private set

    /**
     * Until when the fighter hits straight back after taking a hit.
     */
    var hitBackUntil = 0L

    private var pending: Pending? = null

    private val body get() = fight.body
    private val player get() = fight.player

    fun attack(target: LivingEntity) {
        val tick = fight.tick
        if (critJump && player.onGround() && tick - critJumpAt > CRIT_LAND_TICKS) critJump = false
        val distance = fight.reachDistance(target)
        val strength = body.attackStrength.toDouble()
        val cpsMode = fight.cpsMode
        val ready = if (cpsMode) tick >= nextAttack else strength >= threshold
        if (!cpsMode && tick <= hitBackUntil && strength >= SPRINT_HIT && tick >= swingAfter && canHit(target)) {
            // The return hit goes in at once, with the sprint kept for its knockback
            hitBackUntil = 0
            critJump = false
            swingOrMiss(target)
            return
        }
        if (fight.rules.crits && !critJump && player.onGround() && !fight.sneak && !fight.footwork.isCornered &&
            distance < fight.maxReach() + 1.5
        ) {
            if (!cpsMode && wantCrit && strength >= threshold - CRIT_EARLY) {
                startCritJump(tick)
                return
            }
            if (cpsMode && fight.random.nextDouble() < fight.critChance * CPS_CRIT_SHARE) startCritJump(tick)
        }
        // A crit under 1.9 rules lands only while falling: wait for the top of the jump
        if (!cpsMode && critJump && !player.onGround() && (player.deltaMovement.y >= 0 || player.fallDistance <= 0))
            return
        if (!ready || tick < swingAfter) return
        val aimed = fight.aimedAt(target)
        if (fight.inReach(target) && aimed && fight.hasLineOfSight(target)) {
            if (critJump && !cpsMode) fight.sprint = false
            swingOrMiss(target)
        } else if (cpsMode && aimed && distance <= fight.maxReach() + 1.5) {
            // A click into the air close by, as players do while closing in
            body.swing(InteractionHand.MAIN_HAND)
            nextAttack = tick + interval()
        }
    }

    private fun canHit(target: LivingEntity) =
        fight.inReach(target) && fight.aimedAt(target) && fight.hasLineOfSight(target)

    private fun startCritJump(tick: Long) {
        fight.jump = true
        critJump = true
        critJumpAt = tick
    }

    private fun swingOrMiss(target: LivingEntity) {
        if (fight.random.nextDouble() < fight.skillLevel.miss) miss() else strike(target)
    }

    /**
     * A swing into the air: the click is spent like a hit's.
     */
    private fun miss() {
        val tick = fight.tick
        body.swing(InteractionHand.MAIN_HAND)
        lastAttack = tick
        nextAttack = tick + interval()
        // A swing restarts the 1.9 cooldown
        if (!fight.cpsMode) swingAfter = tick + MISS_COOLDOWN
        wantCrit = false
        critJump = false
        reachSlack = fight.random.nextDouble() * fight.skillLevel.spread
    }

    /**
     * Hits [entity] with what the main hand holds, as a click does; nothing when a block stands in between
     * ([net.ccbluex.agent.navigation.Navigator.canHit]).
     */
    fun strike(entity: Entity) {
        if (!fight.navigator.canHit(entity)) return
        val tick = fight.tick
        val living = entity as? LivingEntity
        if (living != null && living === fight.target) pending = Pending(living, tick, life(living), living.hurtTime)
        body.attack(entity)
        lastAttack = tick
        nextAttack = tick + interval()
        threshold = min(1.0, fight.skillLevel.strength + fight.random.nextGaussian() * THRESHOLD_NOISE)
        wantCrit = fight.random.nextDouble() < fight.critChance
        critJump = false
        reachSlack = fight.random.nextDouble() * fight.skillLevel.spread
        watchLanding()
    }

    /**
     * A hurt animation starting on the target soon after the strike is a landed hit.
     */
    fun watchLanding() {
        val hit = pending ?: return
        if (fight.tick - hit.at > LANDING_TICKS || !hit.entity.isAlive && hit.entity.hurtTime == 0) {
            pending = null
            return
        }
        if (hit.entity.hurtTime > hit.hurtTime) {
            pending = null
            fight.tally.hit(max(0.0, hit.life - life(hit.entity)))
            if (fight.random.nextDouble() < fight.wtapChance) fight.footwork.startWtap()
            return
        }
        hit.hurtTime = hit.entity.hurtTime
    }

    /**
     * Whether the next hit is still well off: the charge far below what the fighter waits for, or its next click.
     */
    fun charging(): Boolean =
        if (fight.cpsMode) fight.tick < nextAttack - 2 else body.attackStrength < threshold - CHARGING_MARGIN

    /**
     * Clicks the next attack now, for a return hit under 1.8 rules.
     */
    fun clickNow() {
        nextAttack = min(nextAttack, fight.tick)
    }

    private fun interval(): Long = interval(fight.cps, fight.random.nextGaussian())

    companion object {
        /**
         * Sprint hits and crits need this much attack charge.
         */
        const val SPRINT_HIT = 0.848
        private const val CRIT_EARLY = 0.35
        private const val CRIT_LAND_TICKS = 3
        private const val CPS_CRIT_SHARE = 0.08
        private const val MISS_COOLDOWN = 10
        private const val THRESHOLD_NOISE = 0.03
        private const val CHARGING_MARGIN = 0.3
        private const val LANDING_TICKS = 10
        private const val INTERVAL_NOISE = 0.2

        /**
         * Ticks to the next click at [cps], spread by [gaussian] (a standard normal draw); at least one.
         */
        fun interval(cps: Double, gaussian: Double): Long =
            max(1L, (20 / max(0.1, cps) * (1 + gaussian * INTERVAL_NOISE)).roundToLong())

        fun life(entity: LivingEntity): Double = (entity.health + entity.absorptionAmount).toDouble()
    }
}
