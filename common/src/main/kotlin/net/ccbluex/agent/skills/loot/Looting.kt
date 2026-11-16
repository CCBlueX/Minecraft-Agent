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

package net.ccbluex.agent.skills.loot

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.block.BlockEntityType
import kotlin.math.abs

/**
 * The built-in [Loot]: walks within reach of the chest, aims at it and clicks it until its menu opens (at most
 * [MAX_CLICKS], [SCREEN_TICKS] apart, never while eating), then takes one wanted stack every [TAKE_TICKS] and closes
 * the chest once nothing in it is wanted. A chest it cannot finish within [CHEST_TICKS] is given up until the refill.
 * A tick without [open] for the chest lets go of it, so a game mode that keeps away from players or backs off walks
 * there rather than to the chest.
 *
 * From LiquidBounce: [Chests.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Chests.kt#L68-L74), [Chests.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Chests.kt#L267-L290)
 *
 * @agent claude-opus-5-5
 */
internal class Looting(override val chests: Chests, private val wanted: Wanted) : Loot {

    private var chest: BlockPos? = null
    private var asked = false
    private var since = 0L
    private var clicks = 0
    private var clickedAt = NEVER
    private var openedAt = NEVER

    override val isLooting: Boolean
        get() = chest != null

    override fun open(chest: BlockPos): Boolean {
        if (chest == this.chest) {
            asked = true
            return true
        }
        if (chests.isOpened(chest)) return false
        chests.add(chest)
        this.chest = chest.immutable()
        asked = true
        clicks = 0
        clickedAt = NEVER
        openedAt = NEVER
        since = NEVER
        return true
    }

    /**
     * Whether the chest was asked for since the last tick; the next tick needs another [open].
     */
    internal fun stillWanted(): Boolean {
        val wanted = asked
        asked = false
        if (!wanted) chest = null
        return wanted
    }

    override fun tick(agent: Agent) {
        val pos = chest ?: return
        if (!stillWanted()) {
            if (agent.body.containerMenu != null) agent.body.closeContainer()
            return
        }
        val body = agent.body
        if (since == NEVER) since = agent.ticks
        val blockEntity = body.level.getBlockEntity(pos)
        if (blockEntity != BlockEntityType.CHEST && blockEntity != BlockEntityType.CONTAINER) {
            giveUp(agent, pos, "no container")
            return
        }
        if (openedAt != NEVER) {
            takeOne(agent, pos)
            return
        }
        if (agent.ticks - since > CHEST_TICKS) {
            giveUp(agent, pos, "took too long")
            return
        }

        val center = Vec3.atCenterOf(pos)
        val eyes = body.player.eyePosition
        if (eyes.distanceToSqr(center) > REACH * REACH) {
            agent.walkTo(center, WALK_RADIUS)
            return
        }
        agent.takeMovement()
        val aim = ViewRotation.fromVector(center.subtract(eyes))
        body.look(aim, LookPurpose.AIM)

        if (body.containerMenu != null && clicks > 0 && agent.ticks - clickedAt <= SCREEN_TICKS) {
            openedAt = agent.ticks
            return
        }
        if (agent.ticks - clickedAt < SCREEN_TICKS || agent.skills.eat?.wantsToEat == true) return
        if (clicks >= MAX_CLICKS) {
            giveUp(agent, pos, "no menu after $clicks clicks")
            return
        }
        // The click carries the rotation the server has, so it waits until that one points at the chest
        if (!isAimed(body.sentRotation, aim)) return
        val face = Direction.getApproximateNearest(eyes.subtract(center))
        val offset = Vec3(face.stepX * HALF, face.stepY * HALF, face.stepZ * HALF)
        val hit = BlockHitResult(center.add(offset), face, pos, false)
        body.useItemOn(InteractionHand.MAIN_HAND, hit)
        clicks++
        clickedAt = agent.ticks
    }

    private fun takeOne(agent: Agent, pos: BlockPos) {
        val body = agent.body
        val menu = body.containerMenu
        if (menu == null) {
            done(agent, pos)
            return
        }
        if ((agent.ticks - openedAt) % TAKE_TICKS != 0L) return
        val player = body.player
        val slot = menu.slots.indexOfFirst { !it.isPlayerInventory && wanted.wants(player, it.item) }
        if (slot < 0) {
            body.closeContainer()
            done(agent, pos)
            return
        }
        body.takeFromContainer(slot)
    }

    private fun done(agent: Agent, pos: BlockPos) {
        chests.opened(pos)
        chest = null
        agent.listener.looted(pos)
    }

    private fun giveUp(agent: Agent, pos: BlockPos, reason: String) {
        chests.giveUp(pos)
        chest = null
        agent.logger.debug("Gave up on chest {}: {}", pos, reason)
    }

    private fun isAimed(sent: ViewRotation, aim: ViewRotation) =
        abs(Mth.wrapDegrees(sent.yRot - aim.yRot)) < AIM_TOLERANCE && abs(sent.xRot - aim.xRot) < AIM_TOLERANCE

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2

        /**
         * From the eyes to the chest center.
         */
        const val REACH = 4.0
        const val WALK_RADIUS = 2.0
        const val HALF = 0.5
        const val AIM_TOLERANCE = 15f

        /**
         * A menu opening this long after a click came from it; without one, the chest is clicked again.
         */
        const val SCREEN_TICKS = 10
        const val MAX_CLICKS = 4
        const val TAKE_TICKS = 5
        const val CHEST_TICKS = 12 * 20
    }
}
