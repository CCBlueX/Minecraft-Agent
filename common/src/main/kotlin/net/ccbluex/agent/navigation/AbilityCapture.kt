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

package net.ccbluex.agent.navigation

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.ccbluex.agent.pathfinder.walk.BreakTimes
import net.ccbluex.agent.pathfinder.walk.Costs
import net.ccbluex.agent.pathfinder.walk.WalkAbilities
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.world.entity.Attributes
import net.ccbluex.agent.world.entity.Entity.Companion.BASE_SAFE_FALL_DISTANCE
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.item.ItemStack
import java.util.function.ToDoubleFunction
import kotlin.math.floor
import kotlin.math.max

private const val WATER_FALL = 23
internal const val MIN_SPRINT_FOOD = 6

/**
 * The walking abilities for one search, safe to read from any thread; [avoid] are feet blocks to keep away from,
 * [unbreakable] blocks never to break.
 *
 * From LiquidBounce: [Settings.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Settings.kt#L76-L94), [ModuleParkour.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/ModuleParkour.kt#L172-L184)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.captureWalk(avoid: LongSet, unbreakable: LongSet): WalkAbilities {
    val options = options()
    val player = body.player
    val placeableBlocks = if (options.canPlace) countPlaceableBlocks() else 0
    return WalkAbilities(
        canBreak = options.canBreak,
        canPlace = placeableBlocks > 0,
        placeableBlocks = placeableBlocks,
        canParkour = options.canParkour,
        riskyJumps = options.canParkour && options.riskyJumps,
        canSprint = player.foodData.foodLevel > MIN_SPRINT_FOOD,
        safeFall = safeFall(),
        waterFall = WATER_FALL,
        breakTicks = if (options.canBreak) {
            val ticks = BreakTimes.capture(hotbar().filterNot { it.isEmpty })
            val breakable = options.breakableBlocks
            ToDoubleFunction { state -> if (breakable.isBreakable(state)) ticks.applyAsDouble(state) else Double.POSITIVE_INFINITY }
        } else {
            ToDoubleFunction { Double.POSITIVE_INFINITY }
        },
        avoid = LongOpenHashSet(avoid),
        air = player.airSupply,
        motion = MotionTraits.capture(player),
        jumpClimbs = body.traits.jumpClimbsLadders,
        bridgeTicks = when {
            options.fastBridges -> Costs.RUN_BRIDGE
            options.bridging == Bridging.GODBRIDGE || options.bridging == Bridging.TELLY -> Costs.GODBRIDGE
            options.bridging == Bridging.AUTO && options.riskyBridges -> Costs.GODBRIDGE
            else -> Costs.NINJA_BRIDGE
        },
        unbreakable = LongOpenHashSet(unbreakable),
        health = player.health,
    )
}

/**
 * Highest drop onto ground, in blocks: the max fall option on top of what the safe fall distance has over the
 * vanilla one.
 *
 * From LiquidBounce: [Settings.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Settings.kt#L69-L73)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal fun NavigationContext.safeFall(): Int {
    val safeFallDistance = floor(body.player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)).toInt()
    return options().maxFall + max(0, safeFallDistance - BASE_SAFE_FALL_DISTANCE)
}

/**
 * @agent claude-opus-5-5
 */
internal fun NavigationContext.hotbar(): List<ItemStack> {
    val inventory = body.player.inventory
    return (0 until Inventory.getSelectionSize()).map(inventory::getItem)
}

/**
 * @agent claude-fable-5-2
 */
private fun NavigationContext.countPlaceableBlocks(): Int {
    val blocks = options().placeableBlocks
    val offhand = body.player.inventory.getItem(Inventory.SLOT_OFFHAND)
    return (listOf(offhand) + hotbar()).sumOf { stack -> if (blocks.isPlaceable(stack)) stack.count else 0 }
}
