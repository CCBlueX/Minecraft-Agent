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

import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.TestInventories
import net.ccbluex.agent.skills.TestInventories.stack
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items
import java.lang.reflect.Proxy
import java.util.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A kiter holds back only for a bow or rod it carries and could use on the target: without one it closes in.
 *
 * A hard bot against a beginner on a 25-block island over the void: it had circled to the outside of its rival,
 * jumped for a crit 0.6 blocks from the east edge with its back to the void, and the rival's sprint hit carried it
 * 6 blocks off.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
class FootworkTest {

    @BeforeTest
    fun bootstrap() = TestInventories.bootstrap()

    private val target = proxy<LivingEntity> { error("the keep distance does not read the target: $it") }

    @Test
    fun `a kiter with only a sword closes in`() {
        assertEquals(0.0, keep(stack(Items.DIAMOND_SWORD)))
    }

    @Test
    fun `a kiter holds back for a rod or a loaded bow in sight`() {
        assertTrue(keep(stack(Items.DIAMOND_SWORD), stack(Items.FISHING_ROD)) > 0)
        assertTrue(keep(stack(Items.BOW), stack(Items.ARROW)) > 0)
    }

    @Test
    fun `a bow without arrows is no reason to hold back`() {
        assertEquals(0.0, keep(stack(Items.DIAMOND_SWORD), stack(Items.BOW)))
    }

    @Test
    fun `a kiter closes in on a target out of sight`() {
        assertEquals(0.0, keep(stack(Items.FISHING_ROD), stack(Items.BOW), stack(Items.ARROW), sees = false))
    }

    private fun hasGround(point: Vec3) = Mth.floor(point.x) in 20468..20492 && Mth.floor(point.z) in 756..780

    private val edge = Vec3(20492.372, 100.0, 772.193)
    private val rival = Vec3(20489.781, 100.827, 773.301)

    @Test
    fun `a sprint hit knocks the fighter off with its back to the edge, not in the middle`() {
        assertTrue(Footwork.canKnockOff(rival, edge, ::hasGround))
        assertFalse(Footwork.canKnockOff(Vec3(20489.0, 100.0, 772.0), Vec3(20486.0, 100.0, 772.0), ::hasGround))
    }

    @Test
    fun `the middle of the ground lies inland of the edge`() {
        val centre = assertNotNull(Footwork.groundCentre(edge, ::hasGround))
        assertTrue(centre.x < edge.x - 2, "inland: $centre")
    }

    private fun keep(vararg stacks: ItemStack, sees: Boolean = true): Double {
        val inventory = TestInventories.of(*stacks)
        val player = proxy<Player> {
            when (it) {
                "getInventory" -> inventory
                "getMainHandItem" -> inventory.getItem(0)
                "getOffhandItem" -> ItemStack.EMPTY
                "hasLineOfSight" -> sees
                else -> error("the keep distance does not read the player's $it")
            }
        }
        val body = proxy<AgentBody> {
            when (it) {
                "getPlayer" -> player
                "getAttackStrength" -> 1f
                "isCombat1_8" -> false
                else -> error("the keep distance does not touch the body: $it")
            }
        }
        val personality = Personality.BALANCED.copy(fighting = FightingStyle.KITER)
        return BuiltInFight(body, SkillLevel.HARD, personality, Rules.DEFAULT, Random(1)).footwork.keep(target)
    }

    private inline fun <reified T> proxy(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name)
        } as T
}
