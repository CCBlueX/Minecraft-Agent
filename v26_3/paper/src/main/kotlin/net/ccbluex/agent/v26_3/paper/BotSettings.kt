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

package net.ccbluex.agent.v26_3.paper

import net.ccbluex.agent.navigation.Options
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.skills.Personality

/**
 * How a bot's body behaves. A change applies to a spawned body right away; [health] at the next spawn.
 *
 * @agent claude-opus-5-5
 */
public data class BotSettings @JvmOverloads constructor(
    /**
     * Maximum health; the server caps it (1024).
     */
    public val health: Double = 20.0,
    /**
     * An immortal body is healed after every hit and never dies.
     */
    public val immortal: Boolean = false,
    /**
     * Whether damage reaches the body; anticheats still see the hits it ignores.
     */
    public val damageable: Boolean = true,
    /**
     * Whether hits and other entities push the body.
     */
    public val knockback: Boolean = true,
    /**
     * An anchored body stays where it is: no keys, no gravity, no pushing.
     */
    public val anchored: Boolean = false,
    public val sneak: Boolean = false,
    /**
     * Sprints whenever it runs forward, as a held sprint key.
     */
    public val sprint: Boolean = false,
    /**
     * Degrees per tick the body turns towards where it looks.
     */
    public val turnSpeed: Float = PaperBody.DEFAULT_TURN_SPEED,
    /**
     * Whether a dead body comes back at the bot's home; without, the bot stays dead until respawned.
     */
    public val respawns: Boolean = true,
    /**
     * Ticks from a death until the new body spawns at home; at least the death animation.
     */
    public val respawnDelay: Int = 60,
    /**
     * Whether the bot plays in another plugin's minigame, as a player: it picks items up, gets hungry, drops its
     * inventory on death and keeps the chunks around it loaded, and it never comes back on its own, as a new body
     * would be a stranger to the minigame.
     */
    public val minigame: Boolean = false,
    public val navigation: Options = Options(),
    /**
     * How well the bot fights: aim, reactions, clicks, how often it reaches for a skill.
     */
    public val skillLevel: SkillLevel = SkillLevel.NORMAL,
    /**
     * How the bot leans when it plays; null rolls a new one for every body.
     */
    public val personality: Personality? = null,
) {

    public companion object {
        /**
         * Stands where it is put and takes hits without moving or dying, as a target to practice on.
         */
        @JvmField
        public val STANDING: BotSettings = BotSettings(
            immortal = true,
            knockback = false,
            anchored = true,
            respawnDelay = 20,
        )
    }
}
