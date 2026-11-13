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

package net.ccbluex.agent.skills.shop

import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody

/**
 * The built-in [Shop]: a tick after [buy] it runs the [Counter] until that is done, then counts the purchase
 * as bought once the price left the inventory. A failed one keeps [buy] refusing for [RETRY_TICKS].
 *
 * @agent claude-opus-5-5
 */
internal class Shopping(
    private val body: AgentBody,
    override val catalogue: Catalogue,
    private val counter: Counter,
) : Shop {

    private var ticks = 0L
    private var retryAt = 0L

    private var offer: Offer? = null
    private var before = 0
    private var step = 0
    private var countedFrom = -1

    override var lastPurchase: Boolean? = null
        private set

    override val isBuying: Boolean
        get() = offer != null

    override fun buy(wish: Wish): Boolean {
        if (offer != null || ticks < retryAt) return false
        val next = catalogue.offer(wish) ?: return false
        val inventory = body.player.inventory
        if (!inventory.affords(next)) return false
        offer = next
        before = inventory.money(next.currency)
        step = -1
        countedFrom = -1
        return true
    }

    override fun tick(agent: Agent) {
        ticks++
        val offer = offer ?: return
        agent.takeMovement()
        step++
        // The shopkeeper was looked at in the tick of buy(); the purchase follows a tick later
        if (step == 0) return
        val paid = body.player.inventory.money(offer.currency) <= before - offer.price
        if (countedFrom < 0) {
            if (paid) {
                finish(agent, offer, bought = true)
                return
            }
            if (counter.step(offer, step - 1) && step < MAX_TICKS) return
            countedFrom = step
            return
        }
        // Menu clicks are answered by the server a little later
        if (paid || step - countedFrom >= CONFIRM_TICKS) finish(agent, offer, paid)
    }

    private fun finish(agent: Agent, offer: Offer, bought: Boolean) {
        this.offer = null
        lastPurchase = bought
        if (bought) {
            agent.decide("Bought ${offer.upgrade ?: offer.stack.item}", offer.price.toDouble(), offer.currency.name)
        } else {
            retryAt = ticks + RETRY_TICKS
            agent.logger.debug("Could not buy {}", offer)
        }
    }

    private companion object {
        const val RETRY_TICKS = 100
        const val MAX_TICKS = 600
        const val CONFIRM_TICKS = 10
    }
}
