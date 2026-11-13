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

import net.minecraft.core.Holder
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Eats a golden apple when low, backing off; walls itself in first when it carries blocks and the target is close. A
 * fighter with totems also eats right after a pop, and whenever its absorption is gone and the target is not close.
 *
 * @agent claude-opus-5-5
 */
internal class Gapple : Technique(Skill.GAPPLE, 70, 60) {

    private var walls = 0
    private var since = 0L

    override val heals: Boolean
        get() = true

    override fun can(fight: BuiltInFight): Boolean {
        if (fight.eatWanted || fight.body.isUsingItem || !fight.hands.has { it.isGoldenApple }) return false
        if (fight.low(LOW)) return true
        if (!fight.carriesTotems()) return false
        return fight.tick - fight.poppedAt <= POP_TICKS || fight.player.absorptionAmount <= 0 && fight.distance() >= 3
    }

    override fun begin(fight: BuiltInFight) {
        walls = if (fight.hands.has { it.isBuildingBlock } && fight.distance() < WALL_DISTANCE) 2 else 0
        since = fight.tick
        if (walls == 0) startEating(fight)
    }

    private fun startEating(fight: BuiltInFight) {
        if (fight.hands.hold { it.isGoldenApple }) fight.body.startUsingItem(InteractionHand.MAIN_HAND)
        since = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        fight.moveControlled = true
        if (walls > 0) {
            val target = fight.target
            if (target == null || !Building.wall(fight, target.position(), 2 - walls) || --walls == 0) {
                walls = 0
                startEating(fight)
            }
            return fight.tick - since < WALL_TICKS || walls == 0
        }
        fight.backOff()
        val elapsed = fight.tick - since
        if (!fight.body.isUsingItem && elapsed > 3) return false
        return elapsed < EAT_TICKS
    }

    override fun end(fight: BuiltInFight) {
        fight.body.releaseUsingItem()
        fight.hands.holdPrimary()
    }

    private companion object {
        const val LOW = 0.4
        const val POP_TICKS = 20
        const val WALL_DISTANCE = 4.0
        const val WALL_TICKS = 10

        /**
         * A little over vanilla's 32 ticks of eating.
         */
        const val EAT_TICKS = 40
    }
}

/**
 * Throws a splash potion at its own feet: it leaves the hand 20 degrees above the look, so the fighter looks down
 * 80 to 90 degrees for it to land within a block.
 *
 * @agent claude-opus-5-5
 */
internal abstract class Splash(kind: Skill, priority: Int, cooldown: Int) : Technique(kind, priority, cooldown) {

    private var effect: Holder<MobEffect>? = null
    private var xRot = 85f
    private var since = 0L

    abstract fun potion(fight: BuiltInFight): Holder<MobEffect>?

    override fun begin(fight: BuiltInFight) {
        if (fight.body.isUsingItem) fight.body.releaseUsingItem()
        effect = potion(fight)
        xRot = fight.random.nextDouble(80.0, 90.0).toFloat()
        since = fight.tick
    }

    fun lookDown(fight: BuiltInFight) {
        fight.attackHeld = true
        fight.lookXRot(xRot, 4.0)
    }

    /**
     * Looks down and throws once aimed, or after 6 ticks whatever the aim; true once there is nothing to throw.
     */
    fun aimAndThrow(fight: BuiltInFight): Boolean {
        lookDown(fight)
        val splash = effect ?: return true
        if (!fight.aimedXRot(xRot, AIM_TOLERANCE) && fight.tick - since < AIM_TICKS) {
            return false
        }
        if (fight.hands.hold { it.splashes(splash) }) fight.hands.use()
        effect = null
        return true
    }

    fun next(fight: BuiltInFight) {
        effect = potion(fight)
        since = fight.tick
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val AIM_TOLERANCE = 6.0
        const val AIM_TICKS = 6
    }
}

/**
 * Under half health: backs off strafing, heals at its feet, and once more 2 to 4 ticks later while still low.
 *
 * @agent claude-opus-5-5
 */
internal class PotHeal : Splash(Skill.POT_HEAL, 75, 10) {

    private var strafe = 1f
    private var thrown = 0
    private var secondAt = 0L

    override val heals: Boolean
        get() = true

    override fun potion(fight: BuiltInFight): Holder<MobEffect>? =
        MobEffects.INSTANT_HEALTH.takeIf { effect -> fight.hands.has { it.splashes(effect) } }

    override fun can(fight: BuiltInFight): Boolean = !fight.eatWanted && fight.low(LOW) && potion(fight) != null

    override fun begin(fight: BuiltInFight) {
        super.begin(fight)
        strafe = if (fight.random.nextBoolean()) 1f else -1f
        thrown = 0
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.moveControlled = true
        fight.backOff()
        if (fight.style.strafe > 0) fight.strafe = strafe
        if (thrown == 1) {
            lookDown(fight)
            if (fight.tick < secondAt) return true
            if (!fight.low(LOW)) return false
            next(fight)
        }
        if (!aimAndThrow(fight)) return true
        if (++thrown == 2) return false
        secondAt = fight.tick + fight.random.nextInt(2, 5)
        return true
    }

    private companion object {
        const val LOW = 0.5
    }
}

/**
 * Splashes strength or swiftness again when it is missing or has under 5 seconds left.
 *
 * @agent claude-opus-5-5
 */
internal class PotBuff : Splash(Skill.POT_BUFF, 60, 10) {

    override fun potion(fight: BuiltInFight): Holder<MobEffect>? =
        listOf(MobEffects.STRENGTH, MobEffects.SPEED).firstOrNull { effect ->
            (fight.player.getEffect(effect)?.duration ?: 0) < RENEW_TICKS && fight.hands.has { it.splashes(effect) }
        }

    override fun can(fight: BuiltInFight): Boolean = potion(fight) != null && fight.distance() > 2.5

    override fun tick(fight: BuiltInFight): Boolean = !aimAndThrow(fight)

    private companion object {
        const val RENEW_TICKS = 100
    }
}

/**
 * Under 12 health: a stew from the hotbar used, and with [Rules.instantSoup] the bowl dropped the tick after; on
 * a server without instant soup the stew is eaten. An empty hotbar is refilled from the inventory first, backing off
 * for the moment that takes.
 *
 * @agent claude-opus-5-5
 */
internal class Soup : Technique(Skill.SOUP, 75, 2) {

    private enum class Step { USE, EAT, DROP, REFILL }

    private var step = Step.USE
    private var stepAt = 0L
    private var refill = 0

    override val heals: Boolean
        get() = true

    override fun can(fight: BuiltInFight): Boolean {
        if (fight.eatWanted || fight.body.isUsingItem || !fight.hands.has(::isStew)) return false
        val low = fight.player.health <= LOW_HEALTH * fight.style.heal
        return low || hotbarStews(fight) == 0 && fight.distance() > REFILL_DISTANCE
    }

    private fun isStew(stack: ItemStack) = stack.isOf(Items.MUSHROOM_STEW)

    private fun hotbarStews(fight: BuiltInFight) =
        (0 until Inventory.getSelectionSize()).count { isStew(fight.hands.stack(it)) }

    override fun begin(fight: BuiltInFight) {
        if (fight.body.isUsingItem) fight.body.releaseUsingItem()
        if (hotbarStews(fight) == 0) {
            step(fight, Step.REFILL)
            refill = fight.slow(12, 40)
            return
        }
        fight.hands.hold(::isStew)
        step(fight, Step.USE)
    }

    private fun step(fight: BuiltInFight, step: Step) {
        this.step = step
        stepAt = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        when (step) {
            Step.USE -> {
                if (!fight.hands.mainHand.isOf(Items.MUSHROOM_STEW)) return false
                if (fight.rules.instantSoup) {
                    fight.hands.use()
                    step(fight, Step.DROP)
                } else {
                    fight.body.startUsingItem(InteractionHand.MAIN_HAND)
                    step(fight, Step.EAT)
                }
            }
            Step.EAT -> {
                fight.moveControlled = true
                fight.backOff()
                return fight.body.isUsingItem && fight.tick - stepAt < EAT_TICKS || fight.tick - stepAt <= 3
            }
            Step.DROP -> {
                val slot = fight.player.inventory.selectedSlot
                if (fight.hands.stack(slot).isOf(Items.BOWL)) fight.body.drop(slot, true)
                return false
            }
            Step.REFILL -> {
                fight.moveControlled = true
                fight.backOff()
                moveStewToHotbar(fight)
                return fight.tick - stepAt < refill
            }
        }
        return true
    }

    /**
     * One stew from the inventory onto an empty or bowl-holding hotbar slot, a click a tick as by hand.
     */
    private fun moveStewToHotbar(fight: BuiltInFight) {
        val free = (0 until Inventory.getSelectionSize()).firstOrNull {
            fight.hands.stack(it).let { stack -> stack.isEmpty || stack.isOf(Items.BOWL) }
        } ?: return
        val stew = (Inventory.getSelectionSize() until Inventory.INVENTORY_SIZE)
            .firstOrNull { isStew(fight.hands.stack(it)) } ?: return
        if (fight.hands.stack(free).isOf(Items.BOWL)) fight.body.drop(free, true)
        fight.body.swapWithHotbar(stew, free)
    }

    override fun end(fight: BuiltInFight) {
        if (step == Step.EAT) fight.body.releaseUsingItem()
        fight.hands.holdPrimary()
    }

    private companion object {
        /**
         * Health, not a share: stews heal a fixed 3.5 hearts.
         */
        const val LOW_HEALTH = 12.0
        const val REFILL_DISTANCE = 4.0
        const val EAT_TICKS = 40
    }
}
