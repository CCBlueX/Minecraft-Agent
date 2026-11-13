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

import net.minecraft.core.BlockPos
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

/**
 * The target stands on the ground close by: lava poured at its feet and scooped up again the next tick, which hurts
 * and sets it alight without leaving lava around.
 *
 * @agent claude-opus-5-5
 */
internal class LavaTap : Technique(Skill.LAVA_TAP, 42, 40) {

    private var cell = BlockPos.ZERO
    private var since = 0L
    private var pouredAt = 0L

    /**
     * Ticks the bucket is held over the cell before the pour: none on hard.
     */
    private var wait = 0

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        if (!fight.hands.has { it.isOf(Items.LAVA_BUCKET) } || fight.distance() > RANGE) return false
        if (target.hasEffect(MobEffects.FIRE_RESISTANCE) || !target.onGround()) return false
        return usable(fight, target.blockPosition())
    }

    private fun usable(fight: BuiltInFight, cell: BlockPos): Boolean {
        if (!Building.air(fight, cell) || !Building.solid(fight, cell.below())) return false
        val point = Building.centre(cell, 0.2)
        val eye = fight.player.eyePosition
        // The ray has to come down onto the floor of the cell, and the fighter must stay out of the lava
        return eye.y > point.y + 0.5 && eye.distanceTo(point) <= POUR_REACH &&
            Aiming.horizontal(fight.player.position(), point) >= 1.3
    }

    override fun begin(fight: BuiltInFight) {
        cell = fight.target?.blockPosition() ?: BlockPos.ZERO
        since = fight.tick
        pouredAt = 0
        wait = fight.slow(0, 8)
        fight.hands.hold { it.isOf(Items.LAVA_BUCKET) }
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        fight.moveControlled = true
        val point = Building.centre(cell, 0.2)
        fight.lookAt(point, 4.0)
        if (pouredAt == 0L) {
            if (!usable(fight, cell)) return false
            if (fight.tick - since < wait) return true
            if (!fight.aimedAtPoint(point)) return fight.tick - since < AIM_TICKS + wait
            if (!fight.hands.mainHand.isOf(Items.LAVA_BUCKET) || !fight.hands.use()) return false
            pouredAt = fight.tick
            return true
        }
        if (!Building.lavaSource(fight, cell)) return false
        if (fight.aimedAtPoint(point) && fight.hands.mainHand.isOf(Items.BUCKET) && fight.hands.use()) return false
        return fight.tick - pouredAt < SCOOP_TICKS
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val RANGE = 4.5
        const val POUR_REACH = 4.3
        const val AIM_TICKS = 6
        const val SCOOP_TICKS = 5
    }
}

/**
 * Stuck in a cobweb: water poured at its own feet washes the web away, then goes back into the bucket.
 *
 * From LiquidBounce: [GameBotBedWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBedWars.kt#L150-L180)
 *
 * @agent claude-opus-5-5
 */
internal class WebEscape : Technique(Skill.WEB_ESCAPE, 85, 40) {

    private var since = 0L
    private var pouredAt = 0L

    override fun can(fight: BuiltInFight): Boolean = fight.hands.has { it.isOf(Items.WATER_BUCKET) } && webbed(fight)

    private fun webbed(fight: BuiltInFight): Boolean {
        val feet = fight.player.blockPosition()
        val level = fight.body.level
        return level.getBlockState(feet).`is`(Blocks.COBWEB) || level.getBlockState(feet.above()).`is`(Blocks.COBWEB)
    }

    override fun begin(fight: BuiltInFight) {
        since = fight.tick
        pouredAt = 0
        fight.hands.hold { it.isOf(Items.WATER_BUCKET) }
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        fight.moveControlled = true
        if (pouredAt == 0L) {
            // Straight down the ray stops at the web, and the water goes into the free cell above it
            fight.lookXRot(90f, 4.0)
            if (!fight.aimedXRot(90f, AIM_TOLERANCE)) return fight.tick - since < AIM_TICKS
            if (!fight.hands.mainHand.isOf(Items.WATER_BUCKET) || !fight.hands.use()) return false
            pouredAt = fight.tick
            return true
        }
        val water = water(fight) ?: return false
        val xRot = if (water.y + 0.5 > fight.player.eyeY) -90f else 90f
        fight.lookXRot(xRot, 4.0)
        // Flowing water takes a few ticks to wash the web out
        if (webbed(fight) && fight.tick - pouredAt < WASH_TICKS) return true
        if (fight.aimedXRot(xRot, AIM_TOLERANCE) &&
            fight.hands.mainHand.isOf(Items.BUCKET) && fight.hands.use()
        ) {
            return false
        }
        return fight.tick - pouredAt < MAX_TICKS
    }

    private fun water(fight: BuiltInFight): BlockPos? =
        (0..3).map { fight.player.blockPosition().above(it) }.firstOrNull { Building.waterSource(fight, it) }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val AIM_TOLERANCE = 8.0
        const val AIM_TICKS = 8
        const val WASH_TICKS = 15
        const val MAX_TICKS = 25
    }
}

/**
 * The target poured water around itself, out of a cobweb or under a fall: an empty bucket takes it away.
 *
 * @agent claude-opus-5-5
 */
internal class Drain : Technique(Skill.DRAIN, 55, 20) {

    private var water: BlockPos? = null
    private var since = 0L

    override fun can(fight: BuiltInFight): Boolean = fight.hands.has { it.isOf(Items.BUCKET) } && water(fight) != null

    private fun water(fight: BuiltInFight): BlockPos? {
        val target = fight.target ?: return null
        val eye = fight.player.eyePosition
        val own = fight.player.blockPosition()
        return (0..2).map { target.blockPosition().above(it) }.firstOrNull { cell ->
            (cell.x != own.x || cell.z != own.z) && Building.waterSource(fight, cell) &&
                Building.centre(cell, 0.5).distanceTo(eye) <= REACH
        }
    }

    override fun begin(fight: BuiltInFight) {
        water = water(fight)
        since = fight.tick
        fight.hands.hold { it.isOf(Items.BUCKET) }
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val cell = water?.takeIf { Building.waterSource(fight, it) } ?: return false
        val point = Building.centre(cell, 0.5)
        fight.lookAt(point, 4.0)
        if (fight.aimedAtPoint(point) && fight.hands.mainHand.isOf(Items.BUCKET) && fight.hands.use()) return false
        return fight.tick - since < MAX_TICKS
    }

    override fun end(fight: BuiltInFight) {
        water = null
        fight.hands.holdPrimary()
    }

    private companion object {
        const val REACH = 4.3
        const val MAX_TICKS = 8
    }
}
