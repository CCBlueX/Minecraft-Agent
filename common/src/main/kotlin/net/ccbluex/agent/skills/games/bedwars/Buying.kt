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

import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.shop.Currency
import net.ccbluex.agent.skills.shop.Offer
import net.ccbluex.agent.skills.shop.Wish
import net.ccbluex.agent.skills.shop.Wishes
import net.ccbluex.agent.skills.shop.affords
import net.ccbluex.agent.skills.shop.cheapest
import net.ccbluex.agent.skills.shop.money
import net.ccbluex.agent.skills.slotOf
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

private const val SHOP_REACH = 3.0
private const val WOOL_STACK = 16

private val WOOD = ItemMatch { it.`is`(ItemTags.PLANKS) || it.`is`(ItemTags.LOGS) }
private val END_STONE = ItemMatch.of(Items.END_STONE)
private val HARD_BLOCK = ItemMatch { WOOD.matches(it) || END_STONE.matches(it) }

/**
 * The wish ladder for this agent, with its Bed Wars leanings; null without a shop to buy from.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.wishes(): Wishes? {
    val catalogue = agent.skills.shop?.catalogue ?: return null
    wishes?.takeIf { wishesFor === catalogue }?.let { return it }
    val leanings = agent.personality.copy(
        aggression = traits.aggression, greed = traits.greed, caution = traits.caution, patience = traits.patience,
    )
    return Wishes(catalogue, leanings, random).also {
        wishes = it
        wishesFor = catalogue
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.nextWish(): Wish? =
    wishes()?.next(self.inventory, senses.matchSeconds) { ownGenerator(it) != null }

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.offer(wish: Wish): Offer? = wishes()?.offer(wish)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.affords(offer: Offer?): Boolean = self.inventory.affords(offer)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.has(wish: Wish): Boolean = Wishes.count(self.inventory, wish.owned) >= wish.amount

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.count(currency: Currency): Int = self.inventory.money(currency)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.count(match: ItemMatch): Int = Wishes.count(self.inventory, match)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.slotOf(match: ItemMatch): Int = self.inventory.slotOf(match)

/**
 * @agent claude-opus-5-5
 */
internal fun isWool(wish: Wish): Boolean = wish.owned === Wishes.WOOL

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.hasSword(): Boolean = has(Wishes.STONE_SWORD)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.hasIronSword(): Boolean = has(Wishes.IRON_SWORD)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.hasChainArmor(): Boolean = has(Wishes.CHAIN_ARMOR)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.wantedUpgrade(): Offer? = wishes()?.wantedUpgrade(ownGenerator(Currency.IRON) != null)

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.upgradeToBuy(): Offer? =
    wishes()?.upgradeToBuy(self.inventory, ownGenerator(Currency.IRON) != null)

/**
 * The blocks of a defence layer: its own, or any hard block when the shop sells none of it.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.blocks(fill: Fill): ItemMatch {
    val catalogue = agent.skills.shop?.catalogue
    return when (fill) {
        Fill.WOOL -> Wishes.WOOL
        Fill.WOOD -> if (catalogue == null || catalogue.cheapest(WOOD) != null) WOOD else HARD_BLOCK
        Fill.END_STONE -> if (catalogue == null || catalogue.cheapest(END_STONE) != null) END_STONE else HARD_BLOCK
    }
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.defenceOffer(fill: Fill): Offer? = agent.skills.shop?.catalogue?.cheapest(blocks(fill))

/**
 * Buys [wish], or takes a tick of gathering for it; false when it cannot be bought at all.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.obtain(wish: Wish): Boolean {
    val offer = offer(wish) ?: return false
    if (affords(offer)) {
        shop(wish, offer)
        return true
    }
    if (isWool(wish)) say(Callout.NeedWool)
    gather(offer.currency)
    return true
}

/**
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.obtainBlocks(fill: Fill): Boolean {
    val offer = defenceOffer(fill) ?: return false
    return obtain(Wish("blocks", blocks(fill), ItemMatch.of(offer.stack.item)))
}

/**
 * GEAR: buys the next wish of the ladder it can pay for, or saves up for it at the own generator.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.gear() {
    while (true) {
        val wish = nextWish() ?: return
        val offer = offer(wish) ?: return
        if (affords(offer)) {
            shop(wish, offer)
        } else {
            gather(offer.currency)
            goal = "Saving up for ${wish.name}"
        }
        scope.nextTick()
    }
}

/**
 * UPGRADE: buys the team upgrade it wants, or the one it likes best of those it can pay for.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.upgrade() {
    while (true) {
        val name = upgradeToBuy()?.upgrade ?: return
        val wish = Wish.upgrade(name)
        shop(wish, offer(wish) ?: return)
        scope.nextTick()
    }
}

/**
 * Walks to the shopkeeper or the upgrades keeper, looks at them and starts the purchase; gathers for a tick instead
 * while the shop waits out a failed purchase.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.shop(wish: Wish, offer: Offer) {
    val shop = agent.skills.shop ?: return
    val at = (if (offer.upgrade != null) team.upgrader else team.shopkeeper) ?: return
    goal = "Buying ${wish.name}"
    plan.bridge(game, null)
    while (!Travel.isWithin(self.position(), at, SHOP_REACH)) {
        val status = if (isOnIsland()) {
            travel.walk(agent, at, SHOP_REACH - 1.5)
        } else {
            travel.bridgeTo(agent, at, SHOP_REACH - 1.5, tick)
        }
        if (status == Travel.Status.BLOCKED) return
        scope.nextTick()
    }
    look(at.add(0.0, 1.5, 0.0))
    if (shop.buy(wish)) {
        purchasing = offer.upgrade ?: ""
    } else {
        gather(offer.currency)
    }
}

/**
 * Whether a purchase still runs; the agent stands still meanwhile.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.shopping(): Boolean = agent.skills.shop?.isBuying == true

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.bought() {
    val upgrade = purchasing
    purchasing = null
    rethink()
    if (agent.skills.shop?.lastPurchase == true && !upgrade.isNullOrEmpty()) say(Callout.BoughtUpgrade(upgrade))
}

/**
 * What the agent's wishes still cost in [currency], with the blocks the bed defence lacks and its next upgrade.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.need(currency: Currency): Int {
    val wishes = wishes() ?: return 0
    var need = 0
    for (wish in wishes.ladder(self.inventory, senses.matchSeconds)) {
        if (has(wish)) continue
        val offer = offer(wish)
        if (offer != null && offer.currency == currency) need += cost(offer, wish.amount - count(wish.owned))
    }
    if (team.isBedAlive) {
        for ((fill, lacking) in plan.lacking(level)) {
            val blocks = blocks(fill)
            val missing = lacking - count(blocks)
            val offer = defenceOffer(fill)
            if (missing > 0 && offer != null && offer.currency == currency) need += cost(offer, missing)
        }
    }
    val upgrade = wantedUpgrade()
    if (upgrade != null && upgrade.currency == currency) need += upgrade.price
    return need
}

/**
 * @agent claude-opus-5-5
 */
private fun cost(offer: Offer, items: Int): Int {
    val amount = offer.stack.count.coerceAtLeast(1)
    return offer.price * maxOf(1, (items + amount - 1) / amount)
}

/**
 * Gets the rush kit on the island: wool for the bridge, a pickaxe for a covered bed, a sword and armor once the
 * opening rush is over, and whatever extra it can pay for; false once it has all of it.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.kitUp(target: BedWarsTeam): Boolean {
    val wool = Wishes.wool(rushWool(target), 0.0)
    if (!has(wool) && obtain(wool)) return true
    // A covered bed takes a pickaxe; the agent gathers for one rather than punching end stone
    val bed = target.bed
    if (bed != null && threats.isCovered(self, bed) && !has(Wishes.PICK) && obtain(Wishes.PICK)) return true
    val wishes = wishes() ?: return false
    if (senses.matchSeconds > RUSH_GEAR_SECONDS) {
        for (needed in listOf(Wishes.STONE_SWORD, Wishes.CHAIN_ARMOR)) {
            if (!has(needed) && obtain(wishes.best(self.inventory, needed))) return true
        }
    }
    for (extra in listOf(Wishes.STONE_SWORD, Wishes.CHAIN_ARMOR, Wishes.SHEARS)) {
        val wanted = wishes.best(self.inventory, extra)
        val offer = offer(wanted)
        if (!has(wanted) && offer != null && affords(offer)) {
            shop(wanted, offer)
            return true
        }
    }
    return false
}

private const val RUSH_GEAR_SECONDS = 90

/**
 * Wool for the bridge to [target] and some to spare, in stacks of 16; the bridge counted on the straight way.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.rushWool(target: BedWarsTeam): Int {
    if (target.name != rushWoolFor) {
        rushWoolFor = target.name
        val bed = target.bed?.let(Vec3::atCenterOf) ?: target.spawn
        val blocks = WayCost.of(terrain(), self.position(), bed).bridgeBlocks + 8 + (traits.aggression * 16).toInt()
        rushWool = ((blocks + WOOL_STACK - 1) / WOOL_STACK * WOOL_STACK).coerceIn(WOOL_STACK, 4 * WOOL_STACK)
    }
    return rushWool
}
