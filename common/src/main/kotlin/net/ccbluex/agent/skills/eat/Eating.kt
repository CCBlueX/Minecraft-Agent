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

package net.ccbluex.agent.skills.eat

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.FED
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.bestFoodSlot
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.item.ItemStack
import java.util.EnumMap
import java.util.concurrent.ThreadLocalRandom

/**
 * The built-in [Eat]. Each tick without a meal under way it picks the [Meal] of highest priority that is due, ready
 * and at hand, selects it and uses it: held until eaten, clicked, or thrown at the feet. It leaves the hand to the
 * fight while that heals, and waits while a container is open. With a target in reach it heals only as
 * [healsInFight] says.
 *
 * @agent claude-opus-5-5
 */
internal class Eating : Eat {

    private val readyAt = EnumMap<Meal, Long>(Meal::class.java)

    private var meal: Meal? = null
    private var hotbar = 0
    private var item = ItemStack.EMPTY
    private var since = 0L
    private var started = false
    private var xRot = 0f

    /**
     * Eating food up to [FED] once hunger got to [Eat.HUNGRY].
     */
    private var feeding = false
    private var due = false

    override val wantsToEat: Boolean
        get() = meal != null || due

    override fun tick(agent: Agent) {
        if (meal != null) {
            continueMeal(agent)
            return
        }
        val body = agent.body
        due = false
        if (agent.skills.fight?.isHealing == true || body.containerMenu != null || body.isUsingItem) {
            return
        }

        val player = body.player
        val inventory = player.inventory
        for (next in BY_PRIORITY) {
            if (agent.ticks < (readyAt[next] ?: 0L) || !isWanted(next, agent)) continue
            val slot = if (next == Meal.FOOD) inventory.bestFoodSlot() else inventory.slotOf(next::matches)
            if (slot < 0) continue
            due = true
            start(agent, next, slot)
            return
        }
    }

    private fun isWanted(meal: Meal, agent: Agent): Boolean {
        val player = agent.body.player
        val heal = agent.personality.fighting.heal
        val share = (player.health + player.absorptionAmount) / player.maxHealth
        val target = agent.skills.fight?.target
        if (meal != Meal.FOOD && target != null) {
            val targetShare = (target.health + target.absorptionAmount) / target.maxHealth
            val distance = target.distanceTo(player).toDouble()
            if (!healsInFight(share.toDouble(), targetShare.toDouble(), distance)) return false
        }
        return when (meal) {
            Meal.SPLASH_HEAL -> share < Meal.SPLASH_SHARE * heal
            Meal.SOUP -> player.health <= Meal.SOUP_HP * heal
            Meal.GOLDEN_APPLE ->
                (player.health < Meal.GAPPLE_HP || share < Meal.GAPPLE_SHARE * heal) && !Meal.stillBuffed(player)
            Meal.FOOD -> {
                val hunger = player.foodData.foodLevel
                if (hunger >= FED) feeding = false
                target == null && (hunger <= Eat.HUNGRY || feeding && hunger < FED)
            }
        }
    }

    private fun start(agent: Agent, meal: Meal, slot: Int) {
        this.meal = meal
        item = agent.body.player.inventory.getItem(slot).copy()
        hotbar = agent.body.hold(slot)
        since = agent.ticks
        started = false
        xRot = ThreadLocalRandom.current().nextDouble(THROW_X_ROT_MIN, THROW_X_ROT_MAX).toFloat()
        if (meal == Meal.FOOD) feeding = true
        agent.decide("Eat ${meal.name.lowercase()}", meal.priority.toDouble(), "hp ${agent.body.player.health}")
    }

    private fun continueMeal(agent: Agent) {
        val meal = meal ?: return
        val body = agent.body
        val age = agent.ticks - since
        if (!meal.matches(body.player.inventory.getItem(hotbar)) && !started) {
            finish(agent, eaten = false)
            return
        }
        body.selectHotbarSlot(hotbar)
        if (age < 1) return

        when (meal.use) {
            Meal.Use.HOLD -> {
                if (body.isUsingItem) {
                    started = true
                } else if (started) {
                    finish(agent, eaten = true)
                    return
                } else if (age > START_TICKS) {
                    finish(agent, eaten = false)
                    return
                } else {
                    body.startUsingItem(InteractionHand.MAIN_HAND)
                }
                if (age > HOLD_TICKS) finish(agent, eaten = false)
            }

            Meal.Use.CLICK -> finish(agent, eaten = body.useItem(InteractionHand.MAIN_HAND))

            Meal.Use.THROW_DOWN -> {
                body.look(ViewRotation(body.player.yRot, xRot), LookPurpose.AIM)
                if (body.sentRotation.xRot >= xRot - AIM_TOLERANCE || age >= THROW_TICKS) {
                    finish(agent, eaten = body.useItem(InteractionHand.MAIN_HAND))
                }
            }
        }
    }

    private fun finish(agent: Agent, eaten: Boolean) {
        val meal = meal ?: return
        this.meal = null
        due = false
        agent.body.releaseHotbarSlot()
        readyAt[meal] = agent.ticks + if (eaten) meal.cooldown else maxOf(meal.cooldown, RETRY_TICKS)
        if (eaten) {
            agent.listener.ate(item)
        } else {
            agent.logger.debug("Gave up on {}", item.item)
        }
    }

    private companion object {
        val BY_PRIORITY = Meal.entries.sortedByDescending { it.priority }

        /**
         * The use has to show this long after the slot was selected, else the meal is given up.
         */
        const val START_TICKS = 6

        /**
         * A golden apple takes 32 ticks.
         */
        const val HOLD_TICKS = 50
        const val RETRY_TICKS = 40

        const val THROW_X_ROT_MIN = 80.0
        const val THROW_X_ROT_MAX = 90.0
        const val THROW_TICKS = 6
        const val AIM_TOLERANCE = 5f
    }
}

/**
 * A target farther than this cannot hit the agent while it eats a golden apple.
 */
private const val SAFE_DISTANCE = 6.0

/**
 * Whether a heal starts with [health] (with absorption, a share of the maximum) while fighting a target with
 * [targetHealth] [targetDistance] blocks away: once the target is out of reach, or in a trade the agent loses with
 * less health than the target. Else it fights on; eating takes the hand off the fight for over 1.5 seconds, and the
 * fight's own heal skills back off or wall in before they eat.
 *
 * @agent claude-opus-5-5
 */
internal fun healsInFight(health: Double, targetHealth: Double, targetDistance: Double): Boolean =
    targetDistance > SAFE_DISTANCE || health < targetHealth
