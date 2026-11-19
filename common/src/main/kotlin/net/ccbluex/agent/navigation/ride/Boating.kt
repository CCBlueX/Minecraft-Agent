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

package net.ccbluex.agent.navigation.ride

import net.ccbluex.agent.world.block.Fluid
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.sees
import net.ccbluex.agent.navigation.walk.Follower
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.item.ItemTags
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.ItemEntity
import net.ccbluex.agent.world.entity.Inventory
import net.ccbluex.agent.world.entity.AbstractBoat
import net.ccbluex.agent.world.ClipContext
import net.ccbluex.agent.math.AABB
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.HitResult
import net.ccbluex.agent.math.Vec3

/**
 * Crosses water the walk would swim in a boat from the inventory, as [net.ccbluex.agent.navigation.Options.canBoat]
 * lets it: places the boat on the water off the shore, boards it and leaves the ride to [Riding]; once that got off
 * at the far shore, breaks the boat and picks it up again. Every part has a time limit; water a boat failed on is
 * swum.
 *
 * @agent claude-opus-5-5
 */
internal class Boating(private val context: NavigationContext) {

    private var phase: Phase = Phase.Idle
    private var ticks = 0

    /**
     * Water a boat could not be put on or boarded at, swum instead.
     */
    private var refused: BlockPos? = null

    /**
     * Where the walk heads while this takes the tick, null to stand still.
     */
    var walkTarget: Vec3? = null
        private set

    /**
     * One tick of a crossing of the swim [follower] has ahead; true when it takes the tick, so the walk only goes to
     * [walkTarget]. A boat outside the hotbar is swapped in once the player stands still.
     *
     * @agent claude-sonnet-5-5
     */
    fun tick(follower: Follower): Boolean {
        val player = context.body.player
        walkTarget = null
        ticks++
        return when (val phase = phase) {
            Phase.Idle -> {
                val water = spot(follower) ?: return false
                val slot = boatSlot()
                if (slot < 0) return false
                if (!Inventory.isHotbarSlot(slot) && !context.canClickInventory) {
                    context.shouldStand = true
                    return true
                }
                enter(Phase.Placing(water, hold(slot)))
                true
            }

            is Phase.Placing -> place(phase)
            is Phase.Placed -> {
                val boat = context.body.level.getEntitiesOfClass(AbstractBoat::class.java, AABB(phase.water).inflate(1.5))
                    .firstOrNull { it.passengers.isEmpty() }
                when {
                    boat != null -> enter(Phase.Boarding(boat))
                    ticks > ANSWER_TICKS -> refuse(phase.water)
                }
                true
            }

            is Phase.Boarding -> board(phase)
            is Phase.Riding -> {
                if (player.isPassenger) return false
                enter(Phase.Breaking(phase.boat))
                true
            }

            is Phase.Breaking -> breakBoat(phase)
            is Phase.Collecting -> collect(phase)
        }
    }

    fun halt() {
        if (phase !is Phase.Riding) enter(Phase.Idle)
        walkTarget = null
    }

    private fun enter(next: Phase) {
        if (phase is Phase.Placing && next !is Phase.Placing) context.body.releaseHotbarSlot()
        phase = next
        ticks = 0
    }

    private fun refuse(water: BlockPos) {
        refused = water
        enter(Phase.Idle)
    }

    /**
     * The water block for the boat: on the first steps of a swim long enough to ride, off the shore with room for the
     * boat around it, within reach of the player standing on the shore.
     */
    private fun spot(follower: Follower): BlockPos? {
        val player = context.body.player
        if (!context.options().canBoat || player.isPassenger || !player.onGround() || player.isInWater) return null
        val swimAhead = follower.swimAhead
        if (swimAhead.size < MIN_SWIM) return null
        val level = context.body.level
        val eye = player.eyePosition
        return swimAhead.asSequence().take(SPOT_STEPS).map { it.to }.firstOrNull { pos ->
            pos != refused && level.getFluidState(pos).type == Fluid.WATER && level.getBlockState(pos.above()).isAir &&
                Vec3.atCenterOf(pos).distanceTo(eye) <= player.blockInteractionRange() - 0.5 &&
                // The boat is wider than the block: nothing solid may stand next to it, corners included
                BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))
                    .all { level.getBlockState(it).getCollisionShape(level, it).isEmpty }
        }
    }

    /**
     * A carried boat, -1 without.
     */
    private fun boatSlot(): Int {
        val inventory = context.body.player.inventory
        return (0 until Inventory.INVENTORY_SIZE).firstOrNull { inventory.getItem(it).`is`(ItemTags.BOATS) } ?: -1
    }

    /**
     * Holds inventory [slot] in a hotbar slot, swapping it in from the rest of the inventory; that hotbar slot.
     */
    private fun hold(slot: Int): Int {
        val body = context.body
        val hotbar = if (Inventory.isHotbarSlot(slot)) {
            slot
        } else {
            body.player.inventory.suitableHotbarSlot.also { body.swapWithHotbar(slot, it) }
        }
        body.selectHotbarSlot(hotbar)
        return hotbar
    }

    /**
     * Looks at the water's surface and uses the boat once the look meets that water first, as the boat item picks.
     */
    private fun place(phase: Phase.Placing): Boolean {
        val body = context.body
        val player = body.player
        if (ticks > TURN_TICKS) {
            refuse(phase.water)
            return false
        }
        body.selectHotbarSlot(phase.hotbar)
        val level = body.level
        val surface = Vec3(
            phase.water.x + 0.5,
            phase.water.y + level.getFluidState(phase.water).getHeight(level, phase.water).toDouble(),
            phase.water.z + 0.5,
        )
        body.look(ViewRotation.fromVector(surface.subtract(player.eyePosition)), LookPurpose.AIM)
        val eye = player.eyePosition
        val end = eye.add(body.sentRotation.viewVector.scale(player.blockInteractionRange()))
        val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, player, ClipContext.Fluid.ANY))
        if (hit.type == HitResult.Type.BLOCK && (hit as BlockHitResult).blockPos == phase.water &&
            player.getItemInHand(InteractionHand.MAIN_HAND).`is`(ItemTags.BOATS) &&
            body.useItem(InteractionHand.MAIN_HAND)
        ) {
            enter(Phase.Placed(phase.water))
        }
        return true
    }

    /**
     * Looks at the boat and gets in, once nothing stands between it and the eyes.
     */
    private fun board(phase: Phase.Boarding): Boolean {
        val body = context.body
        val player = body.player
        val boat = phase.boat
        if (player.isPassenger) {
            enter(Phase.Riding(boat))
            return false
        }
        if (!boat.isAlive || ticks > TURN_TICKS * 2) {
            // A boat it cannot get into is taken back, and the water swum
            refused = BlockPos.containing(boat.position())
            enter(Phase.Breaking(boat))
            return true
        }
        body.look(ViewRotation.fromVector(boat.boundingBox.center.subtract(player.eyePosition)), LookPurpose.AIM)
        if (ticks % CLICK_TICKS == 0 && body.sees(boat)) body.interact(boat, InteractionHand.MAIN_HAND)
        return true
    }

    /**
     * Hits the boat left at the shore until it breaks, at full strength, as a player does.
     */
    private fun breakBoat(phase: Phase.Breaking): Boolean {
        val body = context.body
        val player = body.player
        val boat = phase.boat
        if (!boat.isAlive) {
            enter(Phase.Collecting(boat.position()))
            return true
        }
        val distance = boat.distanceTo(player)
        if (ticks > BREAK_TICKS || distance > player.entityInteractionRange() + LEFT_BEHIND) {
            enter(Phase.Idle)
            return false
        }
        body.look(ViewRotation.fromVector(boat.boundingBox.center.subtract(player.eyePosition)), LookPurpose.AIM)
        if (distance > player.entityInteractionRange()) {
            walkTarget = boat.position()
        } else if (player.getAttackStrengthScale(0.5f) >= FULL_STRENGTH && body.sees(boat)) {
            body.attack(boat)
        }
        return true
    }

    /**
     * Walks onto the boat the broken one dropped.
     */
    private fun collect(phase: Phase.Collecting): Boolean {
        val item = context.body.level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(phase.at, 6.0, 4.0, 6.0))
            .firstOrNull { it.item.`is`(ItemTags.BOATS) }
        if (item == null) {
            // The drop shows up a tick or two after the boat went
            if (ticks > ANSWER_TICKS) enter(Phase.Idle)
            return ticks <= ANSWER_TICKS
        }
        if (ticks > COLLECT_TICKS) {
            enter(Phase.Idle)
            return false
        }
        walkTarget = item.position()
        return true
    }

    /**
     * @agent claude-opus-5-5
     */
    private sealed interface Phase {

        data object Idle : Phase

        class Placing(val water: BlockPos, val hotbar: Int) : Phase

        class Placed(val water: BlockPos) : Phase

        class Boarding(val boat: Entity) : Phase

        class Riding(val boat: Entity) : Phase

        class Breaking(val boat: Entity) : Phase

        class Collecting(val at: Vec3) : Phase
    }

    private companion object {

        /**
         * Swim steps in a row worth a boat; fewer are swum.
         */
        const val MIN_SWIM = 5

        /**
         * The first steps of the swim the boat may go on.
         */
        const val SPOT_STEPS = 3
        const val TURN_TICKS = 20
        const val ANSWER_TICKS = 10
        const val CLICK_TICKS = 5
        const val BREAK_TICKS = 200
        const val COLLECT_TICKS = 100
        const val FULL_STRENGTH = 0.9f

        /**
         * How much farther than a hit reaches the boat may lie, walked up to; farther it is left behind.
         */
        const val LEFT_BEHIND = 3.0
    }
}
