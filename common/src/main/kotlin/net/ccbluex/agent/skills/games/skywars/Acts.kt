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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.Throws
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.slotOf
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * How one tick of going somewhere went.
 *
 * @agent claude-opus-5-5
 */
internal enum class Step { ARRIVED, MOVING, BLOCKED }

private const val ARRIVAL_HEIGHT = 2.0
private const val PEARL_TICKS = 100

/**
 * Works on [plan] until it is done or out of reach; what to leave alone for a while, or null.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.act(plan: Plan): Any? = when (plan.activity) {
    Activity.LOOT -> {
        loot({ chestsNear(island, OWN_CHEST_RANGE) }, "a chest", leftovers = true)
        null
    }
    Activity.MIDDLE -> {
        val point = plan.point
        if (plan.chests.isEmpty() && point != null) {
            stay(point, MIDDLE_WITHIN, "the middle", "Waiting for the refill")
        } else {
            loot({ middleChests() }, "the middle", leftovers = true)
            null
        }
    }
    Activity.RAID -> {
        val point = plan.point
        loot({ if (point == null) emptyList() else chestsNear(point, OWN_CHEST_RANGE) }, "an empty island", false)
        null
    }
    Activity.HUNT -> hunt(plan)
    Activity.GATHER -> plan.point?.let { gather(it) }
    Activity.PICKUP -> pickup(plan)
    Activity.HOLD -> holdSpot("Holding its ground", counts = true)
    Activity.RETREAT -> holdSpot("Backing off to heal", counts = false)
    Activity.REGROUP -> regroup(plan)
    Activity.DOOM -> plan.point?.let { stay(it, DOOM_WITHIN, "the middle", "Holding the middle") }
}

/**
 * One tick on foot, over a bridge or in flight towards [to]: arrived within [within], on the way, or out of reach
 * for now. [key] tells one goal from another for the progress check.
 *
 * @agent claude-opus-5-5
 */
internal fun Round.step(to: Vec3, within: Double, key: Any, name: String, toOpponent: Boolean = false): Step {
    val position = player.position()
    if (position.horizontalDistanceTo(to) <= within && abs(position.y - to.y) < ARRIVAL_HEIGHT) {
        crossing?.stop(agent, "arrived", ticks)
        return Step.ARRIVED
    }
    val flying = crossing?.tick(agent, terrain, to, toOpponent, ticks) == true
    if (!flying) agent.walkTo(to, within)
    goal = when {
        flying -> "Flying to $name"
        agent.navigator.isBuilding -> "Bridging to $name"
        else -> "Going to $name"
    }
    val stranded = !flying && agent.navigator.isStranded
    return if (progress.check(key, position.distanceTo(to), stranded, ticks)) Step.MOVING else Step.BLOCKED
}

/**
 * Goes to [to] and stays there; returns what to avoid once it cannot get there.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Round.stay(to: Vec3, within: Double, name: String, there: String): Any? {
    val key = BlockPos.containing(to)
    while (true) {
        when (step(to, within, key, name)) {
            Step.ARRIVED -> {
                progress.reset()
                goal = there
                agent.walkTo(to, within)
            }
            Step.MOVING -> Unit
            Step.BLOCKED -> return key
        }
        scope.nextTick()
    }
}

/**
 * @agent claude-opus-5-5
 */
private suspend fun Round.hunt(plan: Plan): Any? {
    val id = plan.targetId ?: return null
    while (true) {
        val enemy = agent.body.level.getEntity(id) as? LivingEntity
        if (enemy == null || !enemy.isAlive || !senses.isOpponent(enemy)) return null
        val name = enemy.name.string
        if (plan.pearl) {
            return if (ticks >= pearlAt && enemy.onGround() && throwPearl(enemy.position())) {
                goal = "Pearling to $name"
                null
            } else {
                id
            }
        }
        when (step(enemy.position(), HUNT_WITHIN, id, name, toOpponent = true)) {
            Step.BLOCKED -> return id
            else -> if (plan.thirdParty) goal = "Third-partying $name"
        }
        scope.nextTick()
    }
}

/**
 * Throws an ender pearl that comes down on [to], once the sent rotation lands it there; false without a pearl or a
 * throw that gets there.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.throwPearl(to: Vec3): Boolean {
    val body = agent.body
    val slot = player.inventory.slotOf(ItemMatch.of(Items.ENDER_PEARL))
    if (slot < 0) return false
    val aim = Throws.pearlRotation(body.level, player, to, body.players()) ?: return false
    var thrown = false
    for (tick in 0..AIM_TICKS) {
        agent.takeMovement()
        body.hold(slot)
        body.look(aim, LookPurpose.AIM)
        if (Throws.pearlLandsAt(body.level, player, body.sentRotation, to, body.players())) {
            thrown = body.useItem(InteractionHand.MAIN_HAND)
            break
        }
        scope.nextTick()
    }
    body.releaseHotbarSlot()
    if (thrown) pearlAt = ticks + PEARL_TICKS
    return thrown
}

/**
 * @agent claude-opus-5-5
 */
private suspend fun Round.pickup(plan: Plan): Any? {
    val id = plan.targetId ?: return null
    while (true) {
        val item = agent.body.level.getEntity(id) as? ItemEntity
        if (item == null || !item.isAlive) return null
        if (step(item.position(), PICKUP_WITHIN, id, "loot") == Step.BLOCKED) return id
        goal = "Picking up loot"
        scope.nextTick()
    }
}

/**
 * @agent claude-opus-5-5
 */
private suspend fun Round.regroup(plan: Plan): Any? {
    val id = plan.targetId ?: return null
    while (true) {
        val teammate = agent.body.level.getEntity(id) as? LivingEntity
        if (teammate == null || !teammate.isAlive || !senses.isTeammate(teammate)) return null
        val name = teammate.name.string
        when (step(teammate.position(), REGROUP_WITHIN, id, name)) {
            Step.ARRIVED -> goal = "With $name"
            Step.MOVING -> Unit
            Step.BLOCKED -> return id
        }
        scope.nextTick()
    }
}

/**
 * The most ticks a pearl throw waits for the rotation that lands it.
 */
private const val AIM_TICKS = 6
private const val MIDDLE_WITHIN = 4.0
private const val DOOM_WITHIN = 6.0
private const val HUNT_WITHIN = 1.0
private const val PICKUP_WITHIN = 0.5
private const val REGROUP_WITHIN = 3.0
