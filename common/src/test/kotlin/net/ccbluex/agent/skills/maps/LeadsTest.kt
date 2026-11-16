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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.entity.Entity
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
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

        // A test level has no chunks to read: look at every position of a thin slab around the player
        private val SWITCHES = Switches { level, center, action ->
            for (pos in BlockPos.betweenClosed(center.offset(-Switches.RANGE, -1, -Switches.RANGE), center.offset(Switches.RANGE, 1, Switches.RANGE))) {
                val state = level.getBlockState(pos)
                if (state.`is`(BlockKind.BUTTON) || state.`is`(BlockKind.LEVER) || state.`is`(BlockKind.PRESSURE_PLATE)) {
                    action(pos.immutable(), state.block)
                }
            }
        }
    }

    private val world = TestWorld.flat()
    private val level = TestLevel(world)
    private val handle = level.player(TestWorld.feet(0, 1, 0))
    private val player = handle.toAgent()
    private val changes = Changes()
    private val leads = Leads(SWITCHES)

    private fun interaction(x: Double, z: Double): Entity =
        Interaction(EntityTypes.INTERACTION, level).also {
            it.setPos(x, 1.0, z)
            level.add(it)
        }.toAgent()

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
        assertEquals(stand.toAgent(), lead.entity)
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
        assertEquals(guide.toAgent(), lead.entity)
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
    fun `told to drop with wings on, no edge is guessed`() {
        handle.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
        handle.snapTo(Vec3(39.5, 1.0, 0.5).toMinecraft(), 0f, 0f)
        changes.tick(player)
        changes.chat("Jump off the ledge and glide down")
        assertNull(best())
    }

    @Test
    fun `a pressure plate is stepped on, a button far off is used`() {
        world.set(30, 1, 0, Blocks.STONE_PRESSURE_PLATE.defaultBlockState())
        val plate = assertNotNull(best())
        assertEquals(Leads.Lead.Kind.STEP_INTO, plate.kind)
        assertEquals(BlockPos(30, 1, 0), plate.block)
        leads.tried(plate, 0, worked = true)
        world.set(-40, 1, 0, Blocks.STONE_BUTTON.defaultBlockState())
        val button = assertNotNull(best(now = 1))
        assertEquals(Leads.Lead.Kind.USE_BLOCK, button.kind)
        assertEquals(BlockPos(-40, 1, 0), button.block)
    }

    @Test
    fun `a hatch that opened behind the player is found within 32 blocks, not beyond`() {
        world.set(-20, 0, 0, TestWorld.AIR)
        changes.tick(player)
        changes.blockChanged(BlockPos(-20, 0, 0), TestWorld.AIR, player.position())
        assertEquals(BlockPos(-20, 0, 0), BlockPos.containing(assertNotNull(best()).position))

        val far = Changes()
        world.set(-40, 0, 0, TestWorld.AIR)
        far.tick(player)
        far.blockChanged(BlockPos(-40, 0, 0), TestWorld.AIR, player.position())
        assertNull(Leads(SWITCHES).best(player, far, emptyList(), 0, null))
    }
}
