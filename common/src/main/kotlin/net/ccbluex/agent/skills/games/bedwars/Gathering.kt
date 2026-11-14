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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.skills.shop.Currency
import net.ccbluex.agent.skills.shop.Wishes
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * A leader this close to a generator or a dropped resource has it.
 */
internal const val OCCUPIED = 3.0

private const val HAND_REACH = 2.5
private const val SPARE_IRON = 12
private const val SPARE_GOLD = 4
private const val ORE_WAIT_TICKS = 300
private const val ORE_REST_TICKS = 900
private const val RETRY_TICKS = 1200
private const val ENOUGH_EMERALDS = 6
private const val OPEN_TICKS = 20

/**
 * GATHER: fills the team chest with what the agent has to spare, else stands on the own generator of what it saves
 * for next.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.gatherUp() {
    while (true) {
        if (!spare()) gather(nextWish()?.let(::offer)?.currency ?: Currency.IRON)
        waitTicks(1)
    }
}

/**
 * One tick on the own generator of [resource], or the iron one; between iron and gold when they lie side by side.
 * A leader standing there has it.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.gather(resource: Currency) {
    val generator = ownGenerator(resource) ?: ownGenerator(Currency.IRON)
    if (generator == null) {
        guard(team.spawn, "Waiting")
        return
    }
    var at = generator.position
    val other = ownGenerator(if (generator.resource == Currency.GOLD) Currency.IRON else Currency.GOLD)
    if (other != null && other.position.horizontalDistanceTo(at) < 3.5) at = at.add(other.position).scale(0.5)
    val mate = leaders().firstOrNull { it.position().distanceTo(at) < OCCUPIED }
    if (mate != null) {
        val aside = team.spawn.subtract(at).multiply(1.0, 0.0, 1.0)
        val spot = if (aside.lengthSqr() < 1.0E-3) team.spawn else at.add(aside.normalize().scale(OCCUPIED + 1))
        guard(spot, "Leaving the generator to ${mate.scoreboardName}")
        return
    }
    goal = "Collecting ${resource.name.lowercase()}"
    plan.bridge(game, null)
    val distance = self.position().horizontalDistanceTo(at)
    if (!isOnIsland() && distance > Activities.ISLAND) {
        travel.bridgeTo(agent, at, 0.3, tick)
    } else if (distance > 0.5) {
        travel.walk(agent, at, 0.4)
    }
}

/**
 * @agent claude-opus-5-5
 */
private fun Activities.guard(spot: Vec3, text: String) {
    goal = text
    plan.bridge(game, null)
    if (self.position().horizontalDistanceTo(spot) > 1.5) travel.walk(agent, spot, 1.0)
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.ownGenerator(resource: Currency): Generator? =
    senses.generators.firstOrNull { it.team == team.name && it.resource == resource }

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.neutralGenerator(resource: Currency): Generator? {
    val here = self.position()
    return senses.generators.filter { it.team == null && it.resource == resource }
        .minByOrNull { it.position.distanceToSqr(here) }
}

/**
 * DIAMONDS and EMERALDS: wool for the way first, then over to the nearest generator of [resource], where it takes
 * what lies there and waits a little for more; then it goes to spend it and comes back later.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.ore(resource: Currency) {
    val name = if (resource == Currency.EMERALD) "emeralds" else "diamonds"
    while (!hasEnough(resource)) {
        if (count(Wishes.WOOL) < TRAVEL_WOOL && isOnIsland()) {
            if (!obtain(Wishes.wool(16, 0.0))) return
        } else {
            val at = neutralGenerator(resource)?.position ?: return
            goal = "Getting $name"
            if (!isAtGenerator(at)) {
                if (travel.bridgeTo(agent, at, 0.5, tick) == Travel.Status.BLOCKED) {
                    oreAgainAt[resource] = tick + RETRY_TICKS
                    return
                }
                plan.bridge(game, agent.navigator.placesAhead)
            } else if (!waitForDrops(resource, at)) {
                oreAgainAt[resource] = tick + ORE_REST_TICKS
                return
            }
        }
        waitTicks(1)
    }
}

/**
 * Stands on the generator at [at] while drops keep coming; false once none came for a while, true when it got
 * pushed off or has enough.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Activities.waitForDrops(resource: Currency, at: Vec3): Boolean {
    plan.bridge(game, null)
    var held = count(resource)
    var since = tick
    while (isAtGenerator(at) && !hasEnough(resource)) {
        if (self.position().horizontalDistanceTo(at) > 0.6) {
            travel.walk(agent, at, 0.5)
            since = tick
        }
        val now = count(resource)
        if (now != held) {
            held = now
            since = tick
        }
        if (tick - since > ORE_WAIT_TICKS * (0.5 + traits.patience)) return false
        waitTicks(1)
    }
    return true
}

/**
 * @agent claude-opus-5-5
 */
private fun Activities.isAtGenerator(at: Vec3): Boolean {
    val here = self.position()
    return here.horizontalDistanceTo(at) <= 2.5 && abs(here.y - at.y) <= 2
}

/**
 * @agent claude-opus-5-5
 */
private fun Activities.hasEnough(resource: Currency): Boolean {
    if (resource == Currency.EMERALD) return count(Currency.EMERALD) >= ENOUGH_EMERALDS
    val wanted = wantedUpgrade() ?: return true
    return count(Currency.DIAMOND) >= wanted.price
}

/**
 * Takes what the agent will not need to the team chest, for its leaders; false with nothing to spare or no leader.
 * Whole stacks move with a shift click, so a stack bigger than what is spare stays.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.spare(): Boolean {
    if (tick < chestAgainAt || leaders().isEmpty()) return false
    val spare = Currency.entries.associateWith { maxOf(0, count(it) - need(it)) }
    val worth = (spare[Currency.IRON] ?: 0) >= SPARE_IRON || (spare[Currency.GOLD] ?: 0) >= SPARE_GOLD ||
        (spare[Currency.EMERALD] ?: 0) > 0 || (spare[Currency.DIAMOND] ?: 0) > 0
    if (!worth) return false
    val chest = team.chest ?: return false
    val at = Vec3.atBottomCenterOf(chest)
    goal = "Filling the team chest"
    plan.bridge(game, null)
    while (!Travel.isWithin(self.position(), at, HAND_REACH + 0.5)) {
        if (travel.walk(agent, at, HAND_REACH - 1) == Travel.Status.BLOCKED) {
            chestAgainAt = tick + RETRY_TICKS
            return true
        }
        waitTicks(1)
    }

    val top = Vec3.atCenterOf(chest).add(0.0, 0.5, 0.0)
    val body = agent.body
    look(top)
    if (body.containerMenu == null) {
        body.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(top, Direction.UP, chest, false))
        if (body.containerMenu == null && tickConditional(OPEN_TICKS) { body.containerMenu != null }) {
            chestAgainAt = tick + RETRY_TICKS
            return true
        }
    }
    val menu = body.containerMenu ?: return true
    var moved = 0
    for ((currency, amount) in spare) {
        var left = amount
        for (slot in menu.slots) {
            val stack = slot.item
            if (slot.container !is Inventory || !stack.`is`(currency.item) || stack.count > left) continue
            left -= stack.count
            moved += stack.count
            body.clickContainer(slot.index, 0, ContainerInput.QUICK_MOVE)
        }
    }
    body.closeContainer()
    body.swing(InteractionHand.MAIN_HAND)
    if (moved > 0) say(Callout.LeftResources(moved)) else chestAgainAt = tick + RETRY_TICKS
    return true
}
