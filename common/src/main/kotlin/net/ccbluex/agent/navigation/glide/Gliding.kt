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

package net.ccbluex.agent.navigation.glide

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.getDirectionalInputTowards
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.yRot
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.NavigationContext
import net.ccbluex.agent.navigation.TargetProgress
import net.ccbluex.agent.navigation.safeFall
import net.ccbluex.agent.pathfinder.glide.GlideAbilities
import net.ccbluex.agent.pathfinder.glide.ColumnHeights
import net.ccbluex.agent.pathfinder.glide.Cruise
import net.ccbluex.agent.pathfinder.glide.Landing
import net.ccbluex.agent.pathfinder.glide.Passage
import net.ccbluex.agent.pathfinder.glide.Route
import net.ccbluex.agent.pathfinder.glide.RouteSearch
import net.ccbluex.agent.pathfinder.glide.Rockets
import net.ccbluex.agent.pathfinder.glide.Steering
import net.ccbluex.agent.pathfinder.route.BackgroundSearch
import net.ccbluex.agent.pathfinder.route.Outcome
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.Items
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

/**
 * Glides to goals for a walking navigator while an elytra is worn: plans a glide to goals far enough away and below,
 * or within reach of rockets, and flies it from take-off to touch-down. Farther goals are cruised to with rockets. In
 * the air, falls and glides are taken over and steered through open space, see [Passage].
 *
 * From LiquidBounce: [Gliding.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Gliding.kt#L57-L293)
 *
 * @agent claude-opus-5-5
 */
internal class Gliding(private val context: NavigationContext) {

    private val search = BackgroundSearch<Route>({ context.body.level })
    private val airways = Airways(context)
    private val contacts = Contacts(context)
    private var glide: Glide? = null
    private var ticks = 0
    private var retryAt = 0
    private var shouldFireRocket = false
    private var holdsRocketSlot = false

    val isActive: Boolean
        get() = glide != null

    var sprint = false
        private set

    private val player
        get() = context.body.player

    private val level
        get() = context.body.level

    /**
     * Glides one tick towards [goal]; false to walk instead. Glides from the ground are planned there unless
     * [direct]; in the air, a fall or a glide is taken over and follows the goal wherever it moves.
     */
    fun navigate(goal: Vec3, radius: Double, abilities: GlideAbilities?, direct: Boolean, frameYRot: Float): Boolean {
        ticks++
        releaseRocketSlot()
        contacts.tick()
        val glide = retarget(goal, abilities) ?: start(goal, radius, abilities, direct) ?: return false
        if (!fly(glide, frameYRot)) {
            return false
        }
        (glide.steering as? Passage)?.let { passage ->
            passage.landsAtTarget = hasGround(goal)
            airways.tick(passage, goal, radius)
        }
        steer(glide)
        useRocket()
        return true
    }

    /**
     * Stops planning and steering; a glide in the air goes on as the game moves it.
     */
    fun halt() {
        search.cancel()
        airways.reset()
        glide = null
        shouldFireRocket = false
        sprint = false
        releaseRocketSlot()
    }

    fun blockChanged(pos: BlockPos) {
        search.blockChanged(pos)
        airways.blockChanged(pos)
    }

    fun levelChanged() {
        search.levelChanged()
        airways.levelChanged()
    }

    /**
     * The glide flown, turned towards [goal] once that moved while in the air.
     */
    private fun retarget(goal: Vec3, abilities: GlideAbilities?): Glide? {
        val glide = glide ?: return null
        if (abilities == null || player.onGround() || glide.goal.distanceToSqr(goal) <= GOAL_MOVED * GOAL_MOVED) {
            return glide
        }
        return passage(goal, abilities, glide.hasOpened)
    }

    private fun start(goal: Vec3, radius: Double, abilities: GlideAbilities?, direct: Boolean): Glide? {
        if (abilities == null) {
            search.cancel()
            return null
        }
        if (!player.onGround() && ticks >= retryAt && (player.isFallFlying || isFallingFar())) {
            return passage(goal, abilities, player.isFallFlying)
        }
        // Landed, but the server has not closed the elytra yet
        if (player.isFallFlying) {
            return null
        }

        glide = cruise(goal, abilities, direct) ?: planned(goal, abilities)
        if (glide == null) {
            planIfUseful(goal, radius, abilities, direct)
        }
        return glide
    }

    /**
     * A rocket cruise to a [goal] too far for one glide or not loaded, under an open sky, if the rockets reach.
     */
    private fun cruise(goal: Vec3, abilities: GlideAbilities, direct: Boolean): Glide? {
        if (direct || ticks < retryAt || !player.onGround() || level.dimensionType().hasCeiling()) {
            return null
        }

        val start = player.position()
        val distance = start.horizontalDistanceTo(goal)
        val loaded = level.hasChunkAt(BlockPos.containing(goal))
        if (distance < MIN_DISTANCE || distance <= MAX_DISTANCE && loaded) {
            return null
        }

        val climb = Cruise.cruiseAltitude(start, goal, liveHeights) - start.y
        if (abilities.rockets < Cruise.rocketsNeeded(distance, climb, abilities.rocketTicks)) {
            return null
        }

        search.cancel()
        val rockets = Rockets(abilities.rockets, abilities.rocketTicks)
        return Glide(goal, Cruise(goal, liveHeights, rockets, level.maxY, ::groundNear))
    }

    private fun planned(goal: Vec3, abilities: GlideAbilities): Glide? = when (val result = search.poll()) {
        Outcome.Searching -> null

        is Outcome.Found -> {
            search.cancel()
            val path = result.value
            // A glide found for where the player stood a moment ago is searched again.
            val standsAtStart = player.onGround() &&
                path.start.distanceToSqr(player.position()) < START_TOLERANCE * START_TOLERANCE
            if (standsAtStart) {
                val rockets = Rockets(path.rockets, abilities.rocketTicks)
                Glide(goal, Landing(goal, rockets, path.approach), path)
            } else {
                null
            }
        }

        Outcome.AlreadyThere, is Outcome.Failed -> {
            search.cancel()
            retryAt = ticks + RETRY_TICKS
            null
        }
    }

    private fun planIfUseful(goal: Vec3, radius: Double, abilities: GlideAbilities, direct: Boolean) {
        if (!search.isIdle || ticks < retryAt || direct || !player.onGround()) {
            return
        }

        val start = player.position()
        val distance = start.horizontalDistanceTo(goal)
        val drop = start.y - goal.y
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || drop < MIN_DROP && abilities.rockets == 0) {
            return
        }

        search.request(BlockPos.containing(start) to goal, RouteSearch.region(start, goal)) { terrain ->
            RouteSearch(terrain, start, goal, radius, abilities)
        }
    }

    /**
     * Jumps and opens the elytra in the air, then leaves the steering to [steer] until touch-down.
     */
    private fun fly(glide: Glide, frameYRot: Float): Boolean {
        glide.ticks++
        if (player.isFallFlying && !player.onGround()) {
            glide.hasOpened = true
        } else if (glide.hasOpened && player.onGround() || !glide.hasOpened && glide.ticks > TAKE_OFF_TICKS) {
            // Touched down, even if the server has not told yet that the elytra closed, or it did not open.
            if (!glide.hasOpened) {
                retryAt = ticks + RETRY_TICKS
            }
            halt()
            return false
        }

        val jump = when {
            player.isFallFlying -> false
            player.onGround() -> true
            else -> pressToOpen(glide)
        }
        glide.jumped = jump
        val direction = if (glide.hasOpened) {
            Input.EMPTY
        } else {
            getDirectionalInputTowards(glide.goal.subtract(player.position()), frameYRot)
        }
        context.body.press(
            Input(direction.forward(), direction.backward(), direction.left(), direction.right(), jump, false, false),
        )
        sprint = !glide.hasOpened

        context.route.blocks(emptyList(), emptyList())
        context.route.following(glide.points(player.position()), progress)
        return true
    }

    /**
     * A fresh jump press in the air opens the elytra. The server may first answer with it closed, and a second press
     * before that answer would close it, so the next press waits.
     */
    private fun pressToOpen(glide: Glide): Boolean {
        if (glide.jumped || glide.ticks - glide.pressedAt < REOPEN_TICKS) {
            return false
        }

        glide.pressedAt = glide.ticks
        return true
    }

    private fun steer(glide: Glide) {
        val rotation = if (player.isFallFlying) {
            val input = glide.steering.steer(player.position(), player.deltaMovement)
            shouldFireRocket = input.shouldFireRocket
            ViewRotation(input.yRot, input.xRot)
        } else {
            ViewRotation(glide.goal.subtract(player.position()).yRot, 0f)
        }
        context.body.look(rotation, LookPurpose.MOVEMENT)
    }

    /**
     * Fires once this tick's rotation is set, which the server expects the rocket to be used with.
     */
    private fun useRocket() {
        if (!shouldFireRocket) {
            return
        }
        shouldFireRocket = false

        if (player.offhandItem.`is`(Items.FIREWORK_ROCKET)) {
            context.body.useItem(InteractionHand.OFF_HAND)
            return
        }
        val inventory = player.inventory
        val slot = (0 until HOTBAR_SIZE).firstOrNull { inventory.getItem(it).`is`(Items.FIREWORK_ROCKET) } ?: return
        context.body.selectHotbarSlot(slot)
        holdsRocketSlot = true
        context.body.useItem(InteractionHand.MAIN_HAND)
    }

    private fun releaseRocketSlot() {
        if (holdsRocketSlot) {
            holdsRocketSlot = false
            context.body.releaseHotbarSlot()
        }
    }

    private val liveHeights = ColumnHeights { x, z ->
        if (level.hasChunkAt(x, z)) level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) else null
    }

    /**
     * The loaded ground closest to [position] to glide down to, if any.
     */
    private fun groundNear(position: Vec3): Vec3? {
        var best: Vec3? = null
        var bestDistance = Double.MAX_VALUE
        val x0 = Mth.floor(position.x)
        val z0 = Mth.floor(position.z)
        for (dx in -GROUND_SCAN..GROUND_SCAN step GROUND_STEP) {
            for (dz in -GROUND_SCAN..GROUND_SCAN step GROUND_STEP) {
                val top = liveHeights.top(x0 + dx, z0 + dz) ?: continue
                val distance = (dx * dx + dz * dz).toDouble()
                if (top > level.minY && top < position.y && distance < bestDistance) {
                    best = Vec3(x0 + dx + 0.5, top.toDouble(), z0 + dz + 0.5)
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * A glide in the air along routes through open space to [goal], once the elytra is open.
     */
    private fun passage(goal: Vec3, abilities: GlideAbilities, hasOpened: Boolean): Glide {
        search.cancel()
        airways.reset()
        val rockets = Rockets(abilities.rockets, abilities.rocketTicks)
        val clearance = context.options().glideClearance
        val steering = Passage(goal, rockets, clearance, hasGround(goal)) { Terrain.live(level) }
        return Glide(goal, steering).also {
            it.hasOpened = hasOpened
            glide = it
        }
    }

    /**
     * Whether the player falls with nothing to land on safely within the highest drop the walk takes.
     */
    private fun isFallingFar(): Boolean {
        if (player.deltaMovement.y >= 0.0) {
            return false
        }
        val feet = player.blockPosition()
        for (depth in 0..context.safeFall() + 1) {
            val pos = feet.below(depth)
            val state = level.getBlockState(pos)
            if (!state.getCollisionShape(level, pos).isEmpty || !state.fluidState.isEmpty) {
                return false
            }
        }
        return true
    }

    private fun hasGround(goal: Vec3) = Terrain.live(level).isStandable(BlockPos.containing(goal))

    private val progress = TargetProgress(context.body.player, { isActive }) { glide?.landing }

    /**
     * A glide from take-off to touch-down: planned along [path], or a cruise without one.
     */
    private class Glide(val goal: Vec3, val steering: Steering, private val path: Route? = null) {
        var ticks = 0
        var hasOpened = false
        var pressedAt = -REOPEN_TICKS

        /**
         * Whether jump was pressed last tick.
         */
        var jumped = false

        fun points(position: Vec3): List<Vec3> =
            path?.points ?: (steering as? Passage)?.route?.takeIf { it.size > 1 } ?: listOf(position, steering.target)

        val landing: Vec3
            get() = path?.landing ?: steering.target
    }

    private companion object {
        const val MIN_DISTANCE = 16.0
        const val MAX_DISTANCE = 192.0
        const val MIN_DROP = 6.0
        const val START_TOLERANCE = 1.0
        const val RETRY_TICKS = 40
        const val GROUND_SCAN = 96
        const val GROUND_STEP = 8
        const val TAKE_OFF_TICKS = 24
        const val REOPEN_TICKS = 10
        const val GOAL_MOVED = 1.5
        const val HOTBAR_SIZE = 9
    }

}
