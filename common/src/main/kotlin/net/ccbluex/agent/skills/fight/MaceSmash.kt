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


import net.ccbluex.agent.world.item.Enchantment
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * A wind charge at its own feet, up, and down onto the target with the mace that fits the fall: density for a high
 * one, breach for a short one, wind burst to go straight back up. An axe first when the target blocks. Starts with
 * the target between [min] and [max] blocks away horizontally.
 *
 * @agent claude-opus-5-5
 */
internal class MaceSmash(private val min: Double, private val max: Double) : Technique(Skill.MACE_SMASH, 50, 50) {

    private enum class Phase { JUMP, LAUNCH, RISE, FALL }

    private var phase = Phase.JUMP
    private var since = 0L
    private var phaseAt = 0L
    private var chains = 0

    /**
     * The enchantment of the mace for the next hit; null for any mace.
     */
    private var chosen: Enchantment? = null

    /**
     * Ticks spent looking down before the jump: none on hard.
     */
    private var windup = 0

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        if (!fight.player.onGround()) return false
        return fight.horizontalDistance() in min..max && target.y - fight.player.y < 2 &&
            fight.hands.has { it.isOf(Items.WIND_CHARGE) } && fight.hands.has { it.isOf(Items.MACE) }
    }

    private fun mace(enchantment: Enchantment?): (ItemStack) -> Boolean =
        { it.isOf(Items.MACE) && (enchantment == null || it.enchantLevel(enchantment) > 0) }

    private fun pick(fight: BuiltInFight, fall: Float): Enchantment? {
        val burst = fight.hands.has(mace(Enchantments.WIND_BURST))
        val density = fight.hands.has(mace(Enchantments.DENSITY))
        val breach = fight.hands.has(mace(Enchantments.BREACH))
        return when {
            burst && fall < BURST_FALL && chains < 2 && fight.random.nextBoolean() -> Enchantments.WIND_BURST
            density && (fall >= DENSITY_FALL || !breach) -> Enchantments.DENSITY
            breach -> Enchantments.BREACH
            else -> null
        }
    }

    // A wind charge's rise and fall is shorter than a mace's cooldown after a swap: the hit waits as long as it can
    private fun landing(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return true
        return fight.player.y + fight.player.deltaMovement.y * 1.5 < target.y + 0.5
    }

    private fun phase(fight: BuiltInFight, phase: Phase) {
        this.phase = phase
        phaseAt = fight.tick
    }

    override fun begin(fight: BuiltInFight) {
        chains = 0
        windup = fight.slow(0, 8)
        since = fight.tick
        phase(fight, Phase.JUMP)
        fight.moveControlled = true
        fight.attackHeld = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        fight.moveControlled = true
        val target = fight.target ?: return false
        if (fight.tick - since > MAX_TICKS) return false
        val player = fight.player
        val rising = player.deltaMovement.y > 0
        val elapsed = fight.tick - phaseAt
        when (phase) {
            Phase.JUMP -> {
                // Looking down before the jump: the charge has to go off at the feet, early in the rise, to lift far
                fight.lookXRot(fight.down, 8.0)
                fight.jump = fight.xRot >= 70 && elapsed >= windup
                if (!player.onGround() && rising) {
                    phase(fight, Phase.LAUNCH)
                } else if (elapsed > 10 + windup) {
                    return false
                }
            }
            Phase.LAUNCH -> {
                fight.lookXRot(fight.down, 4.0)
                if (fight.xRot >= 75) {
                    if (!fight.hands.hold { it.isOf(Items.WIND_CHARGE) }) return false
                    fight.hands.use()
                    chosen = pick(fight, 4f)
                    phase(fight, Phase.RISE)
                } else if (elapsed > 6) {
                    return false
                }
            }
            Phase.RISE -> {
                // Every swap restarts the attack charge, so the mace goes in hand once, on the way up
                fight.hands.hold(mace(chosen))
                fight.forward = if (fight.horizontalDistance() > 1) 1f else 0f
                if (!rising && elapsed > 2) phase(fight, Phase.FALL)
                if (player.onGround() && elapsed > 5) return false
            }
            Phase.FALL -> {
                fight.forward = if (fight.horizontalDistance() > 0.8) 1f else 0f
                if (player.onGround()) return false
                val ready = fight.body.attackStrength >= fight.skillLevel.strength || landing(fight)
                if (player.fallDistance > SMASH_FALL && fight.inReach(target) && fight.aimedAt(target) && ready) {
                    if (Shielding.targetBlocking(fight) && Shielding.holdAxe(fight)) {
                        if (fight.inReach(target)) fight.strike(target)
                        return true
                    }
                    val enchantment = chosen
                    fight.hands.hold(mace(enchantment))
                    fight.strike(target)
                    if (enchantment == Enchantments.WIND_BURST && chains < 3) {
                        chains++
                        chosen = pick(fight, 0f)
                        phase(fight, Phase.RISE)
                        return true
                    }
                    return false
                }
            }
        }
        return true
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val MAX_TICKS = 100
        const val SMASH_FALL = 1.5
        const val BURST_FALL = 6f
        const val DENSITY_FALL = 5f
    }
}
