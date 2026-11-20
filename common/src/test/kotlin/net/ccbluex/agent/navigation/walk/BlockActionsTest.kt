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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.TestBody
import net.ccbluex.agent.pathfinder.MinecraftBootstrap
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A walk opens a door in its way, looking straight ahead at it from the block before: at eye height the look meets the
 * upper half of the door, while the path names the lower one.
 *
 * @agent claude-opus-5-5
 */
internal class BlockActionsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val door = BlockPos(2, 1, 0)

    private fun actions(open: Boolean): Pair<TestBody, BlockActions> {
        val lower = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.EAST)
            .setValue(DoorBlock.OPEN, open)
        val world = TestWorld.flat()
            .set(door.x, door.y, door.z, lower)
            .set(door.x, door.y + 1, door.z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
        // Facing east, level, a block before the door.
        val body = TestBody(TestLevel(world).player(Vec3(1.2, 1.0, 0.5), -90f))
        val actions = BlockActions(body.context())
        actions.open(door)
        return body to actions
    }

    @Test
    fun `a door is used through its upper half`() {
        val (body, actions) = actions(open = false)
        actions.tick()

        assertEquals(listOf(door.above()), body.used.map { it.blockPos })
    }

    @Test
    fun `an open door is not aimed at while the server confirms it`() {
        val (body, actions) = actions(open = true)
        actions.aim()

        assertNull(body.looked)
    }
}
