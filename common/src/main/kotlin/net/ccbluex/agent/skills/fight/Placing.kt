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

package net.ccbluex.agent.skills.fight

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.entity.ThrownEnderpearl
import net.ccbluex.agent.world.item.Items

/**
 * Low and pressed: pillars three blocks up out of reach.
 *
 * @agent claude-opus-5-5
 */
internal class Pillar : Technique(Skill.PILLAR, 65, 300) {

    private var column = BlockPos.ZERO
    private var baseY = 0.0
    private var placed = 0
    private var since = 0L

    override fun can(fight: BuiltInFight): Boolean {
        if (!fight.low(LOW) || fight.distance() >= CLOSE || !fight.player.onGround() ||
            !fight.hands.has { it.isBuildingBlock }
        ) {
            return false
        }
        val feet = fight.player.blockPosition()
        return (2..4).none { Building.solid(fight, feet.above(it)) } &&
            (0 until HEIGHT).all { fight.mayPlace(feet.above(it)) }
    }

    override fun begin(fight: BuiltInFight) {
        column = fight.player.blockPosition()
        baseY = fight.player.y
        placed = 0
        since = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.moveControlled = true
        fight.attackHeld = true
        fight.lookXRot(fight.down, 4.0)
        if (!fight.hands.hold { it.isBuildingBlock }) return false
        if (fight.player.onGround()) fight.jump = true
        val cell = column.above(placed)
        if (fight.player.y >= baseY + placed + RISE && !Building.solid(fight, cell) &&
            Building.placeOn(fight, cell.below(), Direction.UP)
        ) {
            placed++
        }
        return placed < HEIGHT && fight.tick - since < MAX_TICKS
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val LOW = 0.3
        const val CLOSE = 3.5
        const val HEIGHT = 3
        const val RISE = 1.1
        const val MAX_TICKS = 60
    }
}

/**
 * The target throws a pearl at the fighter: a block in its way, at the feet and then at eye level.
 *
 * @agent claude-opus-5-5
 */
internal class BlockPearl : Technique(Skill.BLOCK_PEARL, 60, 40) {

    private var pearl: ThrownEnderpearl? = null
    private var level = 0
    private var since = 0L

    override fun can(fight: BuiltInFight): Boolean = fight.hands.has { it.isBuildingBlock } && incoming(fight) != null

    private fun incoming(fight: BuiltInFight): ThrownEnderpearl? {
        val target = fight.target ?: return null
        val here = fight.player.position()
        return fight.body.level
            .getEntitiesOfClass(ThrownEnderpearl::class.java, fight.player.boundingBox.inflate(12.0, 8.0, 12.0))
            .firstOrNull { thrown ->
                val velocity = thrown.deltaMovement
                val towards = here.subtract(thrown.position())
                thrown.owner === target && velocity.lengthSqr() > 0.01 && towards.lengthSqr() > 0.01 &&
                    velocity.normalize().dot(towards.normalize()) > HEADING
            }
    }

    override fun begin(fight: BuiltInFight) {
        pearl = incoming(fight)
        level = 0
        since = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        val thrown = pearl?.takeIf { it.isAlive } ?: return false
        // Null while it still turns to the face, for a second at most
        val built = Building.wall(fight, thrown.position(), level) ?: return fight.tick - since < 20
        return built && ++level <= 1
    }

    override fun end(fight: BuiltInFight) {
        pearl = null
        fight.hands.holdPrimary()
    }

    private companion object {
        const val HEADING = 0.8
    }
}

/**
 * A cobweb on the feet of a grounded target close by, which keeps it from moving and from dodging crits.
 *
 * @agent claude-opus-5-5
 */
internal class Cobweb : Technique(Skill.COBWEB, 35, 80) {

    private var against = BlockPos.ZERO
    private var since = 0L

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        if (!fight.hands.has { it.isOf(Items.COBWEB) } || fight.distance() > RANGE || !target.onGround()) return false
        val feet = target.blockPosition()
        return fight.mayPlace(feet) && Building.air(fight, feet) && Building.solid(fight, feet.below())
    }

    override fun begin(fight: BuiltInFight) {
        against = fight.target?.blockPosition()?.below() ?: BlockPos.ZERO
        fight.hands.hold { it.isOf(Items.COBWEB) }
        since = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val rotation = Aiming.rotation(fight.player.eyePosition, Building.top(against))
        fight.lookAt(rotation, 3.0)
        if (!fight.navigator.canClick(against, Direction.UP)) return fight.tick - since <= AIM_TICKS
        if (fight.hands.mainHand.isOf(Items.COBWEB) && fight.clickLands())
            Building.placeOn(fight, against, Direction.UP)
        return false
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val RANGE = 3.5
        const val AIM_TICKS = 10
    }
}

/**
 * Falling hard: water poured on the ground just before landing, scooped up again after.
 *
 * From LiquidBounce: [GameBotBedWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBedWars.kt#L150-L180)
 *
 * @agent claude-opus-5-5
 */
internal class WaterClutch : Technique(Skill.WATER_CLUTCH, 90, 40) {

    private var water: BlockPos? = null
    private var since = 0L
    private var pouredAt = 0L

    override fun can(fight: BuiltInFight): Boolean {
        val player = fight.player
        return fight.hands.has { it.isOf(Items.WATER_BUCKET) } && !player.onGround() &&
            player.fallDistance > MIN_FALL && player.deltaMovement.y < -0.4 && ground(fight) != null
    }

    /**
     * The block the fighter lands on, a few below, with air over it; null over liquid or nothing.
     */
    private fun ground(fight: BuiltInFight): BlockPos? {
        val feet = fight.player.blockPosition()
        for (dy in 1..GROUND_DEPTH) {
            val below = feet.below(dy)
            if (!fight.body.level.getFluidState(below).isEmpty) return null
            if (Building.solid(fight, below)) {
                return below.takeIf { Building.air(fight, it.above()) && fight.mayPlace(it.above()) }
            }
        }
        return null
    }

    override fun begin(fight: BuiltInFight) {
        water = null
        since = fight.tick
        fight.hands.hold { it.isOf(Items.WATER_BUCKET) }
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.moveControlled = true
        fight.attackHeld = true
        val poured = water
        if (poured == null) {
            val ground = ground(fight) ?: return !fight.player.onGround() && fight.tick - since < MAX_TICKS
            val top = Building.top(ground)
            fight.lookAt(top, 4.0)
            if (top.distanceTo(fight.player.eyePosition) <= REACH && fight.aimedAtPoint(top) &&
                fight.hands.mainHand.isOf(Items.WATER_BUCKET) && fight.hands.use()
            ) {
                water = ground.above()
                pouredAt = fight.tick
            }
            return !fight.player.onGround() && fight.tick - since < MAX_TICKS
        }
        // Landed in it: the empty bucket takes the water back
        val point = Building.centre(poured, 0.5)
        fight.lookAt(point, 4.0)
        if (fight.player.onGround() || fight.player.isInWater) {
            if (Building.waterSource(fight, poured) && fight.aimedAtPoint(point) &&
                fight.hands.mainHand.isOf(Items.BUCKET)
            ) {
                fight.hands.use()
                return false
            }
        }
        return fight.tick - pouredAt < SCOOP_TICKS
    }

    override fun end(fight: BuiltInFight) {
        water = null
        fight.hands.holdPrimary()
    }

    private companion object {
        const val MIN_FALL = 4.0
        const val GROUND_DEPTH = 5
        const val REACH = 4.5
        const val MAX_TICKS = 40
        const val SCOOP_TICKS = 12
    }
}
