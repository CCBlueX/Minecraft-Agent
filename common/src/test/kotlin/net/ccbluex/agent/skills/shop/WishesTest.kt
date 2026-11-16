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

import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.TestInventories.stack
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import net.minecraft.world.item.Items as McItems

/**
 * The Bed Wars wish ladder, its tier skips and the team upgrade appeal, on a shop priced like a common Bed Wars default.
 *
 * @agent claude-opus-5-5
 */
class WishesTest {

    private val catalogue = TestCatalogue()

    private fun wishes(aggression: Double = 0.5, caution: Double = 0.5, greed: Double = 0.5) =
        Wishes(catalogue, Personality(aggression, caution, greed, 0.5, 0.5, 0.5, 1.0, FightingStyle.NONE), NoTaste)

    @Test
    fun ladderFollowsTraitsAndTime() {
        val inventory = TestInventories.of()
        val calm = wishes(aggression = 0.2).ladder(inventory, 100).map { it.name }
        assertEquals(
            listOf("wool", "stone sword", "chain armor", "iron sword", "iron armor", "golden apple", "diamond sword", "diamond armor"),
            calm,
        )
        val late = wishes(aggression = 0.6).ladder(inventory, 301).map { it.name }
        assertTrue(late.containsAll(listOf("pickaxe", "shears", "better pickaxe")), "$late")
        assertTrue(late.indexOf("pickaxe") < late.indexOf("iron sword"))
    }

    @Test
    fun woolIsUrgentWhenLow() {
        val low = wishes().ladder(TestInventories.of(stack(WOOL, 7)), 0).first()
        val some = wishes().ladder(TestInventories.of(stack(WOOL, 8)), 0).first()
        assertEquals(0.85, low.urgency)
        assertEquals(0.35, some.urgency)
    }

    @Test
    fun nextIsTheFirstAffordableWish() {
        val wishes = wishes()
        val inventory = TestInventories.of(stack(Items.IRON_INGOT, 4))
        assertEquals("wool", wishes.next(inventory, 0) { false }?.name)

        inventory.setItem(1, stack(WOOL, 16))
        assertNull(wishes.next(inventory, 0) { false })
        assertSame(Wishes.STONE_SWORD, wishes.next(inventory, 0) { it == Currency.IRON })
    }

    @Test
    fun aBetterTierIsBoughtStraightAway() {
        val wishes = wishes()
        val inventory = TestInventories.of(stack(WOOL, 16), stack(Items.IRON_INGOT, 10), stack(Items.GOLD_INGOT, 7))
        assertSame(Wishes.IRON_SWORD, wishes.next(inventory, 0) { false })
        inventory.setItem(3, stack(Items.EMERALD, 4))
        assertSame(Wishes.DIAMOND_SWORD, wishes.next(inventory, 0) { false })
    }

    @Test
    fun ownedWishesAreSkipped() {
        val wishes = wishes()
        val inventory = TestInventories.of(stack(WOOL, 16), stack(Items.IRON_SWORD), stack(Items.IRON_INGOT, 64))
        assertTrue(wishes.has(inventory, Wishes.STONE_SWORD))
        assertTrue(wishes.has(inventory, Wishes.IRON_SWORD))
        assertFalse(wishes.has(inventory, Wishes.DIAMOND_SWORD))
        assertSame(Wishes.CHAIN_ARMOR, wishes.next(inventory, 0) { false })
    }

    @Test
    fun traitsPickTheUpgrade() {
        assertEquals(Wishes.SWORDS, wishes(aggression = 0.9, caution = 0.1, greed = 0.1).wantedUpgrade(true)?.upgrade)
        assertEquals(Wishes.ARMOR, wishes(aggression = 0.1, caution = 0.9, greed = 0.1).wantedUpgrade(true)?.upgrade)
        assertEquals(Wishes.FORGE, wishes(aggression = 0.1, caution = 0.1, greed = 0.9).wantedUpgrade(true)?.upgrade)
        assertEquals(Wishes.SWORDS, wishes(aggression = 0.1, caution = 0.1, greed = 0.9).wantedUpgrade(false)?.upgrade)
    }

    @Test
    fun anAffordableUpgradeWhenTheWantedOneIsNot() {
        val wishes = wishes(aggression = 0.9, caution = 0.1, greed = 0.1)
        val poor = TestInventories.of(stack(Items.DIAMOND, 1))
        assertEquals(Wishes.HEAL_POOL, wishes.upgradeToBuy(poor, true)?.upgrade)
        val rich = TestInventories.of(stack(Items.DIAMOND, 8))
        assertEquals(Wishes.SWORDS, wishes.upgradeToBuy(rich, true)?.upgrade)
    }

    private object NoTaste : Random() {
        private fun readResolve(): Any = NoTaste
        override fun nextGaussian(): Double = 0.0
    }

    private companion object {
        init {
            TestInventories.bootstrap()
        }

        val WOOL: Item = McItems.WOOL.white().toAgent()
    }

    private class TestCatalogue : Catalogue {
        override val items = listOf(
            offer(WOOL, 16, Currency.IRON, 4),
            offer(Items.STONE_SWORD, 1, Currency.IRON, 10),
            offer(Items.IRON_SWORD, 1, Currency.GOLD, 7),
            offer(Items.DIAMOND_SWORD, 1, Currency.EMERALD, 4),
            offer(Items.CHAINMAIL_BOOTS, 1, Currency.IRON, 24),
            offer(Items.IRON_BOOTS, 1, Currency.GOLD, 12),
            offer(Items.DIAMOND_BOOTS, 1, Currency.EMERALD, 6),
            offer(Items.WOODEN_PICKAXE, 1, Currency.IRON, 10),
            offer(Items.IRON_PICKAXE, 1, Currency.IRON, 10),
            offer(Items.SHEARS, 1, Currency.IRON, 20),
            offer(Items.GOLDEN_APPLE, 1, Currency.GOLD, 3),
        )

        private val upgrades = mapOf(
            Wishes.FORGE to 2, Wishes.SWORDS to 4, Wishes.ARMOR to 2, Wishes.HEAL_POOL to 1, Wishes.MINER to 2,
            Wishes.TRAP to 1,
        )

        override fun upgrade(name: String): Offer? =
            upgrades[name]?.let { Offer(TestInventories.stack(Items.DIAMOND), Currency.DIAMOND, it, name) }

        override fun upgradeTier(name: String): Int = 0

        override val activeTraps: Int = 0

        private fun offer(item: Item, amount: Int, currency: Currency, price: Int) =
            Offer(TestInventories.stack(item, amount), currency, price)
    }
}
