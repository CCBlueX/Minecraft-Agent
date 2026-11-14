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

import net.ccbluex.agent.InternalAgentApi
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.walk.BlockActions
import net.ccbluex.agent.navigation.walk.Follower
import net.ccbluex.agent.navigation.walk.PlacedBlock
import net.ccbluex.agent.navigation.walk.Planning
import net.ccbluex.agent.navigation.walk.Setbacks
import net.ccbluex.agent.tick.tickUntil
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
import java.util.function.Consumer

/**
 * Walks the player of [body] to a goal given every tick: straight on where nothing is in the way, else along a
 * searched path, opening doors and, when allowed, breaking and placing blocks. A ridden vehicle is driven there
 * instead, and the player flies or glides where it can.
 *
 * With [Options.fastBridges], searches count level bridges as quick to cross, and while
 * [Options.placesBridgeFloors] another feature such as Scaffold places their floor and the walk only runs
 * across them; [bridgeAhead] tells when one comes up.
 *
 * The host calls [navigate], [tick], [press] or [halt] once every tick, before the keys of the tick are used, and
 * feeds world changes in through [blockChanged], [blockConfirmed] and [levelChanged]. [moveTo] and [follow] hand it a
 * walk to hold, which every [tick] goes on with until it ends; [walkTo] waits for one in suspend code. Every tick's
 * keys reach [Body.press] once, timed by [Options.minPressTicks] and [Options.minReleaseTicks] on a plain walk.
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
    private val course = Course(context, lookAhead, actions, follower, heading, planning)

    private var lastInput: Input = Input.EMPTY
    private var walk: Walk? = null
    private val keystrokes = Keystrokes()
    private val setbacks = Setbacks()
    private var ticks = 0L

    /**
     * Where the player stood when the last tick began, the move a setback refuses starting there.
     */
    private var tickStart: Vec3? = null

    /**
     * Changes the keys of every tick last, as a rescue that keeps the player from walking off an edge does.
     */
    @InternalAgentApi
    public var guard: ((Input) -> Input)? = null

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
            val goal = course.lastGoal ?: return null
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
        get() = course.isStranded

    /**
     * Ticks in a row the walk has been [isStranded], 0 after a tick without a goal.
     */
    public var strandedTicks: Int = 0
        private set

    /**
     * Whether the navigator holds a walk given by [moveTo] or [follow].
     */
    public val isInProgress: Boolean
        get() = walk != null

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
        val plainWalk = course.move(keys, goal, radius)
        strandedTicks = if (isStranded) strandedTicks + 1 else 0
        val pressed = tickBody.takePressed()
        val input = pressed ?: keys.toInput(sprint = false)
        val sprint = heading.sprint && input.isMoving
        finishTick(
            if (sprint) input.withKeys(input.jump(), input.shift(), sprint = true) else input,
            exact = !plainWalk || pressed != null && tickBody.pressedExact,
        )
        return plainWalk
    }

    /**
     * Walks to [goal] on every [tick] until within [radius]; [ended] hears how it ended. Ends the walk held before
     * with [Ending.STOPPED].
     */
    @JvmOverloads
    public fun moveTo(goal: Vec3, radius: Double = DestinationSource.DEFAULT_RADIUS, ended: Consumer<Ending>? = null) {
        hold(Walk(Walk.Fixed(goal, radius), endsOnArrival = true, ended))
    }

    /**
     * Walks to the goal [source] gives every [tick], standing still while it gives none; ends only halted.
     */
    @JvmOverloads
    public fun follow(source: DestinationSource, ended: Consumer<Ending>? = null) {
        hold(Walk(source, endsOnArrival = false, ended))
    }

    /**
     * Goes on with the held walk for one tick, or halts without one; true for a plain walk, as [navigate]. A tick
     * something else moves the player ([navigate] to another goal, [press], [halt]) pauses the walk.
     */
    public fun tick(): Boolean {
        val walk = walk ?: return false.also { halt() }
        val goal = walk.source.goal()
        if (this.walk !== walk) {
            // The source ended or replaced its own walk
            return false
        }
        if (goal == null) {
            halt()
            return false
        }
        val plainWalk = navigate(goal, walk.source.radius())
        walk.tick(course.arrived, isStranded)?.let { ending ->
            this.walk = null
            walk.end(ending)
        }
        return plainWalk
    }

    /**
     * Presses [keys] something else picked this tick, such as an ability; the held walk pauses meanwhile.
     */
    public fun press(keys: Input, exact: Boolean) {
        idle()
        finishTick(keys, exact)
    }

    internal fun startWalk(goal: Vec3, radius: Double, ended: Consumer<Ending>): Walk =
        Walk(Walk.Fixed(goal, radius), endsOnArrival = true, ended).also(::hold)

    /**
     * Lets go of [walk] without a report, if it is still the one held.
     */
    internal fun drop(walk: Walk) {
        if (this.walk === walk) this.walk = null
    }

    private fun hold(walk: Walk?) {
        val old = this.walk
        this.walk = walk
        old?.end(Ending.STOPPED)
    }

    /**
     * The keys the last tick pressed, none after [halt].
     */
    public val input: Input
        get() = lastInput

    /**
     * For ticks without a goal; presses nothing, the keys are the host's or the user's again.
     */
    public fun halt() {
        idle()
        releaseKeys()
    }

    private fun idle() {
        startTick()
        clearWalk()
    }

    private fun clearWalk() {
        strandedTicks = 0
        course.halt()
        tickBody.takePressed()
        body.safeWalk = false
    }

    private fun releaseKeys() {
        keystrokes.reset()
        lastInput = Input.EMPTY
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
     * [Body.continueDestroy], checked every tick as [useItemOn] is; once refused, as when a block comes between
     * mid-dig, the dig is let go of ([Body.stopDestroy]) and has to start again.
     *
     * @agent claude-opus-5-5
     */
    public fun continueDestroy(pos: BlockPos, face: Direction): Boolean = tickBody.continueDestroy(pos, face)

    /**
     * Whether a click on [face] of [pos] would go out now, as [useItemOn] checks it; callers that turned to the face
     * wait for it, since the server gets the new rotation a tick or more later.
     *
     * @agent claude-opus-5-5
     */
    public fun canClick(pos: BlockPos, face: Direction): Boolean = tickBody.canClick(pos, face)

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
     * For when the owner stops; ends the held walk with [Ending.STOPPED].
     */
    public fun stop() {
        hold(null)
        halt()
        planning.forgetStuckSpots()
        lookAhead.reset()
    }

    /**
     * A block changed, so routes through it are searched again.
     */
    public fun blockChanged(pos: BlockPos) {
        planning.blockChanged(pos)
        course.blockChanged(pos)
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
        // Like reset, but without a tick: the player of the level left behind may be gone already
        hold(null)
        clearWalk()
        releaseKeys()
        planning.forgetStuckSpots()
        lookAhead.reset()
        actions.levelChanged()
        planning.levelChanged()
        course.levelChanged()
    }

    /**
     * The server set the player back to another position. Setbacks repeated close together mark every feet block
     * the player was refused at there as stuck spots, so the walk is searched again around them.
     */
    public fun positionCorrected() {
        val refused = tickStart ?: player.position()
        context.logger.debug("Set back from {}", refused)
        follower.positionCorrected()
        setbacks.refused(refused, ticks).forEach(planning::stuckAt)
    }

    /**
     * What happens at the start of every tick: the camera turn, block actions and the rotation for this tick.
     */
    private fun startTick() {
        ticks++
        tickStart = player.position()
        if (!viewTurned) {
            lookAhead.tick()
        }
        viewTurned = false
        actions.tick()
        actions.aim()
        heading.lookAlongPath()
    }

    /**
     * The keys of the tick through key timing and the [guard] to the body, once.
     */
    private fun finishTick(input: Input, exact: Boolean) {
        val options = context.options()
        val timed = keystrokes.press(input, exact, options.minPressTicks, options.minReleaseTicks)
        body.safeWalk = follower.path != null && follower.safeWalk
        val guarded = guard?.invoke(timed) ?: timed
        lastInput = guarded
        body.press(guarded, exact || guarded != timed)
    }

    public companion object {
        /**
         * Stranded ticks in a row that end a walk to a fixed goal as [Ending.UNREACHABLE].
         */
        public const val UNREACHABLE_TICKS: Int = 40

        private const val LOGGER = "Agent/Navigator"

        /**
         * Turns of the path sharper than this within [TURN_LOOK] blocks, and the goal this close, slow down.
         */
        private const val SHARP_TURN = 45f
        private const val TURN_LOOK = 4.0
        private const val APPROACH_DISTANCE = 4.0
    }

}

/**
 * Walks to [goal] and returns how it ended, waiting on the coroutine's [net.ccbluex.agent.tick.Ticker]; the host
 * goes on calling [Navigator.tick]. Cancelling the caller or a failing requirement drops the walk without a report.
 *
 * @agent claude-opus-5-5
 */
public suspend fun Navigator.walkTo(goal: Vec3, radius: Double = DestinationSource.DEFAULT_RADIUS): Ending {
    var ending: Ending? = null
    val walk = startWalk(goal, radius) { ending = it }
    try {
        tickUntil { ending != null }
        return checkNotNull(ending)
    } finally {
        if (ending == null) drop(walk)
    }
}
