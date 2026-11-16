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

package net.ccbluex.agent.v1_8_8.spigot

import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.block.Block
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.ItemTags
import net.minecraft.server.v1_8_R3.BlockBed
import net.minecraft.server.v1_8_R3.BlockButtonAbstract
import net.minecraft.server.v1_8_R3.BlockChest
import net.minecraft.server.v1_8_R3.BlockCloth
import net.minecraft.server.v1_8_R3.BlockCobbleWall
import net.minecraft.server.v1_8_R3.BlockDoor
import net.minecraft.server.v1_8_R3.BlockFalling
import net.minecraft.server.v1_8_R3.BlockFence
import net.minecraft.server.v1_8_R3.BlockFenceGate
import net.minecraft.server.v1_8_R3.BlockFire
import net.minecraft.server.v1_8_R3.BlockGlass
import net.minecraft.server.v1_8_R3.BlockLadder
import net.minecraft.server.v1_8_R3.BlockLeaves
import net.minecraft.server.v1_8_R3.BlockLever
import net.minecraft.server.v1_8_R3.BlockLogAbstract
import net.minecraft.server.v1_8_R3.BlockMinecartTrackAbstract
import net.minecraft.server.v1_8_R3.BlockPressurePlateAbstract
import net.minecraft.server.v1_8_R3.BlockSlime
import net.minecraft.server.v1_8_R3.BlockSoil
import net.minecraft.server.v1_8_R3.BlockSlowSand
import net.minecraft.server.v1_8_R3.BlockStainedGlass
import net.minecraft.server.v1_8_R3.BlockStairs
import net.minecraft.server.v1_8_R3.BlockTrapdoor
import net.minecraft.server.v1_8_R3.BlockWood
import net.minecraft.server.v1_8_R3.Blocks
import net.minecraft.server.v1_8_R3.EntityHuman
import net.minecraft.server.v1_8_R3.EnumDirection
import net.minecraft.server.v1_8_R3.IBlockData
import net.minecraft.server.v1_8_R3.ItemAxe
import net.minecraft.server.v1_8_R3.ItemBlock
import net.minecraft.server.v1_8_R3.ItemPickaxe
import net.minecraft.server.v1_8_R3.ItemSword
import net.minecraft.server.v1_8_R3.Material
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.server.v1_8_R3.Block as McBlock
import net.minecraft.server.v1_8_R3.BlockPosition as McBlockPos
import net.minecraft.server.v1_8_R3.Item as McItem
import net.minecraft.server.v1_8_R3.World as McLevel

/**
 * The 1.8 blocks, block states and items as the library's game types, named as the modern game names them: a state
 * per combined id (`block << 4 | data`), and an item by id and data where 1.8 told items apart by data.
 *
 * @agent claude-opus-5-5
 */
internal object GameTypes {
    private val blocks = IdentityHashMap<McBlock, Block>()
    private val items = IdentityHashMap<McItem, Item>()
    private val gameBlocks = IdentityHashMap<Block, McBlock>()
    private val gameItems = IdentityHashMap<Item, McItem>()
    private val states = arrayOfNulls<StateView>(COMBINED_IDS)
    private val interactive = ConcurrentHashMap<Class<*>, Boolean>()

    /**
     * Whether a block's collision shape depends on its neighbours or its block entity, so every position needs its
     * own; by block id.
     */
    private val placed = BooleanArray(BLOCK_IDS)

    @OptIn(net.ccbluex.agent.InternalAgentApi::class)
    private fun define(game: McBlock, block: Block) {
        if (gameBlocks[block] !== game) return
        val tags = blockTags(game)
        block.define(
            defaultState = state(game.blockData),
            item = McItem.getItemOf(game)?.let(items::get),
            kinds = kinds(game),
            tags = { it in tags },
            friction = game.frictionFactor,
            // Soul sand slows by its entity collision, which PlayerMotion's 1.8 step takes from the speed factor
            speedFactor = if (game is BlockSlowSand) SOUL_SAND_SPEED else 1f,
            jumpFactor = 1f,
            bounceRestitution = if (game is BlockSlime) 1f else 0f,
            fallDistanceReduction = 0f,
            color = if (game is BlockBed) DyeColor.RED else null,
        )
    }

    @OptIn(net.ccbluex.agent.InternalAgentApi::class)
    private fun define(game: McItem, item: Item) {
        val data = when (item.id) {
            "minecraft:enchanted_golden_apple" -> 1
            "minecraft:splash_potion" -> SPLASH_POTION
            else -> 0
        }
        val tags = itemTags(game)
        item.define(
            { StackView(net.minecraft.server.v1_8_R3.ItemStack(game, 1, data)) },
            (game as? ItemBlock)?.d()?.let(blocks::get),
        ) { it in tags }
    }

    private fun kinds(block: McBlock): Set<BlockKind> = buildSet {
        when (block) {
            is BlockDoor -> add(BlockKind.DOOR).also { if (block.getMaterial() != Material.ORE) add(BlockKind.OPENS_BY_HAND) }
            is BlockTrapdoor -> add(BlockKind.TRAPDOOR).also { if (block !== Blocks.IRON_TRAPDOOR) add(BlockKind.OPENS_BY_HAND) }
            is BlockFenceGate -> addAll(listOf(BlockKind.FENCE_GATE, BlockKind.OPENS_BY_HAND))
            is BlockFence -> add(BlockKind.FENCE)
            is BlockCobbleWall -> add(BlockKind.WALL)
            is BlockLadder -> add(BlockKind.LADDER)
            is BlockStairs -> add(BlockKind.STAIRS)
            is BlockBed -> add(BlockKind.BED)
            is BlockChest -> add(BlockKind.CHEST)
            is BlockLever -> add(BlockKind.LEVER)
            is BlockButtonAbstract -> add(BlockKind.BUTTON)
            is BlockPressurePlateAbstract -> add(BlockKind.PRESSURE_PLATE)
            is BlockSoil -> add(BlockKind.FARMLAND)
            is BlockSlime -> add(BlockKind.SLIME)
            is BlockFire -> add(BlockKind.FIRE)
        }
        if (block is BlockFalling) add(BlockKind.FALLING)
        if (block.isTileEntity) add(BlockKind.ENTITY_BLOCK)
        if (isInteractive(block)) add(BlockKind.INTERACTIVE)
    }

    private fun blockTags(block: McBlock): Set<TagKey<Block>> = buildSet {
        if (block is BlockLogAbstract) add(BlockTags.LOGS)
        if (block is BlockWood) add(BlockTags.PLANKS)
        if (block is BlockCloth) add(BlockTags.WOOL)
        if (block is BlockLeaves) add(BlockTags.LEAVES)
        if (block is BlockMinecartTrackAbstract) add(BlockTags.RAILS)
        if (block is BlockGlass || block is BlockStainedGlass) add(BlockTags.IMPERMEABLE)
    }

    private fun itemTags(item: McItem): Set<TagKey<Item>> = buildSet {
        when (item) {
            is ItemSword -> add(ItemTags.SWORDS)
            is ItemAxe -> add(ItemTags.AXES)
            is ItemPickaxe -> add(ItemTags.PICKAXES)
        }
        val block = (item as? ItemBlock)?.d()
        if (block is BlockLogAbstract) add(ItemTags.LOGS)
        if (block is BlockWood) add(ItemTags.PLANKS)
    }

    /**
     * Whether a class between [block]'s and Block overrides the use with an empty hand.
     */
    private fun isInteractive(block: McBlock): Boolean = interactive.computeIfAbsent(block.javaClass) { type ->
        generateSequence<Class<*>>(type) { it.superclass }
            .takeWhile { it != McBlock::class.java }
            .any { declaresInteract(it) }
    }

    private fun declaresInteract(type: Class<*>) = try {
        type.getDeclaredMethod(
            "interact", McLevel::class.java, McBlockPos::class.java, IBlockData::class.java, EntityHuman::class.java,
            EnumDirection::class.java, Float::class.java, Float::class.java, Float::class.java,
        )
        true
    } catch (_: NoSuchMethodException) {
        false
    }

    // Fences, panes, walls and stairs join their neighbours, doors read their other half, chests their neighbour,
    // skulls and moving pistons their block entity
    private fun shapeDependsOnPosition(block: McBlock): Boolean = block is BlockFence || block is BlockCobbleWall ||
        block is net.minecraft.server.v1_8_R3.BlockThin || block is BlockStairs || block is BlockDoor ||
        block is net.minecraft.server.v1_8_R3.BlockChest || block is net.minecraft.server.v1_8_R3.BlockSkull ||
        block is net.minecraft.server.v1_8_R3.BlockPistonMoving

    /**
     * The id chunk sections store a state by: `block id << 4 | data`.
     */
    fun stateId(data: IBlockData): Int = McBlock.d.b(data)

    fun state(data: IBlockData): StateView = state(stateId(data))

    fun state(stateId: Int): StateView = states.getOrNull(stateId) ?: AIR

    private val AIR: StateView by lazy { checkNotNull(states[0]) }

    fun isPlaced(stateId: Int): Boolean = placed[stateId ushr DATA_BITS]

    fun toAgent(block: McBlock): Block = blocks.getValue(block)

    fun toMinecraft(block: Block): McBlock = gameBlocks[block] ?: Blocks.AIR

    fun toAgent(item: McItem, data: Int): Item = when {
        item === net.minecraft.server.v1_8_R3.Items.GOLDEN_APPLE && data == 1 -> Item.of("enchanted_golden_apple")
        item === net.minecraft.server.v1_8_R3.Items.POTION && data and SPLASH_POTION != 0 -> Item.of("splash_potion")
        else -> items.getValue(item)
    }

    fun toMinecraft(item: Item): McItem? = gameItems[item]

    private const val BLOCK_IDS = 4096
    private const val DATA_BITS = 4
    private const val COMBINED_IDS = BLOCK_IDS shl DATA_BITS
    private const val SOUL_SAND_SPEED = 0.4f
    private const val DEFAULT_FRICTION = 0.6f

    // The data bit of a thrown potion
    const val SPLASH_POTION = 16384

    // 1.8's registry names that the modern game renamed, for the blocks and items the library tells apart
    private val RENAMED = mapOf(
        "web" to "cobweb", "slime" to "slime_block", "grass" to "grass_block", "tallgrass" to "short_grass",
        "waterlily" to "lily_pad", "snow_layer" to "snow", "snow" to "snow_block", "planks" to "oak_planks",
        "log" to "oak_log", "log2" to "acacia_log", "leaves" to "oak_leaves", "leaves2" to "acacia_leaves",
        "wool" to "white_wool", "flowing_water" to "water", "flowing_lava" to "lava", "lit_furnace" to "furnace",
        "fence" to "oak_fence", "fence_gate" to "oak_fence_gate", "wooden_door" to "oak_door", "trapdoor" to "oak_trapdoor",
        "golden_rail" to "powered_rail", "noteblock" to "note_block", "deadbush" to "dead_bush", "red_flower" to "poppy",
        "yellow_flower" to "dandelion", "brick_block" to "bricks", "mob_spawner" to "spawner",
        "stonebrick" to "stone_bricks", "melon_block" to "melon", "nether_brick" to "nether_bricks",
        "lit_pumpkin" to "jack_o_lantern", "quartz_ore" to "nether_quartz_ore", "hardened_clay" to "terracotta",
        "stained_hardened_clay" to "white_terracotta", "stained_glass" to "white_stained_glass",
        "stained_glass_pane" to "white_stained_glass_pane", "carpet" to "white_carpet", "sapling" to "oak_sapling",
        "monster_egg" to "infested_stone", "standing_sign" to "oak_sign", "wall_sign" to "oak_wall_sign",
        "portal" to "nether_portal", "piston_extension" to "moving_piston", "wooden_slab" to "oak_slab",
        "double_wooden_slab" to "oak_slab", "double_stone_slab" to "smooth_stone_slab", "stone_slab" to "smooth_stone_slab",
        "stone_slab2" to "red_sandstone_slab", "double_stone_slab2" to "red_sandstone_slab",
        "wooden_pressure_plate" to "oak_pressure_plate", "wooden_button" to "oak_button",
        "unpowered_repeater" to "repeater", "powered_repeater" to "repeater", "unpowered_comparator" to "comparator",
        "powered_comparator" to "comparator", "lit_redstone_ore" to "redstone_ore", "lit_redstone_lamp" to "redstone_lamp",
        "unlit_redstone_torch" to "redstone_torch", "reeds" to "sugar_cane", "skull" to "skeleton_skull",
    )

    private val RENAMED_ITEMS = RENAMED + mapOf(
        "fish" to "cod", "cooked_fish" to "cooked_cod", "speckled_melon" to "glistering_melon_slice",
        "melon" to "melon_slice", "boat" to "oak_boat", "fireworks" to "firework_rocket", "dye" to "ink_sac",
        "netherbrick" to "nether_brick", "sign" to "oak_sign", "bed" to "red_bed",
    )

    private fun modernBlockId(id: String): String = "minecraft:" + id.removePrefix("minecraft:").let { RENAMED[it] ?: it }

    private fun modernItemId(id: String): String = "minecraft:" + id.removePrefix("minecraft:").let { RENAMED_ITEMS[it] ?: it }

    init {
        for (block in McBlock.REGISTRY) {
            val agent = Block.of(modernBlockId(McBlock.REGISTRY.c(block).toString()))
            blocks[block] = agent
            gameBlocks.putIfAbsent(agent, block)
            placed[McBlock.getId(block)] = shapeDependsOnPosition(block)
        }
        for (data in McBlock.d) {
            val id = stateId(data)
            if (id in states.indices && states[id] == null) states[id] = StateView(blocks.getValue(data.block), data)
        }
        for (item in McItem.REGISTRY) {
            val agent = Item.of(modernItemId(McItem.REGISTRY.c(item).toString()))
            items[item] = agent
            gameItems.putIfAbsent(agent, item)
        }
        // Items 1.8 tells apart only by data
        gameItems[Item.of("enchanted_golden_apple")] = net.minecraft.server.v1_8_R3.Items.GOLDEN_APPLE
        gameItems[Item.of("splash_potion")] = net.minecraft.server.v1_8_R3.Items.POTION
        blocks.forEach { (game, block) -> define(game, block) }
        gameItems.forEach { (item, game) -> define(game, item) }
        // Blocks the library names that 1.8 lacks, such as void air and campfires, are air in a 1.8 world
        for (field in net.ccbluex.agent.world.block.Blocks::class.java.fields) {
            val block = field.get(null) as? Block ?: continue
            if (block !in gameBlocks) defineAbsent(block)
        }
    }

    @OptIn(net.ccbluex.agent.InternalAgentApi::class)
    private fun defineAbsent(block: Block) {
        block.define(
            defaultState = StateView(block, Blocks.AIR.blockData),
            item = null,
            kinds = emptySet(),
            tags = { false },
            friction = DEFAULT_FRICTION,
            speedFactor = 1f,
            jumpFactor = 1f,
            bounceRestitution = 0f,
            fallDistanceReduction = 0f,
        )
    }
}

/**
 * Defines the library's blocks and items from the 1.8 registries; the plugin calls it once the server started.
 *
 * @agent claude-opus-5-5
 */
public fun defineGameTypes() {
    GameTypes
}
