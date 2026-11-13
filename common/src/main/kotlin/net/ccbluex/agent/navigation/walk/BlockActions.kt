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

package net.ccbluex.agent.navigation.walk

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.NavigationContext
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult

/**
 * Mines, places and opens the blocks a walking path changes, one at a time: aims in [aim], acts in [tick] once the
 * server has the aim. A placed block counts as unconfirmed until the server sends it back ([answer]) or
 * [verdictTicks] pass, as a server that refuses it only says so with an update to air.
 *
 * From LiquidBounce: [BlockActions.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/BlockActions.kt#L76-L298)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class BlockActions(private val context: NavigationContext) {

    private val targets = BlockTargets(context)
    private val body get() = context.body
    private val level get() = context.body.level

    private var action: Action? = null

    /**
     * The aim of the current action, null while there is nothing to aim at.
     */
    var rotation: ViewRotation? = null
        private set

    private var useCooldown = 0
    private var ticks = 0

    /**
     * The block being dug, so the dig goes on rather than starting again.
     */
    private var digging: BlockPos? = null

    private val unconfirmed = HashMap<BlockPos, PlacedBlock>()

    /**
     * Doors, trapdoors and fence gates used, by the tick, until the server sends their new state.
     */
    private val unconfirmedOpens = HashMap<BlockPos, Int>()

    /**
     * The block placed last.
     */
    var placement: PlacedBlock? = null
        private set

    /**
     * Placements the server refused since it last kept one.
     */
    var rejectedInRow = 0
        private set

    val isActive: Boolean
        get() = action != null

    fun isUnconfirmed(pos: BlockPos) = pos in unconfirmed

    /**
     * Whether the block at [pos] was used to open it and the server has not sent its new state yet.
     */
    fun isOpenUnconfirmed(pos: BlockPos) = pos in unconfirmedOpens

    fun forgetRejections() {
        rejectedInRow = 0
    }

    fun mine(pos: BlockPos) {
        if ((action as? Action.Mine)?.pos != pos) {
            idle()
            action = Action.Mine(pos)
        }
    }

    /**
     * Places a block at [pos] once [ready]; until then only aims.
     */
    fun place(pos: BlockPos, ready: Boolean) {
        val placing = action as? Action.Place
        if (placing?.pos == pos) {
            placing.ready = ready
            return
        }

        // From one block to the next, the aim and the slot stay.
        if (placing == null) {
            idle()
        }
        action = Action.Place(pos, ready)
    }

    /**
     * Uses the door, trapdoor or fence gate at [pos].
     */
    fun open(pos: BlockPos) {
        if (action !is Action.Open) {
            idle()
        }
        action = Action.Open(pos)
    }

    fun idle() {
        val stopped = action ?: return
        action = null
        rotation = null
        if (stopped is Action.Mine && digging != null) {
            digging = null
            body.stopDestroy()
        }
        body.releaseHotbarSlot()
    }

    /**
     * Aims at the block of the current action; once per tick, before the walk decides on the next one.
     */
    fun aim() {
        rotation = null
        val action = action ?: return
        val aim = when (action) {
            is Action.Place -> {
                action.plan = targets.placeableSlot()?.let { targets.planPlacement(action.pos, it) }
                action.plan?.rotation
            }

            else -> targets.aimAt(action.pos, level.getBlockState(action.pos))
        } ?: return

        rotation = aim
        body.look(aim, LookPurpose.AIM)
    }

    /**
     * Acts with the rotation the server has; once per tick, before [aim].
     */
    fun tick() {
        ticks++
        if (useCooldown > 0) {
            useCooldown--
        }
        expireVerdicts()

        when (val action = action) {
            is Action.Mine -> breakTick(action.pos)
            is Action.Open -> openTick(action.pos)
            is Action.Place -> action.plan?.takeIf { action.ready }?.let(::placeTick)
            null -> Unit
        }
    }

    private fun breakTick(pos: BlockPos) {
        val state = level.getBlockState(pos)
        if (state.getCollisionShape(level, pos).isEmpty) {
            return
        }

        targets.bestTool(state)?.let(body::selectHotbarSlot)

        // The action goes out before this tick's rotation, so it has to match the rotation the server has.
        val reach = body.player.blockInteractionRange() + 1.0
        val hit = targets.raycast(body.sentRotation, reach)
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos != pos) {
            return
        }
        if (digging == pos) {
            body.continueDestroy(pos, hit.direction)
        } else {
            digging = pos
            body.startDestroy(pos, hit.direction)
        }
    }

    /**
     * @agent claude-opus-5-5
     */
    private fun openTick(pos: BlockPos) {
        val state = level.getBlockState(pos)
        if (useCooldown > 0 || state.isOpenOrGone) {
            return
        }

        val hit = targets.raycast(body.sentRotation, body.player.blockInteractionRange())
        if (hit.type == HitResult.Type.BLOCK && hit.blockPos == pos) {
            if (body.useItemOn(InteractionHand.MAIN_HAND, hit)) {
                // The client shows it open at once; the walk goes through once the server says so too.
                unconfirmedOpens[pos] = ticks
            }
            // The server gets time to answer before another use, which would close the block again.
            useCooldown = USE_COOLDOWN_TICKS
        }
    }

    private fun placeTick(plan: BlockTargets.Plan) {
        val hit = targets.verifyClick(plan, body.sentRotation) ?: return
        if (plan.slot >= 0) {
            body.selectHotbarSlot(plan.slot)
        }
        if (body.useItemOn(plan.hand, hit)) {
            val placed = PlacedBlock(hit.blockPos.relative(hit.direction), ticks)
            unconfirmed[placed.pos] = placed
            placement = placed
            context.listener.blockPlaced(placed)
        }
    }

    /**
     * The server's word on the block at [pos].
     *
     * @agent claude-opus-5-5
     */
    fun answer(pos: BlockPos, state: BlockState) {
        unconfirmed.remove(pos)?.let { judge(it, kept = !state.canBeReplaced()) }
        unconfirmedOpens.remove(pos)
    }

    /**
     * Without an answer, a placed block counts as kept if it is still there, and a used door as the client shows it.
     *
     * @agent claude-opus-5-5
     */
    private fun expireVerdicts() {
        if (unconfirmed.isEmpty() && unconfirmedOpens.isEmpty()) {
            return
        }

        val timeout = verdictTicks()
        unconfirmedOpens.values.removeIf { ticks - it > timeout }
        val iterator = unconfirmed.values.iterator()
        while (iterator.hasNext()) {
            val placed = iterator.next()
            if (ticks - placed.tick > timeout) {
                iterator.remove()
                judge(placed, kept = !level.getBlockState(placed.pos).canBeReplaced())
            }
        }
    }

    private fun judge(placed: PlacedBlock, kept: Boolean) {
        placed.verdict = if (kept) PlacedBlock.Verdict.KEPT else PlacedBlock.Verdict.REJECTED
        rejectedInRow = if (kept) 0 else rejectedInRow + 1
        if (kept) {
            context.listener.blockKept(placed)
        } else {
            context.logger.warn("The server took back the block placed at {}", placed.pos)
            context.listener.blockRejected(placed)
        }
    }

    /**
     * Twice the round trip and some ticks for the server to get to it.
     */
    private fun verdictTicks() = VERDICT_TICKS + body.latencyMillis / MILLIS_PER_TICK * 2

    fun levelChanged() {
        action = null
        rotation = null
        digging = null
        unconfirmed.clear()
        unconfirmedOpens.clear()
        placement = null
        rejectedInRow = 0
    }

    private sealed class Action(val pos: BlockPos) {
        class Mine(pos: BlockPos) : Action(pos)
        class Open(pos: BlockPos) : Action(pos)
        class Place(pos: BlockPos, var ready: Boolean) : Action(pos) {
            var plan: BlockTargets.Plan? = null
        }
    }

    private companion object {
        const val USE_COOLDOWN_TICKS = 10
        const val VERDICT_TICKS = 10
        const val MILLIS_PER_TICK = 50
    }

}

/**
 * A block placed at [pos] in [tick], and whether the server kept it.
 *
 * From LiquidBounce: [BlockActions.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/BlockActions.kt#L307-L311)
 *
 * @agent claude-fable-5-2
 */
public class PlacedBlock(public val pos: BlockPos, public val tick: Int) {
    public var verdict: Verdict = Verdict.PENDING
        internal set

    public enum class Verdict(public val tag: String) {
        PENDING("pending"),
        KEPT("kept"),
        REJECTED("rejected"),
    }
}
