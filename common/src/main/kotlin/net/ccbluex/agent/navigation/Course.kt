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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.navigation.walk.BlockActions
import net.ccbluex.agent.navigation.walk.Follower
import net.ccbluex.agent.navigation.walk.Hops
import net.ccbluex.agent.navigation.walk.Planning
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.isStraightWalkable

/**
 * The way one tick of a [Navigator] goes towards its goal: arrived and standing, riding, flying or gliding, straight
 * on, along the searched path, or without one yet.
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L253-L328), [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L389-L443)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
internal class Course(
    private val context: NavigationContext,
    private val lookAhead: LookAhead,
    private val actions: BlockActions,
    private val follower: Follower,
    private val heading: Heading,
    private val planning: Planning,
) {

    private val body = context.body
    private val route = context.route
    private val player get() = body.player
    private val travel = Travel(context, ::stopWalking, ::resetWalk)
    private val hops = Hops(context, follower.stride)

    /**
     * Whether the goal is reached by walking straight on, as of the last tick on the ground.
     */
    private var straight = false
    private var straightGoal: Vec3? = null
    private var failed = false

    /**
     * Whether the last tick ended within the radius of the goal.
     */
    var arrived = false
        private set

    /**
     * The goal of the last tick navigated, null once halted.
     */
    var lastGoal: Vec3? = null
        private set

    val isStranded: Boolean
        get() = !straight && follower.path == null && (planning.failed || planning.isSettled)

    /**
     * One tick towards [goal], within [radius]; true for a plain walk.
     */
    fun move(keys: MovementKeys, goal: Vec3, radius: Double): Boolean {
        planning.tick(goal)
        lastGoal = goal
        val position = player.position()

        if (player.rootVehicle.position().distanceToSqr(goal) <= Mth.square(radius)) {
            stop(keys)
            heading.forgetLook()
            route.done()
            if (!arrived) {
                arrived = true
                context.listener.arrived(goal)
            }
            return false
        }
        arrived = false

        val options = context.options()
        if (player.onGround()) {
            // Goals beyond the path range are walked to in stages; a straight walk there is never checked in full.
            straight = position.distanceToSqr(goal) <= Mth.square(options.maxLength) &&
                isStraightWalkable(Terrain.live(body.level), position, goal, radius, context.safeFall())
        }

        // A ridden vehicle follows the view, which the ride turns itself.
        if (travel.ride(goal, radius)) {
            return false
        }

        if (flies(goal, radius)) {
            lookAhead.aim(heading.target)
            return false
        }

        if (straight) {
            walkStraight(keys, goal)
            lookAhead.aim(goal)
            return true
        }

        straightGoal = null
        planning.collect(position)

        val path = follower.path
        val plainWalk = if (path != null && !follower.isFinished) {
            followPath(keys).also {
                // Along a bridge the view keeps to its far end rather than turning with the steps.
                val ahead = follower.pointAhead(player.position(), lookAhead.distance.toDouble())
                lookAhead.aim(follower.bridgeTarget ?: ahead)
            }
        } else {
            fallback(keys, goal, radius).also { lookAhead.aim(heading.target) }
        }

        planning.planIfNeeded(goal, radius, position)
        reportFailure(goal)
        return plainWalk
    }

    /**
     * A tick without a goal.
     */
    fun halt() {
        lastGoal = null
        arrived = false
        failed = false
        travel.halt()
        stopWalking()
    }

    fun blockChanged(pos: BlockPos) {
        travel.blockChanged(pos)
    }

    fun levelChanged() {
        travel.levelChanged()
    }

    private fun reportFailure(goal: Vec3) {
        if (planning.failed && !failed) {
            context.logger.debug("No way found to {}", goal)
            context.listener.failed(goal)
        }
        failed = planning.failed
    }

    private fun flies(goal: Vec3, radius: Double): Boolean {
        if (!travel.fly(goal, radius, straight)) {
            return false
        }
        heading.target = travel.target
        heading.sprint = travel.sprint
        return true
    }

    /**
     * @agent claude-opus-5-5
     */
    private fun walkStraight(keys: MovementKeys, goal: Vec3) {
        planning.drop()
        follower.clear()
        actions.idle()

        straightGoal = goal
        heading.walkTowards(keys, goal, sprint = true)
        keys.jump = keys.jump || hops.starts(player.position(), goal)

        route.blocks(emptyList(), emptyList())
        route.following(listOf(player.position(), goal), straightProgress)
    }

    private fun followPath(keys: MovementKeys): Boolean {
        val path = follower.path ?: return false
        val state = follower.tick(keys, body.inputYRot)
        if (state == FollowState.STUCK) {
            follower.stuckAt?.let {
                planning.stuckAt(it)
                context.logger.debug("Stuck before {}", it)
                context.listener.stuck(it)
            }
        }

        heading.target = follower.target
        heading.sprint = follower.sprint && (!keys.sneak || follower.dive)

        route.blocks(follower.remainingBreaks, follower.remainingPlaces)
        route.following(path.points, follower)
        return state == FollowState.WALKING && follower.isPlainWalk && !actions.isActive
    }

    /**
     * Without a path: the rest of the way within the feet block, else on over plain ground while the first path is
     * searched, else standing still.
     */
    private fun fallback(keys: MovementKeys, goal: Vec3, radius: Double): Boolean {
        actions.idle()

        val searching = planning.isSearching
        val target = context.finalApproach(goal, radius)
            ?: goal.takeIf { searching && !planning.failed && context.hasHeadway(it) }
        if (target != null) {
            heading.walkTowards(keys, target, sprint = target === goal)
        } else {
            stop(keys)
        }

        when {
            planning.failed -> route.invalid()
            searching -> route.searching(player.position(), goal, radius, planning.preview)
            else -> route.done()
        }
        return false
    }

    private fun stop(keys: MovementKeys) {
        actions.idle()
        heading.stop(keys)
    }

    private fun stopWalking() {
        resetWalk()
        route.clear()
    }

    private fun resetWalk() {
        planning.reset()
        follower.clear()
        actions.idle()
        straight = false
        straightGoal = null
        heading.clear()
    }

    private val straightProgress = TargetProgress(body, { straightGoal != null }) { straightGoal }

}
