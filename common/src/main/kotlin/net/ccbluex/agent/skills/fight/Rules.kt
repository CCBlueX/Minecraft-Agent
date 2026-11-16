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

import net.ccbluex.agent.math.Vec3
import java.util.EnumSet

/**
 * What the server grants a fighter. The built-in fight picks its skills from what the inventory holds; these rules
 * only take away what the server forbids or change what its mode plays differently.
 *
 * @property skills skills allowed at all
 * @property oldCombat the server plays 1.8 combat on a newer game, as OldCombatMechanics does: clicks at a rate and
 *   sword block-hits instead of waiting for the attack charge. A body under 1.8 rules does so anyway.
 * @property crits whether jumping for critical hits pays off; off in knockback modes such as sumo
 * @property hitReactions whether a hit taken is met with a jump reset and a return hit
 * @property attacks whether the fighter hits at all outside its skills; off for a shield-only practice opponent
 * @property alwaysGuard keeps the shield up whenever it works instead of only against an approaching target
 * @property instantSoup a stew heals at once on use, as soup modes have it, instead of being eaten
 * @property blastWeight 0 to 1: how much the mode leans on blasts and one-go skills (crystals, anchors, maces), which
 *   [net.ccbluex.agent.skills.SkillLevel.use] mixes into how skills are used
 * @property ringCentre a ring to stay in (sumo): strafes lean towards it
 * @property guardsEdges holds the fighter at edges over the void instead of stepping off towards a target
 * @property retaliates fights back against whoever hit it while no target is given
 * @property hunts goes for the nearest opponent in sight while no target is given
 * @property range how far a retaliation or hunt reaches, in blocks
 * @property area fights only opponents inside and places blocks, crystals, anchors and buckets only inside; a target
 *   given by [Fight.engage] is fought wherever it is. Null: no limit.
 * @property reach attack reach in blocks; 0 keeps the held item's reach, more replaces its maximum and drops its
 *   minimum to 0
 * @property strafe which way the fighter strafes when close
 * @property cps clicks per second under 1.8 combat, before the difficulty multiplies it
 * @property reaction ticks before the fighter reacts to a new target, before the difficulty multiplies it
 * @property critChance 0 to 1: how often a hit is a jumping crit, before the difficulty multiplies it
 * @property wtapChance 0 to 1: how often a hit resets sprint, before the difficulty and style multiply it
 * @agent claude-opus-5-5
 */
public data class Rules @JvmOverloads constructor(
    val skills: Set<Skill> = EnumSet.allOf(Skill::class.java),
    val oldCombat: Boolean = false,
    val crits: Boolean = true,
    val hitReactions: Boolean = true,
    val attacks: Boolean = true,
    val alwaysGuard: Boolean = false,
    val instantSoup: Boolean = false,
    val blastWeight: Double = 0.0,
    val ringCentre: Vec3? = null,
    val guardsEdges: Boolean = true,
    val retaliates: Boolean = true,
    val hunts: Boolean = false,
    val range: Double = 16.0,
    val area: Area? = null,
    val reach: Double = 0.0,
    val strafe: Strafe = Strafe.FREE,
    val cps: Double = 8.0,
    val reaction: Double = 4.0,
    val critChance: Double = 0.5,
    val wtapChance: Double = 0.5,
) {

    public companion object {

        /**
         * Everything allowed, newer combat unless the body plays 1.8 rules.
         */
        @JvmField
        public val DEFAULT: Rules = Rules()
    }

    /**
     * Which way a close fighter strafes: [FREE] picks and switches sides itself, [LEFT] and [RIGHT] always go there.
     *
     * @agent claude-opus-5-5
     */
    public enum class Strafe { FREE, LEFT, RIGHT }
}
