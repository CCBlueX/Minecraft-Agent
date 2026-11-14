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

package net.ccbluex.agent.skills

import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Turning
import java.util.Locale
import kotlin.math.min

/**
 * How well a bot plays: how fast it clicks, turns and reacts, and how often and how well it uses skills. The skill
 * use comes twice, for fighting with the weapon ([melee]) and with blasts ([blast]); [use] mixes the two.
 *
 * @property cps multiplies the clicks per second
 * @property aim multiplies the turn speed
 * @property reaction multiplies the reaction ticks
 * @property noise aim noise in degrees per tick
 * @property strength attack strength waited for under 1.9 rules
 * @property technique multiplies crit and w-tap chances
 * @property reach share of the weapon's reach the longest swing comes from
 * @property spread blocks short of that the swings come from, drawn anew per swing
 * @property miss chance that a swing misses
 * @property strafe chance to strafe for a while when close
 * @property damage multiplies the damage of hits, where the host can scale it
 * @agent claude-opus-5-5
 */
public data class SkillLevel(
    val cps: Double,
    val aim: Double,
    val reaction: Double,
    val noise: Double,
    val strength: Double,
    val technique: Double,
    val reach: Double,
    val spread: Double,
    val miss: Double,
    val strafe: Double,
    val damage: Double,
    val melee: Use,
    val blast: Use,
) {

    /**
     * How skills are used.
     *
     * @property skill chance to use a skill when it could
     * @property cooldown multiplies skill cooldowns
     * @property slowness 0 to 1: how far a skill's steps lean to their slowest
     * @property placement 0 to 1: how well placed things land where they hurt, and how often a click lands
     * @agent claude-opus-5-5
     */
    public data class Use(val skill: Double, val cooldown: Double, val slowness: Double, val placement: Double) {

        /**
         * Between this and [blast] by [weight]: 0 is this, 1 is [blast].
         */
        public fun towards(blast: Use, weight: Double): Use = Use(
            mix(skill, blast.skill, weight),
            mix(cooldown, blast.cooldown, weight),
            mix(slowness, blast.slowness, weight),
            mix(placement, blast.placement, weight),
        )

        private fun mix(a: Double, b: Double, weight: Double) = a + (b - a) * weight
    }

    /**
     * Skill use for a fighter leaning [blastWeight] (0 to 1) on blasts and one-go skills.
     */
    public fun use(blastWeight: Double): Use = melee.towards(blast, blastWeight)

    /**
     * How a body turns at this level: [LookPurpose.AIM] at 25 times [aim] degrees a tick, [LookPurpose.STEER] four
     * times as fast, the pitch at 0.7 of the yaw's pace, and anything else at [walkDegrees]; nothing turns faster
     * than [walkDegrees].
     */
    @JvmOverloads
    public fun turning(walkDegrees: Float = WALK_TURN): Turning {
        val aimPace = (AIM_SPEED * aim).toFloat()
        val aiming = paced(aimPace, walkDegrees)
        val flicking = paced(aimPace * FLICK_SPEED, walkDegrees)
        val walking = Turning.linear(walkDegrees)
        return Turning { from, to, purpose ->
            when (purpose) {
                LookPurpose.AIM -> aiming
                LookPurpose.STEER -> flicking
                LookPurpose.MOVEMENT, LookPurpose.VIEW -> walking
            }.turn(from, to, purpose)
        }
    }

    private fun paced(yRot: Float, most: Float) = Turning.linear(min(yRot, most), min(yRot * X_ROT_SPEED, most))

    public companion object {
        /**
         * Degrees a tick an aim turns at an aim multiplier of 1.
         */
        private const val AIM_SPEED = 25.0
        private const val FLICK_SPEED = 4f
        private const val X_ROT_SPEED = 0.7f
        private const val WALK_TURN = 40f

        @JvmField
        public val BEGINNER: SkillLevel = SkillLevel(
            0.4, 0.4, 3.0, 4.0, 0.55, 0.0, 0.8, 0.6, 0.3, 0.0, 0.25,
            Use(0.0, 3.0, 1.0, 0.4), Use(0.0, 3.0, 1.0, 0.3),
        )

        @JvmField
        public val EASY: SkillLevel = SkillLevel(
            0.6, 0.55, 2.2, 3.2, 0.7, 0.25, 0.85, 0.5, 0.15, 0.2, 0.55,
            Use(0.3, 1.9, 0.6, 0.65), Use(0.15, 3.0, 0.8, 0.45),
        )

        @JvmField
        public val NORMAL: SkillLevel = SkillLevel(
            0.75, 0.6, 2.0, 2.8, 0.8, 0.35, 0.9, 0.45, 0.15, 0.3, 0.5,
            Use(0.65, 1.3, 0.3, 0.85), Use(0.45, 1.8, 0.45, 0.7),
        )

        @JvmField
        public val HARD: SkillLevel = SkillLevel(
            1.25, 1.5, 0.6, 0.6, 0.95, 1.25, 0.98, 0.3, 0.0, 1.0, 1.0,
            Use(1.0, 0.7, 0.0, 1.0), Use(1.0, 0.7, 0.0, 1.0),
        )

        /**
         * The level named [name] (`beginner`, `easy`, `normal`, `hard`), [NORMAL] for anything else.
         */
        @JvmStatic
        public fun of(name: String): SkillLevel = when (name.lowercase(Locale.ROOT)) {
            "beginner" -> BEGINNER
            "easy" -> EASY
            "hard" -> HARD
            else -> NORMAL
        }
    }
}
