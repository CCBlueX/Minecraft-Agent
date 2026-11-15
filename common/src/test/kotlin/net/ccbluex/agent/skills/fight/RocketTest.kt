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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.TestInventories.stack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When a rocket flight turns into a mace dive, and where the elytra goes then.
 *
 * @agent claude-opus-5-5
 */
class RocketTest {

    @BeforeTest
    fun bootstrap() = TestInventories.bootstrap()

    @Test
    fun `an empty hotbar slot takes the elytra`() {
        val inventory = TestInventories.of(stack(Items.MACE), stack(Items.AIR), stack(Items.FIREWORK_ROCKET))
        assertEquals(1 to null, diveSlot(inventory::getItem))
    }

    @Test
    fun `a full hotbar moves a stack other than the mace into the rest of the inventory`() {
        // The elytra kit fills all nine hotbar slots, the mace first
        val inventory = TestInventories.of(
            stack(Items.MACE), stack(Items.NETHERITE_SWORD), stack(Items.BOW), stack(Items.FIREWORK_ROCKET),
            stack(Items.GOLDEN_APPLE), stack(Items.COOKED_BEEF), stack(Items.TOTEM_OF_UNDYING),
            stack(Items.FIREWORK_ROCKET), stack(Items.ARROW), stack(Items.NETHERITE_BOOTS),
        )
        assertEquals(1 to 10, diveSlot(inventory::getItem))
    }

    @Test
    fun `a full inventory leaves no slot`() {
        val inventory = TestInventories.of(*Array(36) { stack(Items.COBBLESTONE) })
        assertNull(diveSlot(inventory::getItem))
    }

    private val target = Vec3.ZERO

    @Test
    fun `a glider heading for the target dives short of it, its speed carrying it the rest`() {
        assertTrue(divesOnto(Vec3(-10.0, 20.0, 0.0), Vec3(1.0, 0.0, 0.0), target))
        assertFalse(divesOnto(Vec3(-20.0, 20.0, 0.0), Vec3(1.0, 0.0, 0.0), target))
    }

    @Test
    fun `a glider past the target or too low does not dive`() {
        // High above and sixteen blocks off, flying away: the old height rule dived here and fell beside the target
        assertFalse(divesOnto(Vec3(0.0, 44.0, -16.0), Vec3(0.0, 0.0, -1.0), target))
        assertFalse(divesOnto(Vec3(0.0, 4.0, 0.0), Vec3.ZERO, target))
        assertTrue(divesOnto(Vec3(0.0, 12.0, 1.0), Vec3.ZERO, target))
    }
}
