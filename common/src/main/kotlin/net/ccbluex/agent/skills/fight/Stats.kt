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

/**
 * What the fighter's own hits on its target came to.
 *
 * @property hits hits that hurt the target
 * @property combo hits in a row since the fighter last took a hit
 * @property longestCombo the longest such run
 * @property damageDealt health and absorption the hits took off the target
 * @agent claude-opus-5-5
 */
public data class Stats(val hits: Int, val combo: Int, val longestCombo: Int, val damageDealt: Double) {

    public companion object {
        @JvmField
        public val NONE: Stats = Stats(0, 0, 0, 0.0)
    }
}

/**
 * The running count behind [Stats].
 *
 * @agent claude-opus-5-5
 */
internal class Tally {

    private var hits = 0
    private var combo = 0
    private var longestCombo = 0
    private var damage = 0.0

    fun hit(damage: Double) {
        hits++
        combo++
        longestCombo = maxOf(longestCombo, combo)
        this.damage += damage
    }

    fun hurt() {
        combo = 0
    }

    fun snapshot(): Stats = if (hits == 0) Stats.NONE else Stats(hits, combo, longestCombo, damage)
}
