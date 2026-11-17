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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.pathfinder.walk.Route
import net.ccbluex.agent.pathfinder.walk.Step
import kotlin.math.abs
import kotlin.math.min

/**
 * How close to the feet point of a step counts as there.
 */
internal const val REACH_HORIZONTAL = 0.35
private const val REACH_VERTICAL = 0.6

/**
 * Whether the steps of a path are done: their blocks changed and the player where they lead.
 *
 * From LiquidBounce: [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L606-L633), [Follower.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/walk/Follower.kt#L526-L542)
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
internal class StepCheck(private val context: NavigationContext, private val actions: BlockActions) {

    private val player get() = context.body.player
    private val level get() = context.body.level

    fun isReached(step: Step, next: Step?): Boolean {
        val built = step.places.all(::isPlaced) && step.places.none(actions::isUnconfirmed)
        if (!step.breaks.all(::isCleared) || !built || !step.opens.all(::isOpen)) {
            return false
        }

        val position = player.position()
        val block = BlockPos.containing(position)
        // In the air above its target, the landing of a jump still needs steering.
        if (step.jump != null) {
            val support = player.mainSupportingBlockPos
            return player.onClimbable() && block == step.to ||
                player.onGround() && (block == step.to || support == step.to || support == step.to.below())
        }

        val close = position.horizontalDistanceTo(step.feet) < REACH_HORIZONTAL
        if (close && abs(position.y - step.feet.y) < REACH_VERTICAL) {
            return true
        }

        // Fast or in the air, the feet point is rarely hit; a plain or built bridge step passed along its way is done.
        if ((step.isPlain || step.isBridge) && hasPassed(step, position)) {
            return true
        }

        // On straight runs, entering the block is enough; turns wait for the center so corners are not cut.
        return player.isStable && next != null && block == step.to && continuesStraight(step, next)
    }

    /**
     * The step after the plain one from [index] whose block the player stands in, as a shortcut walks by the blocks
     * of plain steps; [index] when there is none.
     */
    fun skipAhead(path: Route, index: Int): Int {
        if (!player.onGround()) {
            return index
        }

        val block = BlockPos.containing(player.position())
        for (i in index until min(path.steps.size, index + SHORTCUT_STEPS)) {
            val step = path.steps[i]
            if (!step.isPlain) {
                return index
            }
            if (step.to == block) {
                return i + 1
            }
        }
        return index
    }

    fun isCleared(pos: BlockPos) = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty

    fun isPlaced(pos: BlockPos) = !level.getBlockState(pos).canBeReplaced()

    fun isOpen(pos: BlockPos): Boolean = level.getBlockState(pos).isOpenOrGone
}
