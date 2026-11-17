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

package net.ccbluex.agent.skills.gather

import net.ccbluex.agent.world.item.DyeColor
import net.ccbluex.agent.world.entity.Sheep
import net.ccbluex.agent.world.entity.Animal
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.bestSlot
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.tick.waitTicks
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.item.Items
import net.ccbluex.agent.world.block.Blocks
import kotlin.math.abs

/**
 * Hunts the nearest grown animal of [prey] within 32 blocks that no player keeps and that stands clear of powder
 * snow, where a chase sinks in and freezes; for wool, sheep of the colour it carries most come first, so the wool
 * makes a bed. With none in reach it explores. Whether it got the animal, or somewhere new to look.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public suspend fun Agent.huntFor(prey: Prey): Boolean {
    val player = body.player
    val level = body.level
    val animals = level.getEntitiesOfClass(Animal::class.java, player.boundingBox.inflate(32.0)) { animal ->
        animal.isAlive && !animal.isBaby && prey.matches(animal) && !isLivestock(animal) &&
            BlockPos.betweenClosed(animal.blockPosition().offset(-1, -1, -1), animal.blockPosition().offset(1, 0, 1))
                .none { level.getBlockState(it).`is`(Blocks.POWDER_SNOW) }
    }
    val inventory = player.inventory
    val colour = DyeColor.entries.maxBy { inventory.countItem(Items.WOOL[it.ordinal]) }
        .takeIf { inventory.countItem(Items.WOOL[it.ordinal]) > 0 }
    val animal = animals.minByOrNull { animal ->
        val other = colour != null && animal is Sheep && animal.color != colour
        // A sheep of another colour counts as 24 blocks farther
        animal.distanceTo(player) + if (other) 24f else 0f
    } ?: return explore()
    listener.step("hunting ${animal.typeId.removePrefix("minecraft:")} at ${animal.blockPosition().toShortString()}")
    return hunt(animal)
}

/**
 * Kills [animal] for what it drops and picks that up: walks up with the best weapon carried and hits it on a full
 * charge once it looks at it, as a player hunts. False when it got away or [limitTicks] passed.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
@JvmOverloads
public suspend fun Agent.hunt(animal: LivingEntity, limitTicks: Int = 600): Boolean {
    // The fight ability fights opponents that fight back and lands nothing on an animal that only runs
    skills.fight?.disengage()
    val weapon = body.player.inventory.bestSlot(GearScore::weaponScore)
    try {
        val hotbar = if (weapon >= 0) body.hold(weapon) else -1
        tickUntil { ticks ->
            if (!animal.isAlive || ticks >= limitTicks || !body.player.isAlive) return@tickUntil true
            if (hotbar >= 0) body.selectHotbarSlot(hotbar)
            walkTo(animal.position(), 1.5)
            val eyes = body.player.eyePosition
            val aim = ViewRotation.fromVector(animal.boundingBox.center.subtract(eyes))
            body.look(aim, LookPurpose.AIM)
            val sent = body.sentRotation
            val aimed = abs(Mth.wrapDegrees(sent.yRot - aim.yRot)) < 20f && abs(sent.xRot - aim.xRot) < 20f
            val reach = body.maxAttackReach
            if (aimed && body.attackStrength >= 0.95f && animal.boundingBox.distanceToSqr(eyes) <= reach * reach &&
                navigator.canHit(animal)
            ) {
                body.attack(animal)
            }
            false
        }
    } finally {
        body.releaseHotbarSlot()
    }
    if (animal.isAlive) return false
    // Drops come out as the death animation ends
    waitTicks(25)
    pickUpItems(animal.position())
    return true
}
