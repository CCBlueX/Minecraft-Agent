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

import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockTags
import net.ccbluex.agent.world.entity.MinecartTNT
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * TNT minecart PvP: a rail beside the target, a TNT minecart on it, then a Flame arrow into the cart from at least
 * [SAFE] blocks away. A burning arrow sets the cart off at once; flint and steel does nothing to a cart. A cart
 * already beside the target is shot without placing another.
 *
 * @agent claude-opus-5-5
 */
internal class CartBomb : Technique(Skill.CART_BOMB, 45, 30) {

    private enum class Phase { BASE, RAIL, CART, SHOOT }

    private var phase = Phase.RAIL
    private var since = 0L
    private var phaseAt = 0L
    private var cell = BlockPos.ZERO
    private var cart: MinecartTNT? = null
    private var draw = 0
    private var shot = false

    override fun can(fight: BuiltInFight): Boolean {
        if (!flameBowLoaded(fight)) return false
        existing(fight)?.let { return it.distanceTo(fight.player) >= 3 }
        return fight.horizontalDistance() in MIN_RANGE..MAX_RANGE && fight.hands.has { it.isOf(Items.RAIL) } &&
            fight.hands.has { it.isOf(Items.TNT_MINECART) } && spot(fight) != null
    }

    private fun flameBow(stack: ItemStack): Boolean =
        stack.isOf(Items.BOW) && stack.enchantLevel(Enchantments.FLAME) > 0

    private fun flameBowLoaded(fight: BuiltInFight): Boolean {
        val slot = fight.hands.find(::flameBow) ?: return false
        return fight.hands.stack(slot).enchantLevel(Enchantments.INFINITY) > 0 ||
            fight.hands.has { it.isOf(Items.ARROW) }
    }

    /**
     * A TNT minecart right beside the target that the fighter can see.
     */
    private fun existing(fight: BuiltInFight): MinecartTNT? {
        val target = fight.target ?: return null
        return fight.body.level.getEntitiesOfClass(MinecartTNT::class.java, target.boundingBox.inflate(3.0, 2.0, 3.0))
            .firstOrNull { it.isAlive && it.distanceTo(target) <= NEAR_TARGET && fight.hasLineOfSight(it) }
    }

    /**
     * The free cell beside the target's feet the rail goes in, nearest the fighter so the arrow does not meet the
     * target first. Over a gap of one block when the fighter carries obsidian to fill it.
     */
    private fun spot(fight: BuiltInFight): BlockPos? {
        val target = fight.target ?: return null
        val feet = Building.landing(fight, target)
        val eye = fight.player.eyePosition
        val obsidian = fight.hands.has { it.isOf(Items.OBSIDIAN) }
        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val block = feet.offset(dx, 0, dz)
            if (!Building.air(fight, block) || !fight.mayPlace(block) || Building.occupied(fight, block, 1)) continue
            val ground = block.below()
            val filled = obsidian && Building.air(fight, ground) && fight.mayPlace(ground) &&
                Building.solid(fight, ground.below()) && !Building.occupied(fight, ground, 1)
            if (!Building.solid(fight, ground) && !filled) continue
            val distance = Building.top(ground).distanceTo(eye)
            if (distance <= PLACE_REACH && distance < bestDistance) {
                best = block
                bestDistance = distance
            }
        }
        return best
    }

    private fun phase(fight: BuiltInFight, phase: Phase) {
        this.phase = phase
        phaseAt = fight.tick
    }

    override fun begin(fight: BuiltInFight) {
        since = fight.tick
        shot = false
        fight.attackHeld = true
        cart = existing(fight)
        if (cart != null) return startDraw(fight)
        cell = spot(fight) ?: BlockPos.ZERO
        phase(fight, if (Building.solid(fight, cell.below())) Phase.RAIL else Phase.BASE)
    }

    private fun startDraw(fight: BuiltInFight) {
        fight.hands.hold(::flameBow)
        fight.body.startUsingItem(InteractionHand.MAIN_HAND)
        draw = fight.slow(DRAW_HARD, DRAW_SLOWEST)
        phase(fight, Phase.SHOOT)
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        if (fight.tick - since > MAX_TICKS) return false
        val elapsed = fight.tick - phaseAt
        when (phase) {
            Phase.BASE -> {
                if (!fight.hands.hold { it.isOf(Items.OBSIDIAN) } || !Building.air(fight, cell.below())) return false
                if (!place(fight, cell.below(2))) return elapsed <= 4
                phase(fight, Phase.RAIL)
            }
            Phase.RAIL -> {
                if (!fight.hands.hold { it.isOf(Items.RAIL) } || !Building.air(fight, cell)) return false
                if (!place(fight, cell.below())) return elapsed <= 4
                phase(fight, Phase.CART)
            }
            Phase.CART -> {
                val rail = fight.body.level.getBlockState(cell).`is`(BlockTags.RAILS)
                if (!rail || !fight.hands.hold { it.isOf(Items.TNT_MINECART) }) return false
                // The cart is an entity: the minecart item used on the rail spawns it
                if (!place(fight, cell)) return elapsed <= 4
                cart = fight.body.level.getEntitiesOfClass(MinecartTNT::class.java, AABB(cell)).firstOrNull()
                    ?: return false
                startDraw(fight)
            }
            Phase.SHOOT -> return shoot(fight)
        }
        return true
    }

    /**
     * Backs off with the bow drawn and lets go once far enough from the cart, with the target still by it.
     */
    private fun shoot(fight: BuiltInFight): Boolean {
        val cart = cart?.takeIf { it.isAlive } ?: return false
        val target = fight.target ?: return false
        if (!fight.hands.mainHand.isOf(Items.BOW)) return false
        val at = cart.position()
        val away = fight.player.position().distanceTo(at)
        val held = fight.tick - phaseAt
        // The target walked off: the cart stays for later
        if (target.position().distanceTo(at) > LEFT_CART || held > draw + GIVE_UP_TICKS) return false
        fight.moveControlled = true
        if (away < SAFE + 1) fight.forward = -1f
        val rotation = Aiming.rotation(fight.player.eyePosition, at.add(0.0, 0.5, 0.0))
        fight.lookAt(rotation, 4.0)
        // From 5 blocks the cart spans about 6 degrees either side of its middle; a held draw takes a worse shot
        val tolerance = if (held > draw + HURRY_TICKS) 9.0 else 6.0
        if (held < draw || away < SAFE && held < draw + HURRY_TICKS || !fight.aimed(rotation, tolerance) ||
            !fight.hasLineOfSight(cart)
        ) {
            return true
        }
        fight.body.releaseUsingItem()
        shot = true
        return false
    }

    /**
     * Clicks the top of [against] with what is held once looking at it; false while it still turns or misses.
     */
    private fun place(fight: BuiltInFight, against: BlockPos): Boolean {
        val rotation = Aiming.rotation(fight.player.eyePosition, Building.top(against))
        fight.lookAt(rotation, 4.0)
        if (!fight.aimed(rotation, 10.0) && fight.tick - phaseAt < 3) return false
        return fight.clickLands() && Building.placeOn(fight, against, Direction.UP)
    }

    // Changing the held item lowers a drawn bow without shooting it
    override fun end(fight: BuiltInFight) {
        fight.hands.holdPrimary()
        if (!shot && fight.body.isUsingItem && fight.hands.mainHand.isOf(Items.BOW)) fight.body.releaseUsingItem()
        cart = null
    }

    private companion object {
        const val MIN_RANGE = 3.0
        const val MAX_RANGE = 6.0
        const val PLACE_REACH = 4.5
        const val NEAR_TARGET = 2.5
        const val LEFT_CART = 5.0
        const val MAX_TICKS = 80

        /**
         * The blast reaches 8 to 17 blocks; from here the fighter takes about half what the target by it does.
         */
        const val SAFE = 4.5
        const val DRAW_HARD = 20
        const val DRAW_SLOWEST = 30
        const val HURRY_TICKS = 15
        const val GIVE_UP_TICKS = 40
    }
}
