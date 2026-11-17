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

package net.ccbluex.agent.skills.danger

import net.ccbluex.agent.world.entity.Spider
import net.ccbluex.agent.world.entity.Enderman
import net.ccbluex.agent.world.entity.Enemy
import net.ccbluex.agent.world.entity.Creeper
import net.ccbluex.agent.world.entity.Mob
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.item.ItemTags
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.block.Blocks
import net.ccbluex.agent.math.Vec3

/**
 * A survival player's reflexes, ticked by the game mode before its plan: swim to air before drowning, climb out of
 * powder snow, back away from a creeper about to blow, and fight the nearest hostile mob that comes close, or is at a
 * teammate nearby ([net.ccbluex.agent.skills.games.RoundSenses.isTeammate]), while armed and not outmatched, else
 * back away. While it is busy with one, the plan waits.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class Dangers {

    /**
     * The mob fought or fled from now; null when none.
     */
    public var threat: Mob? = null
        private set

    private var calmTicks = 0

    /**
     * Where the head was last out of water: in a flooded cave the way back there is the one way known to air.
     */
    private var lastAir: Vec3? = null

    /**
     * Whether the player is busy with a danger this tick. [sheltered] players, dug in for the night, only fight what
     * reaches them.
     */
    public fun tick(agent: Agent, sheltered: Boolean): Boolean {
        val player = agent.body.player
        if (!player.isUnderWater) lastAir = player.position()
        if (player.isUnderWater && player.airSupply < LOW_AIR) {
            // Under a cave's roof straight up leads nowhere
            val air = airNear(player) ?: lastAir ?: player.position().add(0.0, SURFACE_RISE, 0.0)
            agent.walkTo(air, 0.5)
            return true
        }
        // isInPowderSnow is only set as the player moves, after the keys of the tick are read; the blocks tell it now
        if (inPowderSnow(player)) {
            // Routes keep out of powder snow, so the keys head straight for firm ground, jumping to climb the snow
            agent.takeMovement()
            firmGround(player)?.let { ground ->
                agent.press(getDirectionalInputTowards(ground.subtract(player.position()), agent.body.inputYRot).copy(jump = true), exact = true)
            }
            return true
        }
        val hostiles = player.level().getEntitiesOfClass(Mob::class.java, player.boundingBox.inflate(ENGAGE_RANGE)) {
            it is Enemy && it.isAlive && !isPeaceful(it, player)
        }
        val creeper = hostiles.filterIsInstance<Creeper>().filter { it.distanceTo(player) < CREEPER_RANGE }.minByOrNull { it.distanceTo(player) }
        if (creeper != null && !sheltered) {
            agent.walkTo(player.position().add(away(player, creeper.position())), 1.0)
            engage(agent, creeper, fight = false)
            return true
        }
        val reach = if (sheltered) SHELTERED_RANGE else ENGAGE_RANGE
        val near = hostiles.filter { it !is Creeper && it.distanceTo(player) < reach && player.hasLineOfSight(it) }
        val target = near.minByOrNull { it.distanceTo(player) } ?: if (sheltered) null else guarded(agent, player)
        if (target != null) {
            // Without a sword or an axe a zombie takes twenty punches, and two mobs at once wear down an unarmoured
            // player: it backs off and lets its health come back, as a fight on would end it
            val outmatched = !isArmed(player) || player.health <= RETREAT_HEALTH ||
                near.size > 1 && player.health < OUTNUMBERED_HEALTH
            if (outmatched && near.isEmpty()) {
                // Not its fight to pick unarmed or hurt; the teammate is on its own
                return threat != null && calm(agent)
            }
            if (outmatched && !sheltered) {
                val centre = near.fold(Vec3.ZERO) { sum, mob -> sum.add(mob.position()) }.scale(1.0 / near.size)
                agent.walkTo(player.position().add(away(player, centre)), 1.0)
                engage(agent, target, fight = false)
            } else {
                engage(agent, target, fight = true)
            }
            return true
        }
        if (threat == null) return false
        return calm(agent)
    }

    /**
     * A moment's calm before going back to work, as a mob comes round a corner again; whether it still waits.
     */
    private fun calm(agent: Agent): Boolean {
        if (++calmTicks < CALM_TICKS && threat?.isAlive == true) return true
        agent.skills.fight?.disengage()
        threat = null
        return false
    }

    /**
     * The nearest hostile mob right at a teammate within [GUARD_RANGE] blocks, one the player sees.
     */
    private fun guarded(agent: Agent, player: Player): Mob? {
        val senses = agent.senses ?: return null
        return agent.body.players()
            .filter { it !== player && it.isAlive && it.distanceTo(player) < GUARD_RANGE && senses.isTeammate(it) }
            .flatMap { mate ->
                player.level().getEntitiesOfClass(Mob::class.java, mate.boundingBox.inflate(3.0)) {
                    it is Enemy && it !is Creeper && it.isAlive && !isPeaceful(it, mate) && player.hasLineOfSight(it)
                }
            }
            .minByOrNull { it.distanceTo(player) }
    }

    private fun inPowderSnow(player: Player): Boolean {
        val box = player.boundingBox.deflate(1.0E-3)
        return BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ),
        ).any { player.level().getBlockState(it).`is`(Blocks.POWDER_SNOW) }
    }

    private fun engage(agent: Agent, mob: Mob, fight: Boolean) {
        if (threat !== mob) calmTicks = 0
        threat = mob
        if (fight) agent.skills.fight?.engage(mob) else agent.skills.fight?.disengage()
    }

    /**
     * [FLEE_DISTANCE] blocks straight away from [from], level.
     */
    private fun away(player: Player, from: Vec3): Vec3 =
        player.position().subtract(from).multiply(1.0, 0.0, 1.0).normalize().scale(FLEE_DISTANCE)

    private fun isArmed(player: Player): Boolean = (0 until Inventory.INVENTORY_SIZE).any {
        val stack = player.inventory.getItem(it)
        stack.`is`(ItemTags.SWORDS) || stack.`is`(ItemTags.AXES)
    }

    /**
     * The nearest cell of air over water or ground within a few blocks that the player can swim to through water and
     * air, not one behind a wall, where the head comes up to breathe.
     */
    private fun airNear(player: Player): Vec3? {
        val level = player.level()
        val eyes = BlockPos.containing(player.eyePosition)
        val seen = hashSetOf(eyes)
        val open = ArrayDeque(listOf(eyes))
        while (open.isNotEmpty()) {
            val pos = open.removeFirst()
            if (level.getBlockState(pos).isAir && level.getFluidState(pos).isEmpty && !level.getBlockState(pos.below()).isAir) {
                // Over water the head comes up there with the feet in the water; on ground the feet stand there
                return Vec3.atBottomCenterOf(if (level.getFluidState(pos.below()).isEmpty) pos else pos.below())
            }
            for (direction in Direction.entries) {
                val next = pos.relative(direction)
                if (next.distChessboard(eyes) > AIR_RANGE || !seen.add(next)) continue
                if (level.getBlockState(next).getCollisionShape(level, next).isEmpty) open += next
            }
        }
        return null
    }

    /**
     * The nearest place within a few blocks to stand on with no powder snow at the feet or head.
     */
    private fun firmGround(player: Player): Vec3? {
        val level = player.level()
        val feet = player.blockPosition()
        return BlockPos.betweenClosed(feet.offset(-SNOW_RANGE, -2, -SNOW_RANGE), feet.offset(SNOW_RANGE, 2, SNOW_RANGE))
            .map(BlockPos::immutable)
            .filter { pos ->
                val below = pos.below()
                !level.getBlockState(pos).`is`(Blocks.POWDER_SNOW) && !level.getBlockState(pos.above()).`is`(Blocks.POWDER_SNOW) &&
                    level.getBlockState(pos).getCollisionShape(level, pos).isEmpty &&
                    level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && !level.getBlockState(below).`is`(Blocks.POWDER_SNOW)
            }
            .minByOrNull { it.distSqr(feet) }
            ?.let(Vec3::atBottomCenterOf)
    }

    /**
     * Mobs that leave a player alone: an enderman not looked at, a spider by day.
     */
    private fun isPeaceful(mob: Mob, player: Player): Boolean = when (mob) {
        is Enderman -> !mob.isCreepy
        is Spider -> player.level().isBrightOutside && mob.distanceTo(player) > SPIDER_RANGE
        else -> false
    }

    private companion object {
        const val LOW_AIR = 200
        const val SURFACE_RISE = 4.0
        const val ENGAGE_RANGE = 10.0
        const val GUARD_RANGE = 16f
        const val SHELTERED_RANGE = 2.5
        const val CREEPER_RANGE = 5.0
        const val FLEE_DISTANCE = 8.0
        const val SPIDER_RANGE = 3.0
        const val CALM_TICKS = 40
        const val RETREAT_HEALTH = 8f
        const val OUTNUMBERED_HEALTH = 16f
        const val SNOW_RANGE = 5
        const val AIR_RANGE = 8
    }
}
