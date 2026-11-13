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

import net.ccbluex.agent.skills.ItemMatch
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * What a shop sells and for how much, from the host: the live shop configuration on a server, the known prices of
 * a network on a client.
 *
 * @agent claude-opus-5-5
 */
public interface Catalogue {

    /**
     * Every item offer, each tier of a tiered item as an offer of its own.
     */
    public val items: List<Offer>

    /**
     * The next tier of the team upgrade [name] for the agent's team; null when it has them all or none is sold.
     */
    public fun upgrade(name: String): Offer?

    /**
     * Tiers of the team upgrade [name] the team bought; 0 for none.
     */
    public fun upgradeTier(name: String): Int

    /**
     * Traps the team has set and waiting.
     */
    public val activeTraps: Int
}

/**
 * One thing a shop sells.
 *
 * @property stack what it gives, its count the amount; for team upgrades the menu icon
 * @property upgrade the team upgrade it is, such as `upgrade-forge`; null for items
 * @agent claude-opus-5-5
 */
public data class Offer @JvmOverloads constructor(
    val stack: ItemStack,
    val currency: Currency,
    val price: Int,
    val upgrade: String? = null,
)

/**
 * What shops take as money.
 *
 * @agent claude-opus-5-5
 */
public enum class Currency(public val item: Item) {
    IRON(Items.IRON_INGOT),
    GOLD(Items.GOLD_INGOT),
    DIAMOND(Items.DIAMOND),
    EMERALD(Items.EMERALD),
    ;

    public companion object {
        /**
         * The currency [item] is; null when it is none.
         */
        @JvmStatic
        public fun of(item: Item): Currency? = entries.firstOrNull { it.item == item }
    }
}

/**
 * The cheapest item offer giving something [match] accepts.
 */
internal fun Catalogue.cheapest(match: ItemMatch): Offer? =
    items.filter { match.matches(it.stack) }.minByOrNull { it.price }

/**
 * The offer that makes [wish] come true: its team upgrade, or the cheapest matching item.
 */
internal fun Catalogue.offer(wish: Wish): Offer? = wish.upgrade?.let(::upgrade) ?: cheapest(wish.buy)

internal fun Inventory.money(currency: Currency): Int =
    (0 until containerSize).sumOf { slot -> getItem(slot).let { if (it.`is`(currency.item)) it.count else 0 } }

internal fun Inventory.affords(offer: Offer?): Boolean = offer != null && money(offer.currency) >= offer.price
