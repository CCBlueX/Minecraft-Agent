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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.pathfinder.TestWorld
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.BlockHitResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.lang.reflect.Proxy
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wall block goes out only once the rotation the server has reaches the face it is placed against: while the turn is
 * on its way the click waits instead of being refused.
 *
 * @agent claude-opus-5-5
 */
class BuildingTest {

    init {
        TestInventories.bootstrap()
    }

    @Test
    fun `a wall block waits for the server's rotation and then goes on the top it looks at`() {
        val level = TestLevel(TestWorld.flat())
        val player = level.player(TestWorld.feet(0, 1, 0))
        player.inventory.setItem(0, ItemStack(Items.STONE, 16))
        player.inventory.selectedSlot = 0
        var sent = ViewRotation(90f, 0f)
        val clicks = ArrayList<BlockHitResult>()
        val body = Proxy.newProxyInstance(AgentBody::class.java.classLoader, arrayOf(AgentBody::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayer" -> player.toAgent()
                "getLevel" -> level.toAgent()
                "getSentRotation" -> sent
                "useItemOn" -> clicks.add(args[1] as BlockHitResult)
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    Integer.TYPE -> 0
                    java.lang.Float.TYPE -> 0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
        } as AgentBody
        val fight = BuiltInFight(body, SkillLevel.HARD, Personality.BALANCED, Rules.DEFAULT, Random(1))
        fight.navigator = Navigator(body)
        val east = Vec3(5.5, 1.0, 0.5)

        // Looking away: the server has not got the turn yet
        assertNull(Building.wall(fight, east, 0))
        assertTrue(clicks.isEmpty())

        sent = ViewRotation.fromVector(Building.top(BlockPos(1, 0, 0)).subtract(player.eyePosition.toAgent()))
        assertEquals(true, Building.wall(fight, east, 0))
        val click = clicks.single()
        assertEquals(BlockPos(1, 0, 0), click.blockPos)
        assertEquals(Direction.UP, click.direction)
    }
}
