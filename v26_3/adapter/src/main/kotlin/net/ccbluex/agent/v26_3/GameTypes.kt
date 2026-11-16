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

package net.ccbluex.agent.v26_3

import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.DyeColor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.AbstractBedBlock
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.FarmlandBlock
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.SlimeBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.WallBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.BlockPos as McBlockPos
import net.minecraft.tags.TagKey as McTagKey
import net.minecraft.world.entity.player.Player as McPlayer
import net.minecraft.world.item.Item as McItem
import net.minecraft.world.level.Level as McLevel
import net.minecraft.world.level.block.Block as McBlock
import net.minecraft.world.level.block.state.BlockState as McBlockState
import net.minecraft.world.phys.BlockHitResult as McBlockHitResult

/**
 * The library's blocks and items for every one in the game's registries, defined with the game's facts on first use.
 *
 * @agent claude-opus-5-5
 */
internal object GameTypes {
    val blocks = IdentityHashMap<McBlock, Block>()
    val items = IdentityHashMap<McItem, Item>()
    private val gameBlocks = IdentityHashMap<Block, McBlock>()
    private val gameItems = IdentityHashMap<Item, McItem>()
    private val blockTags = ConcurrentHashMap<TagKey<Block>, McTagKey<McBlock>>()
    private val itemTags = ConcurrentHashMap<TagKey<Item>, McTagKey<McItem>>()
    private val interactive = ConcurrentHashMap<Class<*>, Boolean>()

    init {
        for (block in BuiltInRegistries.BLOCK) {
            val agent = Block.of(BuiltInRegistries.BLOCK.getKey(block).toString())
            blocks[block] = agent
            gameBlocks[agent] = block
        }
        for (item in BuiltInRegistries.ITEM) {
            val agent = Item.of(BuiltInRegistries.ITEM.getKey(item).toString())
            items[item] = agent
            gameItems[agent] = item
        }
        // The states made here look their blocks up in the maps above.
        blocks.forEach { (game, block) -> define(game, block) }
        items.forEach { (game, item) ->
            val holder = game.builtInRegistryHolder()
            item.define({ game.defaultInstance.toAgent() }, (game as? BlockItem)?.block?.let(blocks::get)) { tag ->
                holder.`is`(itemTags.computeIfAbsent(tag) { McTagKey.create(Registries.ITEM, Identifier.parse(it.id)) })
            }
        }
    }

    @OptIn(net.ccbluex.agent.InternalAgentApi::class)
    private fun define(game: McBlock, block: Block) {
        val holder = game.builtInRegistryHolder()
        block.define(
            defaultState = game.defaultBlockState().toAgent(),
            item = game.asItem().takeIf { it !== net.minecraft.world.item.Items.AIR }?.let { items[it] },
            kinds = kinds(game),
            tags = { tag ->
                holder.`is`(
                    blockTags.computeIfAbsent(tag) { McTagKey.create(Registries.BLOCK, Identifier.parse(it.id)) },
                )
            },
            friction = game.friction,
            speedFactor = game.speedFactor,
            jumpFactor = game.jumpFactor,
            bounceRestitution = game.bounceRestitution,
            fallDistanceReduction = game.fallDistanceReduction,
            color = (game as? BedBlock)?.color?.let { DyeColor.valueOf(it.name) },
        )
    }

    private fun kinds(block: McBlock): Set<BlockKind> = buildSet {
        when (block) {
            is DoorBlock -> add(BlockKind.DOOR).also { if (block.type().canOpenByHand()) add(BlockKind.OPENS_BY_HAND) }
            is TrapDoorBlock -> add(BlockKind.TRAPDOOR).also {
                if (block !== net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR) add(BlockKind.OPENS_BY_HAND)
            }
            is FenceGateBlock -> addAll(listOf(BlockKind.FENCE_GATE, BlockKind.OPENS_BY_HAND))
            is FenceBlock -> add(BlockKind.FENCE)
            is WallBlock -> add(BlockKind.WALL)
            is LadderBlock -> add(BlockKind.LADDER)
            is StairBlock -> add(BlockKind.STAIRS)
            is AbstractBedBlock -> add(BlockKind.BED)
            is ChestBlock -> add(BlockKind.CHEST)
            is LeverBlock -> add(BlockKind.LEVER)
            is ButtonBlock -> add(BlockKind.BUTTON)
            is BasePressurePlateBlock -> add(BlockKind.PRESSURE_PLATE)
            is FarmlandBlock -> add(BlockKind.FARMLAND)
            is SlimeBlock -> add(BlockKind.SLIME)
            is BaseFireBlock -> add(BlockKind.FIRE)
        }
        if (block is FallingBlock) add(BlockKind.FALLING)
        if (block is EntityBlock) add(BlockKind.ENTITY_BLOCK)
        if (isInteractive(block)) add(BlockKind.INTERACTIVE)
    }

    /**
     * Whether a class between [block]'s and [BlockBehaviour] overrides the empty-hand use.
     */
    private fun isInteractive(block: McBlock): Boolean = interactive.computeIfAbsent(block.javaClass) { type ->
        generateSequence<Class<*>>(type) { it.superclass }
            .takeWhile { it != BlockBehaviour::class.java }
            .any { declaresUseWithoutItem(it) }
    }

    private fun declaresUseWithoutItem(type: Class<*>) = try {
        type.getDeclaredMethod(
            "useWithoutItem", McBlockState::class.java, McLevel::class.java, McBlockPos::class.java,
            McPlayer::class.java, McBlockHitResult::class.java,
        )
        true
    } catch (_: NoSuchMethodException) {
        false
    }

    fun toMinecraft(block: Block): McBlock = gameBlocks[block] ?: net.minecraft.world.level.block.Blocks.AIR

    fun toMinecraft(item: Item): McItem = gameItems[item] ?: net.minecraft.world.item.Items.AIR
}

/**
 * Defines the library's blocks and items with the game's facts, so [net.ccbluex.agent.world.block.Blocks] and
 * [net.ccbluex.agent.world.item.Items] work before any block or item of the game is seen; once the game's registries
 * are frozen.
 *
 * @agent claude-opus-5-5
 */
public fun defineGameTypes() {
    GameTypes
}

/**
 * @agent claude-opus-5-5
 */
public fun McBlock.toAgent(): Block = GameTypes.blocks.getValue(this)

/**
 * @agent claude-opus-5-5
 */
public fun Block.toMinecraft(): McBlock = GameTypes.toMinecraft(this)

/**
 * @agent claude-opus-5-5
 */
public fun McItem.toAgent(): Item = GameTypes.items.getValue(this)

/**
 * @agent claude-opus-5-5
 */
public fun Item.toMinecraft(): McItem = GameTypes.toMinecraft(this)
