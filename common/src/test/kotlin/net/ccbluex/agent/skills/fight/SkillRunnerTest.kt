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

import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import java.lang.reflect.Proxy
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The skill scheduler: priorities, cutting in, use chance and cooldowns. The fight around it never touches its body
 * here, so the body is a proxy that fails on any call.
 *
 * @agent claude-opus-5-5
 */
class SkillRunnerTest {

    private class Probe(
        kind: Skill,
        priority: Int,
        cooldown: Int = 0,
        var startable: Boolean = true,
        override val interruptible: Boolean = false,
        override val essential: Boolean = false,
        var ticks: Int = 10,
    ) : Technique(kind, priority, cooldown) {
        var begun = 0
        var ended = 0

        override fun can(fight: BuiltInFight) = startable

        override fun begin(fight: BuiltInFight) {
            begun++
        }

        override fun tick(fight: BuiltInFight) = --ticks > 0

        override fun end(fight: BuiltInFight) {
            ended++
        }
    }

    private fun fight(
        skillLevel: SkillLevel = SkillLevel.HARD,
        style: FightingStyle = FightingStyle.NONE,
    ): BuiltInFight {
        val body = Proxy.newProxyInstance(
            AgentBody::class.java.classLoader, arrayOf(AgentBody::class.java),
        ) { _, method, _ ->
            error("the scheduler does not touch the body: ${method.name}")
        } as AgentBody
        return BuiltInFight(
            body, skillLevel, Personality.BALANCED.copy(fighting = style), Rules.DEFAULT, Random(1),
        )
    }

    @Test
    fun `the highest priority skill that can start starts`() {
        val low = Probe(Skill.BOW, 20)
        val high = Probe(Skill.GAPPLE, 70)
        val blocked = Probe(Skill.TOTEM, 100, startable = false)
        val runner = SkillRunner(listOf(low, high, blocked))
        runner.run(fight())
        assertSame(high, runner.active)
        assertEquals(0, low.begun)
    }

    @Test
    fun `a running skill that is not interruptible only ticks`() {
        val eating = Probe(Skill.GAPPLE, 70)
        val totem = Probe(Skill.TOTEM, 100, startable = false)
        val runner = SkillRunner(listOf(eating, totem))
        val fight = fight()
        runner.run(fight)
        totem.startable = true
        runner.run(fight)
        assertSame(eating, runner.active)
        assertEquals(0, totem.begun)
    }

    @Test
    fun `only a strictly higher priority cuts into an interruptible skill`() {
        val guard = Probe(Skill.GUARD, 40, interruptible = true)
        val same = Probe(Skill.COBWEB, 40, startable = false)
        val higher = Probe(Skill.SHIELD_BREAK, 80, startable = false)
        val runner = SkillRunner(listOf(guard, same, higher))
        val fight = fight()
        runner.run(fight)
        same.startable = true
        runner.run(fight)
        assertSame(guard, runner.active)
        higher.startable = true
        runner.run(fight)
        assertSame(higher, runner.active)
        assertEquals(1, guard.ended)
    }

    @Test
    fun `a skill ends when its tick says so`() {
        val shot = Probe(Skill.BOW, 20, ticks = 1)
        val runner = SkillRunner(listOf(shot))
        val fight = fight()
        runner.run(fight)
        shot.startable = false
        runner.run(fight)
        assertNull(runner.active)
        assertEquals(1, shot.ended)
    }

    @Test
    fun `beginners only use essential skills, the rest retry a second later`() {
        val bow = Probe(Skill.BOW, 20)
        val essential = Probe(Skill.THROW, 10, essential = true)
        val runner = SkillRunner(listOf(bow, essential))
        runner.run(fight(SkillLevel.BEGINNER))
        assertSame(essential, runner.active)
        assertEquals(20L, bow.readyAt)
    }

    @Test
    fun `cooldowns start at the begin, scaled by the skillLevel and shortened by favour`() {
        val gapple = Probe(Skill.GAPPLE, 70, cooldown = 60)
        SkillRunner(listOf(gapple)).run(fight(SkillLevel.HARD))
        assertEquals(42L, gapple.readyAt)
        assertEquals(78L, SkillRunner.cooldownTicks(60, SkillLevel.NORMAL.melee.cooldown, 1.0))
        assertEquals(39L, SkillRunner.cooldownTicks(60, SkillLevel.NORMAL.melee.cooldown, 2.0))
    }

    @Test
    fun `styles favour their skills`() {
        assertEquals(2.0, SkillRunner.favour(FightingStyle.DEFENSIVE, Skill.GUARD))
        assertEquals(2.0, SkillRunner.favour(FightingStyle.KITER, Skill.BOW))
        assertEquals(2.0, SkillRunner.favour(FightingStyle.TRICKSTER, Skill.PEARL))
        assertEquals(1.0, SkillRunner.favour(FightingStyle.AGGRESSIVE, Skill.GUARD))
    }

    @Test
    fun `unless holds a skill back while another could start`() {
        val crystal = Probe(Skill.CRYSTAL, 45)
        val mace = Unless(Probe(Skill.MACE_SMASH, 50), listOf(crystal))
        val fight = fight()
        assertEquals(false, mace.can(fight))
        crystal.startable = false
        assertEquals(true, mace.can(fight))
    }
}
