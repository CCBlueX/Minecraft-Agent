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
 * The skills the built-in fight uses besides hitting. Each one only starts while the inventory holds what it needs, so
 * [Rules.skills] only has to name what the server forbids.
 *
 * @agent claude-opus-5-5
 */
public enum class Skill {

    /**
     * A new totem in the off hand after one popped.
     */
    TOTEM,

    /**
     * The shield raised while the target closes in and the own hit is not ready.
     */
    GUARD,

    /**
     * An axe hit on a raised shield, which disables it.
     */
    SHIELD_BREAK,

    /**
     * A golden apple when low, walled in first when the target is close.
     */
    GAPPLE,

    /**
     * Three blocks up out of reach when low and pressed.
     */
    PILLAR,

    /**
     * A block in the way of a pearl the target throws at the agent.
     */
    BLOCK_PEARL,

    /**
     * Water on the ground just before a hard landing.
     */
    WATER_CLUTCH,

    /**
     * A wind charge jump and a mace hit on the way down.
     */
    MACE_SMASH,

    /**
     * An ender pearl onto a far target.
     */
    PEARL,

    /**
     * A cobweb on the feet of a grounded target.
     */
    COBWEB,

    /**
     * Obsidian and an end crystal beside the target, hit at once.
     */
    CRYSTAL,

    /**
     * A respawn anchor beside the target, charged and set off.
     */
    ANCHOR,

    /**
     * The bow on a target out of melee range.
     */
    BOW,

    /**
     * The spear's lunge and charge, and its close rhythm.
     */
    SPEAR_CHARGE,

    /**
     * The fishing rod on a target a few blocks away.
     */
    ROD,

    /**
     * Snowballs, eggs and wind charges at a target out of reach.
     */
    THROW,

    /**
     * A healing splash potion at its own feet when low.
     */
    POT_HEAL,

    /**
     * Strength and swiftness splash potions kept up.
     */
    POT_BUFF,

    /**
     * Mushroom stew when low.
     */
    SOUP,

    /**
     * Water poured out of a cobweb the agent is stuck in.
     */
    WEB_ESCAPE,

    /**
     * The target's water taken away with an empty bucket.
     */
    DRAIN,

    /**
     * Lava poured at the target's feet and taken back at once.
     */
    LAVA_TAP,
}
