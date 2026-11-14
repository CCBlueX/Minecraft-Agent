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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.skills.games.WayCost
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

private const val PLACE_REACH = 4.5
private const val PLACE_TICKS = 4
private const val SKIP_TICKS = 100
private const val RETRY_TICKS = 1200
private const val INTERCEPT_TICKS = 20
private const val LANDING_STEP = 0.5
private val STAND_HEIGHTS = intArrayOf(0, -1, -2, 1)

/**
 * COVER: places the bed defence block by block, or gets the blocks for it, until none is left for the agent.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.cover() {
    while (coverOnce("Covering the bed") { true }) waitTicks(1)
}

/**
 * Places the agent's next bed defence block while [still] holds, or takes a step towards blocks for it; false when
 * there is none left for it to place.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Activities.coverOnce(text: String, still: () -> Boolean): Boolean {
    if (!team.isBedAlive) return false
    val spot = plan.nextDefence(level, game, self.position(), ::isSkipping)
    plan.claim(game, spot?.pos)
    if (spot == null) return false
    if (slotOf(blocks(spot.fill)) < 0) {
        plan.claim(game, null)
        return obtainBlocks(spot.fill)
    }
    goal = text
    place(spot, still)
    return true
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.isSkipping(pos: BlockPos): Boolean = (skipped[pos] ?: 0L) > tick

/**
 * A spot that fails again (out of reach, someone in the way) is left alone for longer each time.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.skip(pos: BlockPos) {
    val times = (failures[pos] ?: 0) + 1
    failures[pos] = times
    skipped[pos] = tick + if (times < 2) SKIP_TICKS.toLong() else RETRY_TICKS.toLong() * times
    plan.failed(pos)
}

/**
 * Places the defence block of [spot] against its support: walks to a cell in reach outside the defence, looks at
 * the face it goes against, and places once the last placement is far enough back.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Activities.place(spot: Spot, still: () -> Boolean) {
    val target = spot.pos
    while (!isInPlacingReach(target)) {
        if (!still() || !isOpen(level, target)) return
        if (travel.walk(agent, standFor(target), 0.3) == Travel.Status.BLOCKED) {
            skip(target)
            return
        }
        waitTicks(1)
    }
    val face = support(level, target) ?: return
    val against = target.relative(face)
    val side = face.opposite
    val point = Vec3.atCenterOf(against).add(side.unitVec3.scale(0.5))
    look(point)
    while (tick < placeAt) {
        waitTicks(1)
        if (!still() || !isOpen(level, target) || !isInPlacingReach(target)) return
        look(point)
    }
    val slot = slotOf(blocks(spot.fill))
    if (slot < 0) return
    placeAt = tick + PLACE_TICKS
    agent.body.hold(slot)
    if (!agent.body.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(point, side, against, false))) skip(target)
}

/**
 * Out of the block's cells and within reach of it.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.isInPlacingReach(target: BlockPos): Boolean {
    val feet = self.blockPosition()
    return feet != target && feet.above() != target && eyes().distanceTo(Vec3.atCenterOf(target)) <= PLACE_REACH
}

/**
 * The nearest cell to stand in within reach of [target], outside the defence layers up to the target's, so the
 * agent never walls itself in; the team spawn without one.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.standFor(target: BlockPos): Vec3 {
    val here = self.position()
    var best: BlockPos? = null
    var bestDistance = Double.MAX_VALUE
    for (dx in -3..3) {
        for (dz in -3..3) {
            val horizontal = hypot(dx.toDouble(), dz.toDouble())
            if (horizontal < 1.5 || horizontal > 2.9) continue
            for (dy in STAND_HEIGHTS) {
                val feet = target.offset(dx, dy, dz)
                val blocked = plan.isInTheWay(feet, target) || plan.isInTheWay(feet.above(), target)
                if (blocked || !isStandable(feet)) continue
                val distance = Vec3.atBottomCenterOf(feet).distanceToSqr(here)
                if (distance < bestDistance) {
                    best = feet
                    bestDistance = distance
                }
                break
            }
        }
    }
    return best?.let(Vec3::atBottomCenterOf) ?: team.spawn
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.isStandable(feet: BlockPos): Boolean {
    val terrain = terrain()
    return terrain.isPassable(feet.x, feet.y, feet.z) && terrain.isPassable(feet.x, feet.y + 1, feet.z) &&
        isSolid(level, feet.below())
}

/**
 * DEFEND, while the alarm holds: home first, then waiting where the intruder lands, covering the bed, or watching
 * by it.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.defend() {
    while (true) {
        val enemy = intruder ?: return
        val name = enemy.scoreboardName
        val alarm = { intruder === enemy && isOnIsland() }
        when {
            !isOnIsland() -> {
                goal = "$name is at our base, heading home"
                travel.bridgeTo(agent, team.spawn, 3.0, tick)
            }
            intercept(enemy) -> Unit
            coverOnce("$name is coming, covering the bed", alarm) -> Unit
            else -> watch(enemy)
        }
        waitTicks(1)
    }
}

/**
 * One tick by the bed, weapon out, facing [enemy].
 *
 * @agent claude-opus-5-5
 */
private fun Activities.watch(enemy: Player) {
    goal = "Watching for ${enemy.scoreboardName}"
    plan.bridge(game, null)
    val spot = bedGuardSpot()
    if (self.position().horizontalDistanceTo(spot) > 1.5) {
        travel.walk(agent, spot, 1.0)
    } else {
        holdWeapon()
        look(enemy.eyePosition)
    }
}

/**
 * Three blocks from the bed toward the spawn.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.bedGuardSpot(): Vec3 {
    val bed = team.bed?.let(Vec3::atBottomCenterOf) ?: return team.spawn
    val towards = team.spawn.subtract(bed).multiply(1.0, 0.0, 1.0)
    if (towards.lengthSqr() < 1.0E-3) return team.spawn
    return bed.add(towards.normalize().scale(3.0))
}

/**
 * Gets ready for [enemy] on the way while it is the intruder: a careful agent covers the bed first while there is
 * time and blocks, then it waits with its sword where the enemy has to land, the spot of its island nearest them.
 * False when that spot is off the island.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Activities.intercept(enemy: Player): Boolean {
    val name = enemy.scoreboardName
    say(Callout.Incoming(name))
    var spot: Vec3? = null
    var spotAt = 0L
    while (intruder === enemy && isOnIsland()) {
        val far = enemy.position().distanceTo(threats.base(team)) > GUARD_RANGE
        val careful = team.isBedAlive && far && traits.caution > 0.4
        if (careful && coverOnce("$name is coming, covering the bed") { intruder === enemy }) {
            waitTicks(1)
            continue
        }
        val landing = spot?.takeIf { tick - spotAt < INTERCEPT_TICKS } ?: landing(enemy.position()).also {
            spot = it
            spotAt = tick
        }
        if (landing.horizontalDistanceTo(team.spawn) > Activities.ISLAND + 6) return false
        stopDigging()
        plan.bridge(game, null)
        goal = "Waiting for $name"
        if (self.position().horizontalDistanceTo(landing) > 1) {
            travel.walk(agent, landing, 0.8)
        } else {
            holdWeapon()
            look(enemy.eyePosition)
        }
        waitTicks(1)
    }
    return true
}

/**
 * The last point with ground under it on the straight way from the agent toward [toward]: the edge of the island
 * an enemy coming that way lands on.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.landing(toward: Vec3): Vec3 {
    val from = self.position()
    val way = toward.subtract(from).multiply(1.0, 0.0, 1.0)
    val length = way.length()
    if (length < LANDING_STEP) return from
    val step = way.scale(LANDING_STEP / length)
    var last = from
    var point = from
    var walked = 0.0
    while (walked < length) {
        point = point.add(step)
        walked += LANDING_STEP
        if (WayCost.isVoidBelow(terrain(), point.x, point.y + 0.5, point.z)) break
        last = point
    }
    return Vec3.atBottomCenterOf(BlockPos.containing(last))
}
