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

import net.ccbluex.agent.skills.eat.Eat
import net.ccbluex.agent.skills.fight.Fight
import net.ccbluex.agent.skills.fight.Rules
import net.ccbluex.agent.skills.gear.Gear
import net.ccbluex.agent.skills.loot.Chests
import net.ccbluex.agent.skills.loot.Loot
import net.ccbluex.agent.skills.shop.Catalogue
import net.ccbluex.agent.skills.shop.Counter
import net.ccbluex.agent.skills.shop.Shop
import net.ccbluex.agent.skills.voidrescue.VoidRescue

/**
 * Who does each ability of an [Agent]. A null ability is off, and game modes plan without it; a host can hand any
 * of them to its own implementation.
 *
 * @agent claude-opus-5-5
 */
public class Skills @JvmOverloads constructor(
    public val fight: Fight? = null,
    public val eat: Eat? = null,
    public val loot: Loot? = null,
    public val gear: Gear? = null,
    public val shop: Shop? = null,
    public val voidRescue: VoidRescue? = null,
) {

    public companion object {

        /**
         * Every built-in ability for [body]. The shop needs the host's [catalogue] and [counter] and is off without
         * them; [chests] are the ones the loot ability knows.
         */
        @JvmStatic
        @JvmOverloads
        public fun builtIn(
            body: AgentBody,
            skillLevel: SkillLevel = SkillLevel.NORMAL,
            personality: Personality = Personality.BALANCED,
            rules: Rules = Rules.DEFAULT,
            chests: Chests = Chests(),
            catalogue: Catalogue? = null,
            counter: Counter? = null,
        ): Skills = Skills(
            fight = Fight.builtIn(body, skillLevel, personality, rules),
            eat = Eat.builtIn(),
            loot = Loot.builtIn(chests),
            gear = Gear.builtIn(),
            shop = if (catalogue != null && counter != null) Shop.builtIn(body, catalogue, counter) else null,
            voidRescue = VoidRescue.builtIn(),
        )
    }
}
