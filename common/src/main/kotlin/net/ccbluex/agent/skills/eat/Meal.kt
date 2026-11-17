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

package net.ccbluex.agent.skills.eat


import net.ccbluex.agent.navigation.isPlainFood
import net.ccbluex.agent.world.entity.MobEffects
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * One way to eat or heal, tried by priority like the fight's skills: the highest one that is due and ready
 * runs, then waits [cooldown] ticks.
 *
 * @agent claude-opus-5-5
 */
internal enum class Meal(val priority: Int, val cooldown: Int, val use: Use) {

    /**
     * Under half health: thrown at the own feet.
     */
    SPLASH_HEAL(75, 10, Use.THROW_DOWN),

    /**
     * Soup servers heal on a right click; vanilla eats it as food.
     */
    SOUP(75, 2, Use.CLICK),

    /**
     * Low on health or about to be: unless regeneration or absorption still runs from the last one.
     */
    GOLDEN_APPLE(70, 40, Use.HOLD),

    /**
     * Hungry and out of a fight: the most nutritious plain food.
     */
    FOOD(10, 0, Use.HOLD),
    ;

    /**
     * How the item is used.
     */
    enum class Use { HOLD, CLICK, THROW_DOWN }

    fun matches(stack: ItemStack): Boolean = when (this) {
        SPLASH_HEAL -> stack.`is`(Items.SPLASH_POTION) && heals(stack)
        SOUP -> stack.`is`(Items.MUSHROOM_STEW)
        GOLDEN_APPLE -> isGoldenApple(stack)
        FOOD -> isPlainFood(stack)
    }

    companion object {

        /**
         * Health below which a golden apple is eaten whatever the share.
         */
        const val GAPPLE_HP = 10f

        /**
         * Health share (times the fighting style's heal) under which a golden apple is eaten.
         */
        const val GAPPLE_SHARE = 0.4
        const val SPLASH_SHARE = 0.5

        /**
         * Health (times the fighting style's heal) at or below which soup is drunk.
         */
        const val SOUP_HP = 12.0

        fun isGoldenApple(stack: ItemStack) = stack.`is`(Items.GOLDEN_APPLE) || stack.`is`(Items.ENCHANTED_GOLDEN_APPLE)

        /**
         * Whether a golden apple's effects still run.
         */
        fun stillBuffed(player: Player) = player.hasEffect(MobEffects.REGENERATION) || player.absorptionAmount > 0f

        private fun heals(stack: ItemStack): Boolean =
            stack.potionEffects?.any { it.effect == MobEffects.INSTANT_HEALTH } == true
    }
}
