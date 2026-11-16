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

package net.ccbluex.agent.pathfinder.walk

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.EmptyBlockGetter
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.item.ItemStack
import java.util.function.ToDoubleFunction
import kotlin.math.ceil

/**
 * Ticks to mine a block with the best of the copied hotbar tools, without haste, fatigue or water penalties. Safe to
 * read from any thread.
 *
 * From LiquidBounce: [Settings.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Settings.kt#L108-L148)
 *
 * @agent claude-fable-5-2
 */
public class BreakTimes private constructor(private val tools: List<Tool>) : ToDoubleFunction<BlockState> {

    private class Tool(val stack: ItemStack, val efficiency: Int)

    override fun applyAsDouble(state: BlockState): Double {
        val hardness = state.getDestroySpeed(EmptyBlockGetter, BlockPos.ZERO)
        return when {
            hardness < 0f -> Double.POSITIVE_INFINITY
            hardness == 0f -> 1.0
            else -> tools.minOf { ticks(it, state, hardness) }
        }
    }

    private fun ticks(tool: Tool, state: BlockState, hardness: Float): Double {
        val harvests = !state.requiresCorrectToolForDrops() || tool.stack.isCorrectToolForDrops(state)
        val divisor = if (harvests) CORRECT_TOOL_DIVISOR else WRONG_TOOL_DIVISOR
        val progress = speed(tool, state) / hardness / divisor
        return if (progress > 0.0) ceil(1.0 / progress) else Double.POSITIVE_INFINITY
    }

    /**
     * As getDestroySpeedWithEnchantment, with the level read beforehand: registries are for the game thread.
     */
    private fun speed(tool: Tool, state: BlockState): Double {
        var speed = tool.stack.getDestroySpeed(state)
        if (speed > 1f && tool.efficiency != 0) {
            speed += (tool.efficiency * tool.efficiency + 1f).coerceIn(0f, MAX_EFFICIENCY_BONUS)
        }
        return speed.toDouble()
    }

    public companion object {
        private const val CORRECT_TOOL_DIVISOR = 30.0
        private const val WRONG_TOOL_DIVISOR = 100.0
        private const val MAX_EFFICIENCY_BONUS = 1024f

        private val EMPTY_HAND = Tool(ItemStack.EMPTY, 0)

        /**
         * Copies the tools in [hotbar], with their Efficiency level. Call on the game thread.
         *
         * @agent claude-fable-5-2
         * @agent claude-opus-5-5
         */
        public fun capture(hotbar: List<ItemStack>): BreakTimes {
            val tools = hotbar.filterNot { it.isEmpty }.map { stack ->
                Tool(stack.copy(), stack.getEnchantmentLevel(Enchantments.EFFICIENCY))
            }
            return BreakTimes(tools + EMPTY_HAND)
        }
    }

}
