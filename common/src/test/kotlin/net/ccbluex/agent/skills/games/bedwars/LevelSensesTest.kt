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

import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.games.BLOCKS_AROUND_ORIGIN
import net.ccbluex.agent.skills.games.FakeRoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.playerNamed
import net.ccbluex.agent.skills.games.scanNow
import net.ccbluex.agent.skills.shop.Currency
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerRO
import net.minecraft.world.level.chunk.Strategy
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Bed Wars senses over a flat map with a red island (the agent, Me, and Ally) around x = 10 and a blue one (Foe)
 * around x = -10, each with a bed.
 *
 * @agent claude-sonnet-5-5
 */
internal class LevelSensesTest {

    companion object {
        init {
            TestInventories.bootstrap()
        }

        private val RED = TeamFacts("Red", DyeColor.RED, setOf("Me", "Ally"), isBedAlive = true)
        private val BLUE = TeamFacts("Blue", DyeColor.BLUE, setOf("Foe"), isBedAlive = true)
    }

    private class FakeFacts : FakeRoundFacts(), LevelSenses.Facts {
        override var ownTeamName: String? = "Red"
        override var teams = listOf(RED, BLUE)

        override fun isShopkeeper(entity: Entity): Boolean = entity.customName?.string == "ITEM SHOP"

        override fun isUpgrader(entity: Entity): Boolean = entity.customName?.string == "TEAM UPGRADES"
    }

    private class Round {
        val world = TestWorld.flat()
            .set(10, 1, 0, Blocks.BED.red().defaultBlockState())
            .set(-10, 1, 0, Blocks.BED.blue().defaultBlockState())
        val level = TestLevel(world)
        val me = level.playerNamed("Me", TestWorld.feet(10, 1, 3))
        val ally = level.playerNamed("Ally", TestWorld.feet(12, 1, 3))
        val foe = level.playerNamed("Foe", TestWorld.feet(-10, 1, 3))
        val facts = FakeFacts()
        val senses = LevelSenses(level, facts, BLOCKS_AROUND_ORIGIN)
    }

    @Test
    fun teamsOwnTheBedOfTheirColour() {
        val round = Round()
        round.senses.tick()
        val senses = round.senses
        assertEquals("Red", senses.ownTeam?.name)
        assertEquals(listOf("Red", "Blue"), senses.teams.map { it.name })
        assertEquals(BlockPos(10, 1, 0), senses.teams[0].bed)
        assertEquals(BlockPos(-10, 1, 0), senses.teams[1].bed)
        assertTrue(senses.teams.all { it.isBedAlive })
        assertEquals(listOf(round.me, round.ally), senses.teams[0].members)
        assertSame(senses.teams[1], senses.teamOf(round.foe))
    }

    @Test
    fun ownTeamIsTheOneWithTheAgentWhenTheTextDoesNotNameIt() {
        val round = Round()
        round.facts.ownTeamName = null
        round.senses.tick()
        assertEquals("Red", round.senses.ownTeam?.name)
    }

    @Test
    fun bedIsDeadWhenTheTextSaysSoOrTheBlockIsGone() {
        val round = Round()
        round.senses.tick()
        round.facts.teams = listOf(RED, BLUE.copy(isBedAlive = false))
        round.senses.scanNow()
        val blue = round.senses.teams[1]
        assertFalse(blue.isBedAlive)
        assertEquals(BlockPos(-10, 1, 0), blue.bed)

        round.facts.teams = listOf(RED, BLUE)
        round.world.set(-10, 1, 0, TestWorld.AIR)
        round.senses.scanNow()
        val gone = round.senses.teams[1]
        assertNull(gone.bed)
        assertFalse(gone.isBedAlive)
        assertNotEquals(Vec3.ZERO, gone.spawn)
        assertTrue(round.senses.teams[0].isBedAlive)
    }

    @Test
    fun spawnIsWhereTheAgentStoodAtTheStart() {
        val round = Round()
        round.facts.state = RoundState.COUNTDOWN
        round.senses.tick()
        assertNull(round.senses.ownSpawn)

        round.facts.state = RoundState.PLAYING
        round.senses.tick()
        val start = round.me.position()
        assertEquals(start, round.senses.ownSpawn)

        round.me.snapTo(TestWorld.feet(0, 1, 0), 0f, 0f)
        round.senses.scanNow()
        assertEquals(start, round.senses.ownSpawn)
        assertEquals(start, round.senses.ownTeam?.spawn)
    }

    @Test
    fun spawnIsTheIslandWhenTheServerTeleportsAfterTheStart() {
        val round = Round()
        val lobby = TestWorld.feet(60, 1, 3)
        val island = round.me.position()
        round.me.snapTo(lobby, 0f, 0f)
        round.facts.state = RoundState.PLAYING
        round.senses.tick()
        assertEquals(lobby, round.senses.ownSpawn)

        round.me.snapTo(island, 0f, 0f)
        round.senses.scanNow()
        assertEquals(island, round.senses.ownSpawn)
        assertEquals(island, round.senses.ownTeam?.spawn)
    }

    @Test
    fun bedsGoByPlaceWhenEveryBedIsRed() {
        val round = Round()
        round.world.set(-10, 1, 0, Blocks.BED.red().defaultBlockState())
        round.facts.state = RoundState.PLAYING
        round.senses.tick()
        assertEquals(BlockPos(10, 1, 0), round.senses.teams[0].bed)
        assertEquals(BlockPos(-10, 1, 0), round.senses.teams[1].bed)
    }

    @Test
    fun blocksPlacedWhilePlayingAreThePlayers() {
        val round = Round()
        val pos = BlockPos(3, 1, 3)
        round.facts.state = RoundState.COUNTDOWN
        round.senses.received(ClientboundBlockUpdatePacket(pos, TestWorld.STONE))
        assertFalse(round.senses.isPlayerPlaced(pos))

        round.facts.state = RoundState.PLAYING
        round.senses.received(ClientboundBlockUpdatePacket(BlockPos.MutableBlockPos(3, 1, 3), TestWorld.STONE))
        assertTrue(round.senses.isPlayerPlaced(pos))

        round.senses.received(ClientboundBlockUpdatePacket(pos, TestWorld.AIR))
        assertFalse(round.senses.isPlayerPlaced(pos))
    }

    @Test
    fun sectionUpdatesAreServerWordToo() {
        val round = Round()
        round.facts.state = RoundState.PLAYING
        val pos = BlockPos(3, 1, 3)
        val states = PalettedContainer(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY))
        // The packet reads no biomes
        @Suppress("UNCHECKED_CAST")
        val section = LevelChunkSection(states, states as PalettedContainerRO<Holder<Biome>>)
        section.setBlockState(3, 1, 3, TestWorld.STONE)
        val changed = ShortOpenHashSet().apply { add(SectionPos.sectionRelativePos(pos)) }
        round.senses.received(ClientboundSectionBlocksUpdatePacket(SectionPos.of(pos), changed, section))
        assertTrue(round.senses.isPlayerPlaced(pos))
        assertFalse(round.senses.isPlayerPlaced(pos.above()))
    }

    @Test
    fun otherPacketsPlaceNothing() {
        val round = Round()
        round.facts.state = RoundState.PLAYING
        round.senses.received(ClientboundBlockDestructionPacket(1, BlockPos(3, 1, 3), 5))
        assertFalse(round.senses.isPlayerPlaced(BlockPos(3, 1, 3)))
    }

    @Test
    fun opponentsAreLivingPlayersOfOtherTeams() {
        val round = Round()
        val ghost = round.level.playerNamed("Ghost", TestWorld.feet(-12, 1, 3), GameType.SPECTATOR)
        val pal = round.level.playerNamed("Pal", TestWorld.feet(-12, 1, 5))
        val npc = round.level.playerNamed("Npc", TestWorld.feet(-12, 1, 7))
        round.facts.friends += "Pal"
        round.facts.bots += "Npc"
        round.senses.tick()
        val senses = round.senses

        assertTrue(senses.isOpponent(round.foe))
        assertFalse(senses.isOpponent(round.ally))
        assertFalse(senses.isOpponent(round.me))
        assertFalse(senses.isOpponent(ghost))
        assertFalse(senses.isOpponent(pal))
        assertFalse(senses.isOpponent(npc))
        assertTrue(senses.isTeammate(round.ally))
        assertFalse(senses.isTeammate(round.foe))
        assertFalse(senses.isSpectator)
    }

    @Test
    fun spectatorIsOutOfTheLevelOrInSpectatorMode() {
        val level = TestLevel(TestWorld.flat())
        val facts = FakeFacts()
        val senses = LevelSenses(level, facts, BLOCKS_AROUND_ORIGIN)
        assertTrue(senses.isSpectator)

        level.playerNamed("Me", TestWorld.feet(0, 1, 0), GameType.SPECTATOR)
        assertTrue(senses.isSpectator)
    }

    @Test
    fun generatorsAreTheIronByTheBedAndTheDiamondsBetweenIslands() {
        val round = Round()
        val iron = ItemEntity(round.level, 11.5, 1.0, 0.5, ItemStack(Items.IRON_INGOT))
        val diamond = ItemEntity(round.level, 0.5, 1.0, 0.5, ItemStack(Items.DIAMOND))
        val stray = ItemEntity(round.level, 8.5, 1.0, 20.5, ItemStack(Items.IRON_INGOT))
        round.level.add(iron).add(diamond).add(stray)
        round.senses.tick()
        assertEquals(
            listOf(
                Generator(Currency.IRON, iron.position(), "Red"),
                Generator(Currency.DIAMOND, diamond.position(), null),
            ),
            round.senses.generators,
        )

        iron.discard()
        round.senses.scanNow()
        assertTrue(Generator(Currency.IRON, iron.position(), "Red") in round.senses.generators)
    }

    @Test
    fun shopkeepersStandByTheirOwnIsland() {
        val round = Round()
        val shop = ArmorStand(round.level, 15.5, 1.0, 0.5)
        shop.customName = Component.literal("ITEM SHOP")
        val upgrades = ArmorStand(round.level, 16.5, 1.0, 3.5)
        upgrades.customName = Component.literal("TEAM UPGRADES")
        round.level.add(shop).add(upgrades)
        round.senses.tick()
        val red = round.senses.teams[0]
        assertEquals(shop.position(), red.shopkeeper)
        assertEquals(upgrades.position(), red.upgrader)
        assertNull(round.senses.teams[1].shopkeeper)
        assertNull(round.senses.teams[1].upgrader)
    }
}
