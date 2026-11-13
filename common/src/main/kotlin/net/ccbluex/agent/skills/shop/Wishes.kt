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
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.itemId
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import java.util.Random

/**
 * What an agent means to buy in Bed Wars and in which order, and which team upgrade it saves for. The ladder and the
 * upgrade appeal lean on its [personality]; [random] gives each agent its own taste for every upgrade.
 *
 * @agent claude-opus-5-5
 */
internal class Wishes(private val catalogue: Catalogue, private val personality: Personality, random: Random) {

    private val upgradeTaste: Map<String, Double> =
        TEAM_UPGRADES.associateWith { random.nextGaussian() * UPGRADE_TASTE_SPREAD }

    /**
     * Everything the agent means to have, in the order it gets it.
     */
    fun ladder(inventory: Inventory, matchSeconds: Int): List<Wish> = buildList {
        add(wool(WOOL_STACK, if (count(inventory, WOOL) < LOW_WOOL) 0.85 else 0.35))
        add(STONE_SWORD)
        add(CHAIN_ARMOR)
        if (personality.aggression > 0.35 || matchSeconds > 240) add(PICK)
        add(IRON_SWORD)
        if (personality.aggression > 0.5) add(SHEARS)
        add(IRON_ARMOR)
        if (matchSeconds > 300) add(BETTER_PICK)
        add(GAPPLE)
        add(DIAMOND_SWORD)
        add(DIAMOND_ARMOR)
    }

    fun has(inventory: Inventory, wish: Wish): Boolean = count(inventory, wish.owned) >= wish.amount

    fun offer(wish: Wish): Offer? = catalogue.offer(wish)

    /**
     * The first missing wish the agent can pay for now, or a better tier of it it can also pay for; else the first
     * whose money [canGather] at home; null when neither.
     */
    fun next(inventory: Inventory, matchSeconds: Int, canGather: (Currency) -> Boolean): Wish? {
        var gatherable: Wish? = null
        for (wish in ladder(inventory, matchSeconds)) {
            if (has(inventory, wish)) continue
            val offer = offer(wish) ?: continue
            if (inventory.affords(offer)) return best(inventory, wish)
            if (gatherable == null && canGather(offer.currency)) gatherable = wish
        }
        return gatherable
    }

    /**
     * [wish], or a better tier of it when the agent can pay for that one.
     */
    fun best(inventory: Inventory, wish: Wish): Wish {
        val better = BETTER[wish] ?: return wish
        return if (!has(inventory, better) && inventory.affords(offer(better))) best(inventory, better) else wish
    }

    /**
     * How much the agent wants the team upgrade [name] next; its traits put Forge, Sharpness or Protection first.
     */
    fun appeal(name: String, offer: Offer, ownIronGenerator: Boolean): Double {
        val bought = catalogue.upgradeTier(name)
        val base = when (name) {
            FORGE -> if (ownIronGenerator) 0.45 + 0.6 * personality.greed - 0.15 * bought else -1.0
            SWORDS -> 0.4 + 0.6 * personality.aggression
            ARMOR -> 0.35 + 0.6 * personality.caution - 0.12 * bought
            HEAL_POOL -> 0.3 + 0.3 * personality.caution
            MINER -> 0.1 + 0.25 * personality.aggression
            TRAP -> if (catalogue.activeTraps == 0) 0.25 + 0.5 * personality.caution else 0.05
            else -> -1.0
        }
        return base + (upgradeTaste[name] ?: 0.0) - 0.02 * offer.price
    }

    /**
     * The team upgrade the agent saves for; null when the team has them all or wants none.
     */
    fun wantedUpgrade(ownIronGenerator: Boolean): Offer? = mostAppealing(ownIronGenerator) { true }

    /**
     * The wanted upgrade if the agent can pay for it, else the one it likes best of those it can pay for.
     */
    fun upgradeToBuy(inventory: Inventory, ownIronGenerator: Boolean): Offer? {
        val wanted = wantedUpgrade(ownIronGenerator)
        if (wanted != null && inventory.affords(wanted)) return wanted
        return mostAppealing(ownIronGenerator) { inventory.affords(it) }
    }

    private inline fun mostAppealing(ownIronGenerator: Boolean, allowed: (Offer) -> Boolean): Offer? {
        var best: Offer? = null
        var bestAppeal = 0.0
        for (name in TEAM_UPGRADES) {
            val offer = catalogue.upgrade(name) ?: continue
            if (!allowed(offer)) continue
            val appeal = appeal(name, offer, ownIronGenerator)
            if (appeal > bestAppeal) {
                best = offer
                bestAppeal = appeal
            }
        }
        return best
    }

    companion object {
        const val FORGE = "upgrade-forge"
        const val SWORDS = "upgrade-swords"
        const val ARMOR = "upgrade-armor"
        const val HEAL_POOL = "upgrade-heal-pool"
        const val MINER = "upgrade-miner"
        const val TRAP = "base-trap-1"
        val TEAM_UPGRADES = listOf(FORGE, SWORDS, ARMOR, HEAL_POOL, MINER, TRAP)

        private const val UPGRADE_TASTE_SPREAD = 0.12
        private const val WOOL_STACK = 16
        private const val LOW_WOOL = 8

        val WOOL: ItemMatch = ItemMatch.named("_wool")
        private val SWORD = ItemMatch { it.itemId.endsWith("_sword") && !it.`is`(Items.WOODEN_SWORD) }
        private val PICKAXE = ItemMatch.named("_pickaxe")

        val STONE_SWORD = Wish("stone sword", SWORD, ItemMatch.of(Items.STONE_SWORD), 1, 0.8)
        val IRON_SWORD = Wish(
            "iron sword", ItemMatch.of(Items.IRON_SWORD, Items.DIAMOND_SWORD), ItemMatch.of(Items.IRON_SWORD), 1, 0.55,
        )
        val DIAMOND_SWORD =
            Wish("diamond sword", ItemMatch.of(Items.DIAMOND_SWORD), ItemMatch.of(Items.DIAMOND_SWORD), 1, 0.6)
        val CHAIN_ARMOR = Wish(
            "chain armor", ItemMatch.of(Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS),
            ItemMatch.of(Items.CHAINMAIL_BOOTS), 1, 0.6,
        )
        val IRON_ARMOR = Wish(
            "iron armor", ItemMatch.of(Items.IRON_BOOTS, Items.DIAMOND_BOOTS), ItemMatch.of(Items.IRON_BOOTS), 1, 0.5,
        )
        val DIAMOND_ARMOR =
            Wish("diamond armor", ItemMatch.of(Items.DIAMOND_BOOTS), ItemMatch.of(Items.DIAMOND_BOOTS), 1, 0.6)
        val PICK = Wish("pickaxe", PICKAXE, ItemMatch.of(Items.WOODEN_PICKAXE), 1, 0.45)
        val BETTER_PICK = Wish(
            "better pickaxe", ItemMatch.of(Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE),
            ItemMatch.of(Items.IRON_PICKAXE), 1, 0.3,
        )
        val SHEARS = Wish("shears", ItemMatch.of(Items.SHEARS), ItemMatch.of(Items.SHEARS), 1, 0.3)
        val GAPPLE = Wish("golden apple", ItemMatch.of(Items.GOLDEN_APPLE), ItemMatch.of(Items.GOLDEN_APPLE), 1, 0.3)

        /**
         * The next tier of a sword or armor, bought straight away when the agent can pay for it.
         */
        private val BETTER = mapOf(
            STONE_SWORD to IRON_SWORD,
            IRON_SWORD to DIAMOND_SWORD,
            CHAIN_ARMOR to IRON_ARMOR,
            IRON_ARMOR to DIAMOND_ARMOR,
        )

        fun wool(amount: Int, urgency: Double): Wish = Wish("wool", WOOL, WOOL, amount, urgency)

        /**
         * Items [match] accepts in the inventory and worn, as a wish counts them.
         */
        fun count(inventory: Inventory, match: ItemMatch): Int =
            (0 until inventory.containerSize).sumOf { slot ->
                inventory.getItem(slot).let { if (match.matches(it)) it.count else 0 }
            }
    }
}
