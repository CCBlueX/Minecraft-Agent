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

package net.ccbluex.agent.world.block

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.world.TagKey
import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.namespaced
import java.util.concurrent.ConcurrentHashMap

/**
 * A block type by its modern id, such as `minecraft:oak_stairs`. One instance per id, so blocks compare with `==`.
 * The adapter of the running game tells it its facts ([define]); until then they are those of a plain stone block.
 *
 * @agent claude-opus-5-5
 */
public class Block private constructor(public val id: String) {

    /**
     * The slipperiness: 0.6, ice 0.98.
     */
    public var friction: Float = DEFAULT_FRICTION
        private set
    public var speedFactor: Float = 1f
        private set
    public var jumpFactor: Float = 1f
        private set

    /**
     * How much of a fall a landing bounces back up: slime 1, beds 0.66.
     */
    public var bounceRestitution: Float = 0f
        private set

    /**
     * How much of the fall distance a landing takes off, such as hay.
     */
    public var fallDistanceReduction: Float = 0f
        private set

    /**
     * The dye colour of a bed; null for every other block.
     */
    public var color: DyeColor? = null
        private set

    public var kinds: Set<BlockKind> = emptySet()
        private set
    private var tags: (TagKey<Block>) -> Boolean = { false }
    private var item: Item? = null
    private var defaultState: BlockState? = null

    public fun asItem(): Item = item ?: Item.AIR

    public fun defaultBlockState(): BlockState =
        defaultState ?: throw IllegalStateException("$id has no states; no game adapter defined it")

    public fun `is`(tag: TagKey<Block>): Boolean = tags(tag)

    public fun `is`(kind: BlockKind): Boolean = kind in kinds

    @InternalAgentApi
    @Suppress("LongParameterList")
    public fun define(
        defaultState: BlockState,
        item: Item?,
        kinds: Set<BlockKind>,
        tags: (TagKey<Block>) -> Boolean,
        friction: Float,
        speedFactor: Float,
        jumpFactor: Float,
        bounceRestitution: Float,
        fallDistanceReduction: Float,
        color: DyeColor? = null,
    ) {
        this.defaultState = defaultState
        this.item = item
        this.kinds = kinds
        this.tags = tags
        this.friction = friction
        this.speedFactor = speedFactor
        this.jumpFactor = jumpFactor
        this.bounceRestitution = bounceRestitution
        this.fallDistanceReduction = fallDistanceReduction
        this.color = color
    }

    override fun toString(): String = "Block{$id}"

    public companion object {
        private const val DEFAULT_FRICTION = 0.6f
        private val blocks = ConcurrentHashMap<String, Block>()

        /**
         * The block of [id]; `minecraft:` is assumed without a namespace.
         */
        @JvmStatic
        public fun of(id: String): Block = blocks.computeIfAbsent(namespaced(id)) { Block(it) }
    }
}

/**
 * What a block is, where the game tells it by its class.
 *
 * @agent claude-opus-5-5
 */
public enum class BlockKind {
    DOOR,
    TRAPDOOR,
    FENCE,
    FENCE_GATE,
    WALL,
    LADDER,
    STAIRS,
    BED,

    /**
     * Sand, gravel, concrete powder, anvils: falls when nothing is below.
     */
    FALLING,
    FIRE,
    FARMLAND,
    SLIME,

    /**
     * Has a block entity, such as a chest or a furnace.
     */
    ENTITY_BLOCK,

    /**
     * A door, trapdoor or gate a hand opens; iron ones need redstone.
     */
    OPENS_BY_HAND,

    /**
     * Does something when clicked with an empty hand (a button, a lever, a crafting table), so a click to place a
     * block against it would use it instead.
     */
    INTERACTIVE,
    CHEST,
    LEVER,
    BUTTON,
    PRESSURE_PLATE,
    CROP,
    NETHER_WART,
    COCOA,
    SWEET_BERRY_BUSH,
    SUGAR_CANE,
    CACTUS,
    BAMBOO,
    KELP_PLANT,
    STEM,
    ATTACHED_STEM,
}
