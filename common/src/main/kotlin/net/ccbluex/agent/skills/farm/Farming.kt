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

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.INVENTORY_SLOTS
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.aimAt
import net.ccbluex.agent.skills.bestSlot
import net.ccbluex.agent.skills.dig
import net.ccbluex.agent.skills.diggingSlot
import net.ccbluex.agent.skills.fight.enchantLevel
import net.ccbluex.agent.skills.gather.fits
import net.ccbluex.agent.skills.gather.pickUp
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.reach
import net.ccbluex.agent.skills.seenPoint
import net.ccbluex.agent.skills.slotOf
import net.ccbluex.agent.skills.useBlock
import net.ccbluex.agent.skills.useItemOn
import net.ccbluex.agent.tick.TickScope
import net.ccbluex.agent.tick.tickConditional
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.block.BlockStateProperties
import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.item.ItemTags
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.item.Item
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.world.block.BlockState
import net.ccbluex.agent.math.Vec3
import java.util.function.IntConsumer
import kotlin.math.floor

/**
 * Tends [farm] until nothing is left to do there, nearest first: harvests what is ripe and sows it again, sows empty
 * soil, uses bone meal, tills and waters new farmland, picks up the drops, and empties the harvest into the chest
 * once the inventory is full, as [Farm] allows. It clicks only blocks in sight. How many crops it harvested.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.farm(farm: Farm): Int {
    val skipped = HashSet<Any>()
    val tries = HashMap<Any, Int>()
    var harvested = 0
    var sowed = 0
    var boneMealAt = 0L
    var chest = farm.chest
    while (body.player.isAlive) {
        if (chest != null && isInventoryFull() && !deposit(chest)) chest = null
        val work = nextWork(farm, skipped, ticks >= boneMealAt) ?: break
        val done = when (work) {
            is Work.Harvest -> harvest(work.pos, farm).also { if (it) harvested++ }
            is Work.Sow -> sow(work.seed, work.soil, Direction.UP, work.soil.above()).also { if (it) sowed++ }
            is Work.Fertilize -> fertilize(work.pos).also { boneMealAt = ticks + farm.boneMealDelayTicks.random() }
            is Work.Till -> till(work.pos)
            is Work.Water -> water(work.pos)
            is Work.PickUp -> pickUp(work.item)
        }
        if (!done) listener.step("farm: failed $work from ${body.player.position()}")
        // A failed try gets one more, as a walk or a turn may fail once; done three times, the server keeps undoing it
        val attempts = tries.merge(work.key, 1, Int::plus) ?: 1
        if (attempts >= if (done) 3 else 2) skipped += work.key
        if (work !is Work.PickUp) waitTicks(farm.delayTicks.random())
    }
    listener.step("farmed: harvested $harvested, sowed $sowed")
    return harvested
}

/**
 * [farm] launched on [scope], for callers without coroutines; [done] gets how many crops it harvested.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public fun Agent.farm(farm: Farm, scope: TickScope, done: IntConsumer? = null): Job =
    scope.launch { done?.accept(farm(farm)) }

/**
 * One thing to do on a farm.
 *
 * @agent claude-opus-5-5
 */
private sealed interface Work {

    /**
     * What a failed try is remembered by, so it is not tried again this time round.
     */
    val key: Any

    data class Harvest(val pos: BlockPos) : Work {
        override val key: Any get() = pos
    }

    data class Sow(val soil: BlockPos, val seed: Item) : Work {
        override val key: Any get() = soil
    }

    data class Fertilize(val pos: BlockPos) : Work {
        override val key: Any get() = pos
    }

    data class Till(val pos: BlockPos) : Work {
        override val key: Any get() = pos
    }

    data class Water(val pos: BlockPos) : Work {
        override val key: Any get() = pos
    }

    data class PickUp(val item: ItemEntity) : Work {
        override val key: Any get() = item.id
    }
}

/**
 * The work nearest the eyes on [farm], leaving out what is [skipped]; without walking, only what is in reach and
 * sight.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.nextWork(farm: Farm, skipped: Set<Any>, canFertilize: Boolean): Work? {
    val level = body.level
    val player = body.player
    val inventory = player.inventory
    waterSpot(farm)?.takeIf { it !in skipped }?.let { return Work.Water(it) }
    val seeds = (0 until INVENTORY_SLOTS).map { inventory.getItem(it) }.filter(Crops::isSeed).map { it.item }.distinct()
    val sows = farm.canPlant && seeds.isNotEmpty()
    val fertilizes = farm.canUseBoneMeal && canFertilize && inventory.slotOf(ItemMatch.of(Items.BONE_MEAL)) >= 0
    val tills = farm.canTill && inventory.slotOf(ItemMatch.tag(ItemTags.HOES)) >= 0
    val eye = player.eyePosition
    val found = ArrayList<Pair<Work, Vec3>>()
    val area = farm.area
    val from = BlockPos.containing(area.minX, area.minY, area.minZ)
    val to = BlockPos.containing(area.maxX, area.maxY, area.maxZ)
    for (cursor in BlockPos.betweenClosed(from, to)) {
        if (cursor in skipped) continue
        val state = level.getBlockState(cursor)
        if (state.isAir) continue
        val pos = cursor.immutable()
        when {
            Crops.isRipe(level, pos, state) -> found += Work.Harvest(pos) to Vec3.atCenterOf(pos)
            fertilizes && Crops.isFertile(level, pos, state) -> found += Work.Fertilize(pos) to Vec3.atCenterOf(pos)
            sows && (state.`is`(BlockTags.SUPPORTS_CROPS) || state.`is`(BlockTags.SUPPORTS_NETHER_WART)) ->
                seedFor(pos, seeds)?.let { found += Work.Sow(pos, it) to Vec3.atCenterOf(pos.above()) }
            tills && Crops.isTillable(level, pos, state) -> found += Work.Till(pos) to Vec3.atCenterOf(pos.above())
        }
    }
    if (farm.canWalk && farm.pickUp != null) {
        for (item in level.getEntitiesOfClass(ItemEntity::class.java, area)) {
            if (item.isAlive && item.id !in skipped && farm.pickUp.matches(item.item) && fits(player, item.item) &&
                item.distanceTo(player) <= farm.pickUpRadius
            ) {
                found += Work.PickUp(item) to item.position()
            }
        }
    }
    found.sortBy { it.second.distanceToSqr(eye) }
    fun isInReach(work: Work) = when (work) {
        is Work.Harvest -> seenPoint(work.pos, null) != null
        is Work.Sow -> seenPoint(work.soil, Direction.UP) != null
        is Work.Fertilize -> seenPoint(work.pos, null) != null
        is Work.Till -> seenPoint(work.pos, Direction.UP) != null
        is Work.Water -> seenPoint(work.pos, null) != null
        is Work.PickUp -> false
    }
    return found.firstOrNull { (work, _) ->
        farm.canWalk && (work !is Work.Sow || farm.canWalkToPlant) || isInReach(work)
    }?.first
}

/**
 * The seed to sow on [soil]: what grows next to it if carried, as rows keep one crop, else the first of [seeds] that
 * grows there; null when none does or the soil is taken.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.seedFor(soil: BlockPos, seeds: List<Item>): Item? {
    val level = body.level
    if (!level.getBlockState(soil.above()).isAir) return null
    val beside = Direction.Plane.HORIZONTAL.map { level.getBlockState(soil.above().relative(it)).block.asItem() }
    val ordered = seeds.sortedByDescending { it in beside }
    return ordered.firstOrNull { Crops.canSow(level, soil, it) }
}

/**
 * Where to pour water on [farm]: the ground in the middle of its area, when it is farmland or could be, no water is
 * near, a water bucket is carried, and solid blocks around would hold the water in.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.waterSpot(farm: Farm): BlockPos? {
    if (!farm.canHydrate || body.player.inventory.slotOf(ItemMatch.of(Items.WATER_BUCKET)) < 0) return null
    val level = body.level
    val area = farm.area
    val center = area.center
    var pos = BlockPos.containing(center.x, area.maxY, center.z)
    while (pos.y > floor(area.minY) && level.getBlockState(pos).isAir) pos = pos.below()
    val state = level.getBlockState(pos)
    if (!state.`is`(Blocks.FARMLAND) && !state.`is`(BlockTags.TURNS_INTO_FARMLAND)) return null
    if (Crops.isWatered(level, pos)) return null
    val walls = Direction.Plane.HORIZONTAL.map { pos.relative(it) } + pos.below()
    return pos.takeIf { walls.all { level.getBlockState(it).isSolidRender } }
}

/**
 * Harvests the ripe crop at [pos]: picks a berry bush, digs the rest with the most Fortune if [farm] wants it, and
 * sows a crop, nether wart or cocoa again.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.harvest(pos: BlockPos, farm: Farm): Boolean {
    val level = body.level
    val state = level.getBlockState(pos)
    if (state.`is`(BlockKind.SWEET_BERRY_BUSH)) {
        return useBlock(pos) {
            val now = level.getBlockState(pos)
            !now.`is`(BlockKind.SWEET_BERRY_BUSH) || !now.isMaxAge
        }
    }
    val fortune = if (farm.shouldUseFortune) fortuneSlot() else -1
    if (!dig(pos, forDrops = false) { if (fortune >= 0) fortune else diggingSlot(it) }) return false
    val seed = Crops.seedOf(state)
    if (!farm.canPlant || seed == null) return true
    // Cocoa hangs on the side of the log it faces
    if (state.`is`(BlockKind.COCOA)) {
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        sow(seed, pos.relative(facing), facing.opposite, pos)
    } else {
        sow(seed, pos.below(), Direction.UP, pos)
    }
    return true
}

/**
 * The carried slot with the most Fortune; -1 when nothing has it.
 *
 * @agent claude-opus-5-5
 */
private fun Agent.fortuneSlot(): Int = body.player.inventory.bestSlot { it.enchantLevel(Enchantments.FORTUNE).toDouble() }

/**
 * Sows [seed] by clicking [face] of [soil], until it grows at [crop]; false when none is carried or it did not take.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.sow(seed: Item, soil: BlockPos, face: Direction, crop: BlockPos): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.of(seed))
    if (slot < 0) return false
    return useItemOn(slot, soil, face) { body.level.getBlockState(crop).block.asItem() == seed }
}

/**
 * Uses bone meal on the crop at [pos] until it grew.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.fertilize(pos: BlockPos): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.of(Items.BONE_MEAL))
    if (slot < 0) return false
    val before = body.level.getBlockState(pos)
    return useItemOn(slot, pos, null) { body.level.getBlockState(pos) != before }
}

/**
 * Hoes the top of [pos] into farmland.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.till(pos: BlockPos): Boolean {
    val slot = body.player.inventory.slotOf(ItemMatch.tag(ItemTags.HOES))
    if (slot < 0) return false
    return useItemOn(slot, pos, Direction.UP) { body.level.getBlockState(pos).`is`(Blocks.FARMLAND) }
}

/**
 * Digs out [pos] and pours a water bucket into the hole, looking at its floor or a wall inside it in sight, as a
 * bucket empties next to where the look meets a block.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.water(pos: BlockPos): Boolean {
    if (!dig(pos, forDrops = false)) return false
    val slot = body.player.inventory.slotOf(ItemMatch.of(Items.WATER_BUCKET))
    if (slot < 0) return false
    val insides = listOf(pos.below() to Direction.UP) + Direction.Plane.HORIZONTAL.map { pos.relative(it) to it.opposite }
    val (block, face) = insides.firstOrNull { (block, face) -> seenPoint(block, face) != null }
        ?: insides.first().takeIf { (block, face) -> reach(block, face) }
        ?: return false
    val level = body.level
    fun isWater() = level.getFluidState(pos).isSource
    try {
        val hotbar = body.hold(slot)
        aimAt(block, face, hotbar) ?: return false
        body.selectHotbarSlot(hotbar)
        body.useItem(InteractionHand.MAIN_HAND)
        body.swing(InteractionHand.MAIN_HAND)
        tickConditional(20) { takeMovement(); isWater() }
    } finally {
        body.releaseHotbarSlot()
    }
    return isWater()
}

/**
 * Whether no main inventory slot is free.
 */
private fun Agent.isInventoryFull(): Boolean = (0 until INVENTORY_SLOTS).none { body.player.inventory.getItem(it).isEmpty }

/**
 * Opens [chest] and moves the harvest into it, keeping the first stack of each seed for sowing; whether a slot came
 * free.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Agent.deposit(chest: BlockPos): Boolean {
    if (!useBlock(chest) { body.containerMenu != null }) return false
    val menu = body.containerMenu ?: return false
    val kept = HashSet<Item>()
    // A stack a tick, so every click sees the server's answer to the last, as the chest's contents arrive after it opens
    for (slot in menu.slots) {
        val stack = slot.item
        if (!slot.isPlayerInventory || !Crops.isProduce(stack)) continue
        if (Crops.isSeed(stack) && kept.add(stack.item)) continue
        waitTicks(1)
        if (body.containerMenu !== menu) return false
        body.clickContainer(slot.index, 0, ContainerInput.QUICK_MOVE)
    }
    waitTicks(2)
    body.closeContainer()
    return !isInventoryFull()
}
