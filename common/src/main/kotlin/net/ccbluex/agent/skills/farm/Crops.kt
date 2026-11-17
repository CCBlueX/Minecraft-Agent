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

package net.ccbluex.agent.skills.farm

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.BlockGetter
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.BlockState

/**
 * What a farm grows: when a crop is ripe, what sows it again, what bone meal helps, and which ground a hoe turns into
 * farmland.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public object Crops {

    private val produce = setOf(
        Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.POISONOUS_POTATO, Items.BEETROOT,
        Items.BEETROOT_SEEDS, Items.NETHER_WART, Items.COCOA_BEANS, Items.MELON_SLICE, Items.PUMPKIN, Items.SUGAR_CANE,
        Items.SWEET_BERRIES, Items.CACTUS, Items.BAMBOO, Items.KELP,
    )

    /**
     * Whether the block at [pos] is ready to harvest: a crop, nether wart, cocoa or berry bush grown full, a melon or
     * pumpkin a stem grew, or the second block of sugar cane, cactus, bamboo or kelp, which leaves the bottom one to
     * grow again.
     *
     * From LiquidBounce: [AutoFarmHelper.kt](https://github.com/CCBlueX/LiquidBounce/blob/9cbf251494af9e5b1fe13d380089a2dd94a02926/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/autofarm/AutoFarmHelper.kt#L42-L97)
     */
    @JvmStatic
    public fun isRipe(level: BlockGetter, pos: BlockPos, state: BlockState): Boolean = when {
        state.`is`(BlockKind.CROP) || state.`is`(BlockKind.NETHER_WART) || state.`is`(BlockKind.COCOA) ||
            state.`is`(BlockKind.SWEET_BERRY_BUSH) -> state.isMaxAge
        state.`is`(BlockKind.SUGAR_CANE) || state.`is`(BlockKind.CACTUS) || state.`is`(BlockKind.BAMBOO) ||
            state.`is`(BlockKind.KELP_PLANT) ->
            level.getBlockState(pos.below()).block === state.block && level.getBlockState(pos.below(2)).block !== state.block
        else -> (state.`is`(Blocks.MELON) || state.`is`(Blocks.PUMPKIN)) && Direction.Plane.HORIZONTAL.any {
            val stem = level.getBlockState(pos.relative(it))
            stem.`is`(BlockKind.ATTACHED_STEM) && stem.getValue(BlockStateProperties.HORIZONTAL_FACING) == it.opposite
        }
    }

    /**
     * Whether [stack] is something a farm yields, its seeds included.
     */
    @JvmStatic
    public fun isProduce(stack: ItemStack): Boolean = !stack.isEmpty && stack.item in produce

    /**
     * The item that sows [state] again once harvested; null for what grows back by itself.
     */
    internal fun seedOf(state: BlockState): Item? =
        if (state.`is`(BlockKind.CROP) || state.`is`(BlockKind.NETHER_WART) || state.`is`(BlockKind.COCOA)) {
            state.block.asItem()
        } else {
            null
        }

    /**
     * Whether [stack] sows a crop or nether wart on its soil.
     */
    internal fun isSeed(stack: ItemStack): Boolean {
        val block = stack.item.block ?: return false
        return block.`is`(BlockKind.CROP) || block.`is`(BlockKind.NETHER_WART)
    }

    /**
     * Whether [seed] can be sown on [soil] now: the cell above is empty, the crop would grow there, and no stem beside
     * it needs the cell for its melon or pumpkin.
     */
    internal fun canSow(level: Level, soil: BlockPos, seed: Item): Boolean {
        val block = seed.block ?: return false
        val above = soil.above()
        return level.getBlockState(above).isAir && block.defaultBlockState().canSurvive(level, above) &&
            Direction.Plane.HORIZONTAL.none {
                val next = level.getBlockState(above.relative(it))
                next.`is`(BlockKind.STEM) || next.`is`(BlockKind.ATTACHED_STEM)
            }
    }

    /**
     * Whether bone meal makes the crop at [pos] grow: crops, stems, cocoa and berry bushes not grown full.
     *
     * From LiquidBounce: [AutoFarmHelper.kt](https://github.com/CCBlueX/LiquidBounce/blob/9cbf251494af9e5b1fe13d380089a2dd94a02926/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/autofarm/AutoFarmHelper.kt#L49-L56)
     */
    internal fun isFertile(level: Level, pos: BlockPos, state: BlockState): Boolean =
        (state.`is`(BlockKind.CROP) || state.`is`(BlockKind.STEM) || state.`is`(BlockKind.COCOA) ||
            state.`is`(BlockKind.SWEET_BERRY_BUSH)) && state.isValidBonemealTarget(level, pos)

    /**
     * Whether a hoe turns the block at [pos] into farmland that stays: grass or dirt with nothing on it and water
     * near enough to keep the farmland wet.
     */
    internal fun isTillable(level: Level, pos: BlockPos, state: BlockState): Boolean =
        state.`is`(BlockTags.TURNS_INTO_FARMLAND) && level.getBlockState(pos.above()).isAir && isWatered(level, pos)

    /**
     * Whether farmland at [pos] has water near: within 4 blocks across, level with it or one above, as the game
     * checks.
     */
    internal fun isWatered(level: Level, pos: BlockPos): Boolean =
        BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))
            .any { level.getFluidState(it).type == Fluid.WATER }
}
