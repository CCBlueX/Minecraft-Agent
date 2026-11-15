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
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Interaction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the map did around a player standing on a flat floor.
 *
 * @agent claude-sonnet-5-5
 */
internal class ChangesTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val level = TestLevel(TestWorld.flat())
    private val player = level.player(TestWorld.feet(0, 1, 0))
    private val changes = Changes()

    @Test
    fun `a jump of many blocks in one tick is a teleport from where the player was`() {
        changes.tick(player)
        val mark = changes.mark()
        player.snapTo(Vec3(30.5, 1.0, 0.5), 0f, 0f)
        changes.tick(player)
        val teleport = changes.since(mark).single()
        assertEquals(Changes.Kind.TELEPORT, teleport.kind)
        assertEquals(Vec3(0.5, 1.0, 0.5), teleport.position)
    }

    @Test
    fun `walking on is no teleport`() {
        changes.tick(player)
        val mark = changes.mark()
        player.snapTo(Vec3(0.5, 1.0, 1.5), 0f, 0f)
        changes.tick(player)
        assertTrue(changes.since(mark).isEmpty())
    }

    @Test
    fun `a chat line is kept without colours and a scene talks for a while`() {
        changes.tick(player)
        assertFalse(changes.isTalking)
        changes.chat("\u00a7aWelcome!")
        assertEquals("Welcome!", changes.lastChat)
        assertTrue(changes.isTalking)
        repeat(401) { changes.tick(player) }
        assertFalse(changes.isTalking)
    }

    @Test
    fun `blocks the server took away and set are told apart, far ones are not noticed`() {
        changes.tick(player)
        val mark = changes.mark()
        changes.tick(player)
        val here = player.position()
        changes.blockChanged(BlockPos(2, 0, 0), Blocks.AIR.defaultBlockState(), here)
        changes.blockChanged(BlockPos(3, 1, 0), Blocks.STONE.defaultBlockState(), here)
        changes.blockChanged(BlockPos(300, 1, 0), Blocks.STONE.defaultBlockState(), here)
        assertEquals(setOf(Changes.Kind.SET, Changes.Kind.REMOVED), changes.since(mark).map { it.kind }.toSet())
        assertEquals(BlockPos(2, 0, 0), BlockPos.containing(changes.removed.single().position))
    }

    @Test
    fun `an entity that comes up near the player is told once the first scan is done`() {
        val first = Interaction(EntityTypes.INTERACTION, level).also { it.setPos(5.5, 1.0, 0.5) }
        level.add(first)
        repeat(5) { changes.tick(player) }
        assertTrue(changes.appeared.isEmpty())
        val second = Interaction(EntityTypes.INTERACTION, level).also { it.setPos(-5.5, 1.0, 0.5) }
        level.add(second)
        repeat(5) { changes.tick(player) }
        assertEquals(listOf(second.id), changes.appeared.map { it.entity })
    }
}
