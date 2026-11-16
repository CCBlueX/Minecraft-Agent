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

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.walk.BlockActions
import net.ccbluex.agent.navigation.walk.Follower
import net.ccbluex.agent.navigation.walk.PlacedBlock
import net.ccbluex.agent.navigation.walk.Planning
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.pathfinder.walk.isStraightWalkable
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory

/**
 * Walks the player of [body] to a goal given every tick: straight on where nothing is in the way, else along a
 * searched path, opening doors and, when allowed, breaking and placing blocks. A ridden vehicle is driven there
 * instead, and the player flies or glides where it can.
 *
 * With [Options.fastBridges], searches count level bridges as quick to cross, and while
 * [Options.placesBridgeFloors] another feature such as Scaffold places their floor and the walk only runs
 * across them; [bridgeAhead] tells when one comes up.
 *
 * The host calls [navigate] or [halt] once every tick, before the keys of the tick are used, and feeds world changes
 * in through [blockChanged], [blockConfirmed] and [levelChanged].
 *
 * From LiquidBounce: [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L389-L443), [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L253-L328), [Navigator.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/Navigator.kt#L120-L166)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
public class Navigator @JvmOverloads constructor(
    private val body: Body,
    options: Options = Options(),
    listener: Listener = Listener.NONE,
) {

    /**
     * Read every tick; a new copy takes effect at once.
     */
    @Volatile
    public var options: Options = options

    private val tickBody = TickBody(body) { this.options }
    private val context =
        NavigationContext(tickBody, RouteReport(listener), { this.options }, listener, LoggerFactory.getLogger(LOGGER))
    private val player get() = body.player

    private val lookAhead = LookAhead(context)

    /**
     * Whether the host turned the camera since the last tick, see [turnView].
     */
    private var viewTurned = false
    private val actions = BlockActions(context)
    private val follower = Follower(context, actions)
    private val heading = Heading(context, follower, actions, lookAhead)
    private val planning = Planning(context, follower)
    private val travel = Travel(context, ::stopWalking, ::resetWalk)

    /**
     * Whether the goal is reached by walking straight on, as of the last tick on the ground.
     */
    private var straight = false
    private var straightGoal: Vec3? = null
    private var lastInput: Input = Input.EMPTY
    private var arrived = false
    private var failed = false

    /**
     * The goal of the last tick navigated, null once halted.
     */
    private var lastGoal: Vec3? = null

    public val isActing: Boolean
        get() = actions.isActive

    /**
     * Whether the walk mines or places blocks in this step or the next, such as while bridging.
     */
    public val isBuilding: Boolean
        get() = actions.isActive || follower.path != null && follower.building

    /**
     * The level bridge steps in a row from this step of the path or the next, 0 without.
     */
    public val bridgeAhead: Int
        get() = if (follower.path != null) follower.bridgeAhead else 0

    /**
     * The blocks the path followed still places, empty without one.
     */
    public val placesAhead: List<BlockPos>
        get() = if (follower.path != null) follower.remainingPlaces else emptyList()

    /**
     * The yaw a bridge is crossed along, null while not crossing one.
     */
    public val bridgeYRot: Float?
        get() = follower.bridgeYRot

    /**
     * Times following the path overshot it, so the aim lay behind the way the player moved.
     */
    public val overshoots: Int
        get() = follower.overshoots

    /**
     * Why to slow down: a sharp `turn` of the path just ahead, or the goal close (`approach`); null to go on.
     */
    public val slowdown: String?
        get() {
            val goal = lastGoal ?: return null
            return when {
                player.position().closerThan(goal, APPROACH_DISTANCE) -> "approach"
                follower.path != null && follower.turnAhead(TURN_LOOK) > SHARP_TURN -> "turn"
                else -> null
            }
        }

    /**
     * The block the walk placed last, and whether the server kept it.
     */
    public val placement: PlacedBlock?
        get() = actions.placement

    /**
     * Whether the searches found no way closer to the goal, so the walk stays short of it.
     */
    public val isStranded: Boolean
        get() = !straight && follower.path == null && (planning.failed || planning.isSettled)

    /**
     * Where the walk heads: the aim of a block action, else the way to the next point, else the movement keys.
     */
    public fun movementRotation(): ViewRotation = heading.rotation(lastInput)

    /**
     * Moves one tick towards [goal], within [radius]; true if the movement is a plain walk other features may add
     * to, such as an extra jump.
     */
    public fun navigate(goal: Vec3, radius: Double): Boolean {
        startTick()
        val keys = MovementKeys()
        val plainWalk = move(keys, goal, radius)
        finishTick(keys)
        return plainWalk
    }

    /**
     * The keys the last [navigate] pressed, none after [halt].
     */
    public val input: Input
        get() = lastInput

    /**
     * For ticks without a goal.
     */
    public fun halt() {
        startTick()
        lastGoal = null
        arrived = false
        failed = false
        travel.halt()
        stopWalking()
        tickBody.takePressed()
        lastInput = Input.EMPTY
        body.safeWalk = false
    }

    /**
     * [Body.useItemOn], sent only when the rotation the server has hits that face of that block first, as the game's
     * pick does, unless [Options.canClickThroughBlocks]; false when refused, so the caller moves or turns first.
     *
     * @agent claude-opus-5-5
     */
    public fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean = tickBody.useItemOn(hand, hit)

    /**
     * [Body.startDestroy], refused as [useItemOn] is.
     *
     * @agent claude-opus-5-5
     */
    public fun startDestroy(pos: BlockPos, face: Direction): Boolean = tickBody.startDestroy(pos, face)

    /**
     * [Body.continueDestroy], refused as [useItemOn] is.
     *
     * @agent claude-opus-5-5
     */
    public fun continueDestroy(pos: BlockPos, face: Direction): Boolean = tickBody.continueDestroy(pos, face)

    /**
     * Whether a hit on [entity] may go out: no solid block stands between the eyes and it where the rotation the
     * server has meets it, unless [Options.canClickThroughBlocks].
     *
     * @agent claude-opus-5-5
     */
    public fun canHit(entity: Entity): Boolean = options.canClickThroughBlocks || body.sees(entity)

    /**
     * Turns the camera towards [point] after this tick, for ticks something else moves the player.
     */
    public fun lookAt(point: Vec3) {
        lookAhead.aim(point)
    }

    /**
     * Turns the camera the way navigation goes, once the player moved this tick and the server got its rotation. A
     * host that does not call it gets the turn at the start of the next [navigate] or [halt].
     */
    public fun turnView() {
        lookAhead.tick()
        viewTurned = true
    }

    /**
     * For when the owner stops.
     */
    public fun stop() {
        halt()
        planning.forgetStuckSpots()
        lookAhead.reset()
    }

    /**
     * A block changed, so routes through it are searched again.
     */
    public fun blockChanged(pos: BlockPos) {
        planning.blockChanged(pos)
        travel.blockChanged(pos)
    }

    /**
     * The server sent the block at [pos], which judges a block placed there.
     */
    public fun blockConfirmed(pos: BlockPos, state: BlockState) {
        actions.answer(pos, state)
    }

    /**
     * The player went to another level or reconnected.
     */
    public fun levelChanged() {
        stop()
        actions.levelChanged()
        planning.levelChanged()
        travel.levelChanged()
    }

    /**
     * The server set the player back to another position.
     */
    public fun positionCorrected() {
        context.logger.debug("Set back to {}", player.position())
        follower.positionCorrected()
    }

    /**
     * What happens at the start of every tick: the camera turn, block actions and the rotation for this tick.
     */
    private fun startTick() {
        if (!viewTurned) {
            lookAhead.tick()
        }
        viewTurned = false
        actions.tick()
        actions.aim()
        heading.lookAlongPath()
    }

    private fun finishTick(keys: MovementKeys) {
        val input = tickBody.takePressed() ?: keys.toInput(sprint = false)
        val sprint = heading.sprint && input.isMoving
        lastInput = if (sprint) input.withKeys(input.jump(), input.shift(), sprint = true) else input
        body.press(lastInput)
        body.safeWalk = follower.path != null && follower.safeWalk
    }

    private fun move(keys: MovementKeys, goal: Vec3, radius: Double): Boolean {
        planning.tick(goal)
        lastGoal = goal
        val position = player.position()

        if (player.rootVehicle.position().distanceToSqr(goal) <= Mth.square(radius)) {
            stop(keys)
            heading.forgetLook()
            context.route.done()
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

    private fun walkStraight(keys: MovementKeys, goal: Vec3) {
        planning.drop()
        follower.clear()
        actions.idle()

        straightGoal = goal
        heading.walkTowards(keys, goal, sprint = true)

        context.route.blocks(emptyList(), emptyList())
        context.route.following(listOf(player.position(), goal), straightProgress)
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

        context.route.blocks(follower.remainingBreaks, follower.remainingPlaces)
        context.route.following(path.points, follower)
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
            planning.failed -> context.route.invalid()
            searching -> context.route.searching(player.position(), goal, radius, planning.preview)
            else -> context.route.done()
        }
        return false
    }

    private fun stop(keys: MovementKeys) {
        actions.idle()
        heading.stop(keys)
    }

    private fun stopWalking() {
        resetWalk()
        context.route.clear()
    }

    private fun resetWalk() {
        planning.reset()
        follower.clear()
        actions.idle()
        straight = false
        straightGoal = null
        heading.clear()
    }

    private val straightProgress = TargetProgress(body.player, { straightGoal != null }) { straightGoal }

    private companion object {
        const val LOGGER = "Agent/Navigator"

        /**
         * Turns of the path sharper than this within [TURN_LOOK] blocks, and the goal this close, slow down.
         */
        const val SHARP_TURN = 45f
        const val TURN_LOOK = 4.0
        const val APPROACH_DISTANCE = 4.0
    }

}
