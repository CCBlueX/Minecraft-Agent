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

package net.ccbluex.agent.v26_3

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.bedwars.LevelSenses
import net.ccbluex.agent.skills.games.bedwars.TeamFacts
import net.ccbluex.agent.world.entity.Entity
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerRO
import net.minecraft.world.level.chunk.Strategy
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.ccbluex.agent.math.BlockPos as AgentBlockPos

/**
 * The packets the server sends, fed to the Bed Wars senses of a level.
 *
 * @agent claude-sonnet-5-5
 */
@OptIn(ExperimentalAgentApi::class)
class PacketsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private class Facts : LevelSenses.Facts {
        override var state = RoundState.PLAYING
        override val countdownSeconds: Int? = null
        override val matchSeconds = 0
        override val ownName: String? = "Me"
        override val ownTeamName: String? = null
        override val teams: List<TeamFacts> = emptyList()

        override fun isShopkeeper(entity: Entity): Boolean = false

        override fun isUpgrader(entity: Entity): Boolean = false
    }

    private val facts = Facts()
    private val senses = LevelSenses(TestLevel(TestWorld.flat()).toAgent(), facts)

    @Test
    fun `block updates are placed blocks while playing and breaks when air`() {
        val pos = BlockPos(3, 1, 3)
        facts.state = RoundState.COUNTDOWN
        senses.received(ClientboundBlockUpdatePacket(pos, TestWorld.STONE.toMinecraft()))
        assertFalse(senses.isPlayerPlaced(pos.toAgent()))

        facts.state = RoundState.PLAYING
        senses.received(ClientboundBlockUpdatePacket(BlockPos.MutableBlockPos(3, 1, 3), TestWorld.STONE.toMinecraft()))
        assertTrue(senses.isPlayerPlaced(pos.toAgent()))

        senses.received(ClientboundBlockUpdatePacket(pos, TestWorld.AIR.toMinecraft()))
        assertFalse(senses.isPlayerPlaced(pos.toAgent()))
    }

    @Test
    fun `section updates are the server's word too`() {
        val pos = BlockPos(3, 1, 3)
        val states = PalettedContainer(Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY))
        // The packet reads no biomes
        @Suppress("UNCHECKED_CAST")
        val section = LevelChunkSection(states, states as PalettedContainerRO<Holder<Biome>>)
        section.setBlockState(3, 1, 3, TestWorld.STONE.toMinecraft())
        val changed = ShortOpenHashSet().apply { add(SectionPos.sectionRelativePos(pos)) }
        senses.received(ClientboundSectionBlocksUpdatePacket(SectionPos.of(pos), changed, section))
        assertTrue(senses.isPlayerPlaced(pos.toAgent()))
        assertFalse(senses.isPlayerPlaced(pos.above().toAgent()))
    }

    @Test
    fun `other packets place nothing`() {
        senses.received(ClientboundBlockDestructionPacket(1, BlockPos(3, 1, 3), 5))
        assertFalse(senses.isPlayerPlaced(AgentBlockPos(3, 1, 3)))
    }
}
