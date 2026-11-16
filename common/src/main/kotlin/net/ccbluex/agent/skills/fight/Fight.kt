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

import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.LivingEntity

/**
 * Fights one target: aims, moves, hits and uses what the inventory holds. A game mode calls [engage] on every tick it
 * has a threat and [disengage] once it has none; [Agent] ticks the fight after the game mode, so while it has a
 * target its walking and keys win over the game mode's.
 *
 * @agent claude-opus-5-5
 */
public interface Fight {

    /**
     * The entity fought now: the engaged one, or whoever hit the agent while the rules let it retaliate.
     */
    public val target: LivingEntity?

    /**
     * Fights [target] from this tick on, until [disengage] or another [engage].
     */
    public fun engage(target: LivingEntity)

    /**
     * Lets go of the target: ends a running skill and leaves movement to the game mode again.
     */
    public fun disengage()

    public fun tick(agent: Agent)

    /**
     * Whether it heals now: eats a golden apple, splashes a healing potion or has soup.
     */
    public val isHealing: Boolean

    /**
     * Whether a skill runs, such as eating, a bow shot or a rod cast.
     */
    public val isBusy: Boolean
        get() = false

    /**
     * The agent took a hit; [attacker] is null for falls, fire and the like.
     */
    public fun damaged(agent: Agent, attacker: Entity?) {}

    public val stats: Stats
        get() = Stats.NONE

    public companion object {

        /**
         * A fighter that plays like a player: delayed aim with noise, reach as the victim sees it, clicks at a rate
         * under 1.8 rules or on the attack charge, crits, w-taps, hit reactions and the skills the inventory and
         * [rules] allow.
         */
        @JvmStatic
        @JvmOverloads
        public fun builtIn(
            body: AgentBody,
            skillLevel: SkillLevel,
            personality: Personality,
            rules: Rules = Rules.DEFAULT,
        ): Fight = BuiltInFight(body, skillLevel, personality, rules)
    }
}
