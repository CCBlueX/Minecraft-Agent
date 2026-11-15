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

import net.ccbluex.agent.skills.FightingStyle
import kotlin.math.roundToLong

/**
 * Something a fighter does besides hitting: raise a shield, eat, smash with a mace. A started skill runs every tick
 * until [tick] returns false; one of a higher priority may cut in while the running one is [interruptible].
 *
 * @agent claude-opus-5-5
 */
internal abstract class Technique(val kind: Skill, val priority: Int, val cooldown: Int) {

    var readyAt = 0L

    /**
     * Whether the moment for the skill has come. Changes nothing.
     */
    abstract fun can(fight: BuiltInFight): Boolean

    abstract fun begin(fight: BuiltInFight)

    /**
     * Runs on the ticks after [begin]; false ends the skill.
     */
    open fun tick(fight: BuiltInFight): Boolean = false

    /**
     * The skill ended or was cut off: put things back.
     */
    open fun end(fight: BuiltInFight) {}

    open val interruptible: Boolean
        get() = false

    /**
     * Whether the skill is what the mode is played with: it runs on every difficulty, which only makes it worse.
     */
    open val essential: Boolean
        get() = false

    /**
     * Whether the skill restores health, which [Fight.isHealing] reports.
     */
    open val heals: Boolean
        get() = false
}

/**
 * A skill that only starts while none of [others] could, such as a mace jump only when no crystal can go down.
 *
 * @agent claude-opus-5-5
 */
internal class Unless(private val skill: Technique, private val others: List<Technique>) :
    Technique(skill.kind, skill.priority, skill.cooldown) {

    override fun can(fight: BuiltInFight): Boolean = skill.can(fight) && others.none { it.can(fight) }

    override fun begin(fight: BuiltInFight) = skill.begin(fight)

    override fun tick(fight: BuiltInFight): Boolean = skill.tick(fight)

    override fun end(fight: BuiltInFight) = skill.end(fight)

    override val interruptible: Boolean
        get() = skill.interruptible
}

/**
 * Picks and runs the skills, highest priority first. A running skill that is not interruptible only ticks; any other
 * may be cut off by a skill of strictly higher priority. A skill that could start still has to pass the difficulty's
 * use chance (unless essential), retried a second later when it fails; its cooldown starts when it begins.
 *
 * @agent claude-opus-5-5
 */
internal class SkillRunner(skills: List<Technique>) {

    val skills: List<Technique> = skills.sortedByDescending { it.priority }

    var active: Technique? = null
        private set

    fun run(fight: BuiltInFight) {
        val running = active
        if (running != null && !running.interruptible) {
            if (!running.tick(fight)) end(fight)
            return
        }
        for (skill in skills) {
            val current = active
            if (current != null && skill.priority <= current.priority) break
            if (fight.tick < skill.readyAt || !skill.can(fight)) continue
            val favour = favour(fight.personality.fighting, skill.kind)
            if (!skill.essential && fight.random.nextDouble() >= fight.use.skill * favour) {
                skill.readyAt = fight.tick + RETRY_TICKS
                continue
            }
            end(fight)
            skill.readyAt = fight.tick + cooldownTicks(skill.cooldown, fight.use.cooldown, favour)
            active = skill
            skill.begin(fight)
            return
        }
        active?.let { if (!it.tick(fight)) end(fight) }
    }

    fun end(fight: BuiltInFight) {
        val skill = active ?: return
        active = null
        skill.end(fight)
    }

    /**
     * Whether a skill of [kind] is in the list and off cooldown within a second; never while no skill gets used.
     */
    fun readySoon(fight: BuiltInFight, kind: Skill): Boolean {
        if (fight.use.skill <= 0) return false
        val skill = skills.firstOrNull { it.kind == kind } ?: return false
        return fight.tick >= skill.readyAt - RETRY_TICKS
    }

    companion object {
        private const val RETRY_TICKS = 20

        /**
         * How much likelier a favoured skill is used, and how much shorter its cooldown is.
         */
        private const val FAVOUR = 2.0

        private val FAVOURED = mapOf(
            FightingStyle.DEFENSIVE to setOf(Skill.GUARD, Skill.GAPPLE, Skill.POT_HEAL, Skill.SOUP),
            FightingStyle.KITER to setOf(Skill.BOW, Skill.ROD),
            FightingStyle.TRICKSTER to setOf(Skill.PEARL, Skill.COBWEB),
        )

        fun favour(style: FightingStyle, kind: Skill): Double =
            if (FAVOURED[style]?.contains(kind) == true) FAVOUR else 1.0

        fun cooldownTicks(cooldown: Int, scale: Double, favour: Double): Long =
            (cooldown * scale / favour).roundToLong()
    }
}

/**
 * Every skill [rules] allow: each starts only once the inventory holds what it needs, so one list
 * serves every loadout. The mace jump waits while a crystal or anchor could go down instead.
 *
 * @agent claude-opus-5-5
 */
internal fun skillsFor(rules: Rules): List<Technique> {
    val allowed = rules.skills
    val crystal = Crystal().takeIf { Skill.CRYSTAL in allowed }
    val anchor = Anchor(ANCHOR_COOLDOWN).takeIf { Skill.ANCHOR in allowed }
    val blasts = listOfNotNull(Crystal().takeIf { crystal != null }, Anchor(ANCHOR_COOLDOWN).takeIf { anchor != null })
    val all = listOfNotNull(
        Totem(), WaterClutch(), WebEscape(), ShieldBreak(), Gapple(), Pillar(), Drain(), BlockPearl(), LavaTap(),
        anchor, crystal, CartBomb(), Unless(MaceSmash(MACE_MIN, MACE_MAX), blasts), Rocket(), Guard(rules.alwaysGuard),
        Cobweb(), PotHeal(), PotBuff(), Soup(), SpearCharge(), Rod(), Throw(), Bow(), Pearl(),
    )
    return all.filter { it.kind in allowed }
}

private const val ANCHOR_COOLDOWN = 60
private const val MACE_MIN = 1.5
private const val MACE_MAX = 6.0
