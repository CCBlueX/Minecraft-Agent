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

package net.ccbluex.agent.skills.maps

import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.TestInventories
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What a player on a flat floor would try first: people, buttons and openings around it.
 *
 * @agent claude-sonnet-5-5
 */
internal class LeadsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
            TestInventories.bootstrap()
        }
    }

    private val world = TestWorld.flat()
    private val level = TestLevel(world)
    private val player = level.player(TestWorld.feet(0, 1, 0))
    private val changes = Changes()
    private val leads = Leads()

    private fun interaction(x: Double, z: Double): Interaction =
        Interaction(EntityTypes.INTERACTION, level).also {
            it.setPos(x, 1.0, z)
            level.add(it)
        }

    private fun best(now: Int = 0) = leads.best(player, changes.also { it.tick(player) }, emptyList(), now, null)

    @Test
    fun `an interaction hitbox is used, the nearer of two first`() {
        val far = interaction(9.5, 0.5)
        val near = interaction(3.5, 0.5)
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.USE_ENTITY, lead.kind)
        assertEquals(near, lead.entity)
        leads.tried(lead, 0, worked = false)
        assertEquals(far, best(now = 1)?.entity)
    }

    @Test
    fun `a figure on show is walked up to, a hidden one is not`() {
        val stand = ArmorStand(EntityTypes.ARMOR_STAND, level).also {
            it.setPos(4.5, 1.0, 0.5)
            it.setInvisible(true)
            level.add(it)
        }
        assertNull(best())
        stand.setInvisible(false)
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.WALK_UP, lead.kind)
        assertEquals(stand, lead.entity)
    }

    @Test
    fun `buttons and levers are used`() {
        world.set(2, 1, 2, Blocks.STONE_BUTTON.defaultBlockState())
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.USE_BLOCK, lead.kind)
        assertEquals(BlockPos(2, 1, 2), lead.block)
    }

    @Test
    fun `a lead that answered is left alone for a while and offered again later`() {
        val box = interaction(3.5, 0.5)
        val lead = assertNotNull(best())
        leads.tried(lead, 0, worked = false)
        assertNull(best(now = 100))
        assertEquals(box, best(now = 20 * 45 + 1)?.entity)
    }

    @Test
    fun `a floor the map took away is stepped into`() {
        world.set(4, 0, 0, TestWorld.AIR)
        changes.tick(player)
        changes.blockChanged(BlockPos(4, 0, 0), TestWorld.AIR, player.position())
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.STEP_INTO, lead.kind)
        assertEquals(BlockPos(4, 0, 0), BlockPos.containing(lead.position))
    }

    @Test
    fun `other players are no lead`() {
        level.player(TestWorld.feet(3, 1, 0))
        assertNull(best())
    }

    @Test
    fun `a sign that toggles pushes the lead beside it away`() {
        val near = interaction(3.5, 0.5)
        val far = interaction(-8.5, 0.5)
        assertEquals(near, best()?.entity)
        val toggle = KnownMap.Sign(3, 1, 0, "Toggle music")
        assertEquals(far, leads.best(player, changes, listOf(toggle), 0, null)?.entity)
    }

    @Test
    fun `a person with a name over the head is walked up to where a player stands, even when invisible`() {
        val guide = ArmorStand(EntityTypes.ARMOR_STAND, level).also {
            it.setPos(4.5, 3.0, 0.5)
            it.setCustomName(Component.literal("Guide"))
            it.isCustomNameVisible = true
            it.setInvisible(true)
            level.add(it)
        }
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.WALK_UP, lead.kind)
        assertEquals(guide, lead.entity)
        assertEquals(Vec3(4.5, 1.0, 0.5), lead.position)
    }

    @Test
    fun `a wall taken away down to the floor is a door to step into`() {
        world.fill(4, 1, 0, 4, 2, 0)
        world.fill(4, 1, 0, 4, 2, 0, TestWorld.AIR)
        changes.tick(player)
        changes.blockChanged(BlockPos(4, 2, 0), TestWorld.AIR, player.position())
        changes.blockChanged(BlockPos(4, 1, 0), TestWorld.AIR, player.position())
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.STEP_INTO, lead.kind)
        assertEquals(Vec3(4.5, 1.0, 0.5), lead.position)
    }

    @Test
    fun `nothing is tried far off while a scene talks`() {
        interaction(30.5, 0.5)
        changes.tick(player)
        changes.chat("Welcome to the lab")
        assertNull(best())
        val near = interaction(10.5, 0.5)
        assertEquals(near, best()?.entity)
    }

    @Test
    fun `told to drop with wings on, the nearest edge over a deep drop is a way on`() {
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
        player.snapTo(Vec3(39.5, 1.0, 0.5), 0f, 0f)
        changes.tick(player)
        changes.chat("Jump off the ledge and glide down")
        val lead = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.STEP_INTO, lead.kind)
        assertEquals(Vec3(41.5, 1.0, 0.5), lead.position)
    }
}
