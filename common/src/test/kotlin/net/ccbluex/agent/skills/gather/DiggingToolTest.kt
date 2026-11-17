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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.navigation.isToolFor
import net.ccbluex.agent.navigation.isWearFree
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which tool digs a block: only one made for it, as any other tool wears out for nothing; a sword is never one, but
 * for cobwebs.
 *
 * @agent claude-opus-5-5
 */
class DiggingToolTest {

    init {
        TestInventories.bootstrap()
        tags
    }

    @Test
    fun `a tool digs what it is made for`() {
        assertTrue(isToolFor(TestInventories.stack(Items.STONE_PICKAXE), state(Blocks.STONE)))
        assertTrue(isToolFor(TestInventories.stack(Items.WOODEN_AXE), state(Blocks.OAK_LOG)))
        assertTrue(isToolFor(TestInventories.stack(Items.WOODEN_SHOVEL), state(Blocks.DIRT)))
        assertTrue(isToolFor(TestInventories.stack(Items.STONE_SWORD), state(Blocks.COBWEB)))
    }

    @Test
    fun `no tool digs what it is not made for`() {
        assertFalse(isToolFor(TestInventories.stack(Items.STONE_PICKAXE), state(Blocks.DIRT)))
        assertFalse(isToolFor(TestInventories.stack(Items.STONE_SWORD), state(Blocks.OAK_LEAVES)))
        assertFalse(isToolFor(TestInventories.stack(Items.STONE_SWORD), state(Blocks.OAK_LOG)))
        assertFalse(isToolFor(TestInventories.stack(Items.WOODEN_PICKAXE), state(Blocks.IRON_ORE)))
    }

    @Test
    fun `only tools wear from digging`() {
        assertTrue(isWearFree(ItemStack.EMPTY))
        assertTrue(isWearFree(TestInventories.stack(Items.COBBLESTONE)))
        assertTrue(isWearFree(TestInventories.stack(Items.BOW)))
        assertFalse(isWearFree(TestInventories.stack(Items.STONE_SWORD)))
        assertFalse(isWearFree(TestInventories.stack(Items.WOODEN_PICKAXE)))
    }

    private fun state(block: Block): BlockState = block.defaultBlockState().toAgent()

    private companion object {

        /**
         * The vanilla data pack's tags bound to the blocks, as a server binds them on start: they say which blocks
         * each tool is for.
         */
        val tags by lazy {
            val vanilla = ServerPacksSource.createVanillaPackSource().fullResources()
            val resources = MultiPackResourceManager(PackType.SERVER_DATA, listOf(vanilla))
            TagLoader.loadTagsForExistingRegistries(resources, RegistryLayer.createRegistryAccess().getLayer(RegistryLayer.STATIC))
                .forEach { it.apply() }
        }
    }
}
