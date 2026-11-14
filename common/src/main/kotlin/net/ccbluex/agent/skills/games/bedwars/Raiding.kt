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

import net.ccbluex.agent.skills.ItemMatch
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.skills.hold
import net.ccbluex.agent.skills.shop.Wishes
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

private const val BED_REACH = 4.0
private const val DIG_REACH = 4.5
private const val ALMOST_THERE = 14.0
private const val RETRY_TICKS = 1200
private const val RAY_STEP = 0.2

/**
 * Wool an agent keeps for bridging to a generator or after a player.
 */
internal const val TRAVEL_WOOL = 12

private val SHEARS = ItemMatch.of(Items.SHEARS)
private val AXE = ItemMatch.tag(ItemTags.AXES)
private val PICKAXE = ItemMatch.tag(ItemTags.PICKAXES)
private val SWORD = ItemMatch.tag(ItemTags.SWORDS)

/**
 * RUSH: the kit from the island first (wool for the bridge, a pickaxe for a covered bed, a sword and armor), then
 * over to the enemy bed it goes for, which it breaks through whatever covers it. Out of wool on the way, it heads
 * home for more; with wool left after a fight out there, it goes on.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.rush() {
    while (true) {
        val target = rushTarget() ?: return
        val bed = target.bed ?: return
        rushing = target.name
        val wool = count(Wishes.WOOL)
        if (setOut && wool == 0) setOut = false
        if (!setOut && !isOnIsland() && wool >= TRAVEL_WOOL) setOut = true
        if (!setOut) {
            if (!isOnIsland()) {
                goal = "Heading home for wool"
                plan.bridge(game, null)
                travel.bridgeTo(agent, team.spawn, 2.0, tick)
                waitTicks(1)
                continue
            }
            if (kitUp(target)) {
                waitTicks(1)
                continue
            }
            setOut = true
            say(Callout.GoingFor(target.name))
        }
        if (!breakBed(target, bed)) return
        waitTicks(1)
    }
}

/**
 * Bridges to [bed] and digs through to it while [target] stays the one to rush; false when the way is blocked,
 * which leaves that team for later.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Activities.breakBed(target: BedWarsTeam, bed: BlockPos): Boolean {
    val centre = Vec3.atCenterOf(bed)
    val stillTarget = { rushTarget()?.name == target.name }
    while (eyes().distanceTo(centre) > BED_REACH) {
        if (count(Wishes.WOOL) == 0 || !stillTarget()) return true
        goal = "Rushing ${target.name}"
        val status = travel.bridgeTo(agent, centre, BED_REACH - 1.5, tick)
        plan.bridge(game, agent.navigator.placesAhead)
        if (status == Travel.Status.BLOCKED) {
            avoided[target.name] = tick + RETRY_TICKS
            travel.forget()
            return false
        }
        waitTicks(1)
    }
    plan.bridge(game, null)
    goal = "Breaking ${target.name}'s bed"
    while (target.isBedAlive && stillTarget() && eyes().distanceTo(centre) <= BED_REACH) {
        dig(digTarget(bed))
        waitTicks(1)
    }
    stopDigging()
    return true
}

/**
 * The nearest enemy bed that still stands: the one it already goes for, one no teammate goes for, then the rest.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.rushTarget(): BedWarsTeam? = senses.teams
    .filter { it.name != team.name && it.isBedAlive && it.bed != null && it.members.isNotEmpty() }
    .minByOrNull { other ->
        other.spawn.distanceTo(team.spawn) +
            20 * plan.goingFor(other.name, game) +
            (if (other.name == rushing) -15 else 0) +
            (if ((avoided[other.name] ?: 0L) > tick) 2000 else 0)
    }

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.isAlmostThere(): Boolean {
    val target = teamNamed(rushing) ?: return false
    val bed = target.bed ?: return false
    return target.isBedAlive && self.position().distanceTo(Vec3.atCenterOf(bed)) < ALMOST_THERE
}

/**
 * HUNT: after the nearest player of a bedless team, with some wool for the way first.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.hunt() {
    while (true) {
        val prey = threats.prey(self, team)?.takeIf { tick >= huntAgainAt } ?: return
        if (count(Wishes.WOOL) < TRAVEL_WOOL / 2 && isOnIsland()) {
            if (!obtain(Wishes.wool(16, 0.0))) return
        } else {
            goal = "Hunting ${prey.scoreboardName}"
            if (!chase(prey)) {
                huntAgainAt = tick + RETRY_TICKS
                return
            }
        }
        waitTicks(1)
    }
}

/**
 * HELP: to the nearest teammate with an enemy on them, and at that enemy.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Activities.help() {
    while (true) {
        val brawl = threats.brawl(self, team) ?: return
        goal = "Helping ${brawl.mate.scoreboardName}"
        if (!chase(brawl.enemy)) return
        waitTicks(1)
    }
}

/**
 * One tick after [prey]: closer, or at it once in reach; false when it cannot get there.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.chase(prey: Player): Boolean {
    if (self.distanceTo(prey) > Threats.FIGHT_RANGE || !threats.isEngageable(self, prey, terrain())) {
        if (travel.bridgeTo(agent, prey.position(), Threats.MELEE - 1, tick) == Travel.Status.BLOCKED) return false
        plan.bridge(game, agent.navigator.placesAhead)
    } else {
        plan.bridge(game, null)
        fight(prey)
    }
    return true
}

/**
 * The bed, or the first block placed in the round between the agent's eyes and it. Map blocks are never the target,
 * and neither is the agent's own bed.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.digTarget(bed: BlockPos): BlockPos {
    val eye = eyes()
    val centre = Vec3.atCenterOf(bed)
    val way = centre.subtract(eye)
    val length = way.length()
    val direction = way.normalize()
    val own = team.bed?.let { bedHalves(level, it) }.orEmpty()
    var step = 0.0
    while (step < length) {
        val pos = BlockPos.containing(eye.add(direction.scale(step)))
        step += RAY_STEP
        if (pos in own) continue
        if (isBed(level, pos)) return pos
        if (isSolid(level, pos) && senses.isPlayerPlaced(pos)) return pos
    }
    return bed
}

/**
 * Digs [pos] with the right tool from where it stands, walking up to it first when out of reach.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.dig(pos: BlockPos) {
    val centre = Vec3.atCenterOf(pos)
    if (eyes().distanceTo(centre) > DIG_REACH) {
        stopDigging()
        travel.walk(agent, Vec3.atBottomCenterOf(pos), 2.0)
        return
    }
    val face = Direction.getApproximateNearest(eyes().subtract(centre))
    look(centre)
    val body = agent.body
    if (pos != digging) {
        stopDigging()
        digging = pos
        val tool = tool(pos)
        if (tool >= 0) body.hold(tool) else body.releaseHotbarSlot()
        body.startDestroy(pos, face)
    } else {
        body.continueDestroy(pos, face)
    }
}

/**
 * Shears for wool, an axe for wood, else a pickaxe; -1 for none.
 *
 * @agent claude-opus-5-5
 */
private fun Activities.tool(pos: BlockPos): Int {
    val state = level.getBlockState(pos)
    if (state.`is`(BlockTags.WOOL)) slotOf(SHEARS).takeIf { it >= 0 }?.let { return it }
    if (state.`is`(BlockTags.PLANKS) || state.`is`(BlockTags.LOGS)) slotOf(AXE).takeIf { it >= 0 }?.let { return it }
    return slotOf(PICKAXE)
}

/**
 * @agent claude-opus-5-5
 */
internal fun Activities.stopDigging() {
    if (digging == null) return
    digging = null
    agent.body.stopDestroy()
}

/**
 * Holds the best sword, ready for an enemy about to land.
 *
 * @agent claude-opus-5-5
 */
internal fun Activities.holdWeapon() {
    val inventory = self.inventory
    var best = -1
    var bestScore = 0.0
    for (slot in 0 until inventory.containerSize) {
        val stack = inventory.getItem(slot)
        if (!SWORD.matches(stack)) continue
        val score = GearScore.weaponScore(stack)
        if (score > bestScore) {
            best = slot
            bestScore = score
        }
    }
    if (best >= 0) agent.body.hold(best)
}
