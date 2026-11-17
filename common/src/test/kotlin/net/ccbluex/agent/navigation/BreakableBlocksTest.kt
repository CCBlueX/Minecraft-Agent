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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.v26_3.toAgent
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On a server shared with players, the walk digs through the ground the world generated and leaves what they built.
 *
 * @agent claude-opus-5-5
 */
class BreakableBlocksTest {

    init {
        tags
    }

    @Test
    fun `the ground, stone, ores and trees are natural`() {
        val natural = listOf(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRAVEL, Blocks.SAND, Blocks.IRON_ORE,
            Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.NETHERRACK,
        )
        for (block in natural) {
            assertTrue(BreakableBlocks.NATURAL.isBreakable(block.defaultBlockState().toAgent()), "$block")
        }
    }

    @Test
    fun `what a player builds or keeps is not`() {
        val built = listOf(
            Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.GLASS, Blocks.CHEST, Blocks.FURNACE,
            Blocks.BRICKS, Blocks.FARMLAND, Blocks.STONE_BRICKS, Blocks.TORCH, Blocks.CRAFTING_TABLE,
        )
        for (block in built) {
            assertFalse(BreakableBlocks.NATURAL.isBreakable(block.defaultBlockState().toAgent()), "$block")
        }
    }

    private companion object {

        /**
         * The vanilla data pack's tags bound to the blocks, as a server binds them on start.
         */
        val tags by lazy {
            MinecraftBootstrap.ensureInitialized()
            val vanilla = ServerPacksSource.createVanillaPackSource().fullResources()
            val resources = MultiPackResourceManager(PackType.SERVER_DATA, listOf(vanilla))
            TagLoader.loadTagsForExistingRegistries(resources, RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC))
                .forEach { it.apply() }
        }
    }
}
