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
import net.ccbluex.agent.pathfinder.glide.GlideAbilities
import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

/**
 * Bounces along a flat floor, such as the Nether roof, with an elytra on a body that reopens it each time the feet
 * touch the floor ([net.ccbluex.agent.navigation.Body.recastsElytra]): much faster than running. The rest of the
 * way is left to walking.
 *
 * From LiquidBounce: [RoofBounce.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/RoofBounce.kt#L53-L176)
 *
 * @agent claude-opus-5-5
 */
internal class RoofBounce(private val context: NavigationContext) {

    private var bounce: Bounce? = null

    val isActive: Boolean
        get() = bounce != null

    /**
     * Bounces one tick towards [goal] while an elytra is worn and the body recasts it; false to walk.
     */
    fun navigate(goal: Vec3, abilities: GlideAbilities?, frameYRot: Float): Boolean {
        val player = context.body.player
        val position = player.position()
        val active = bounce
        val bounce = when {
            abilities == null || !context.body.recastsElytra -> null
            active == null -> start(position, goal)
            else -> keep(active, position, goal)
        }
        this.bounce = bounce
        if (bounce == null) {
            return false
        }

        bounce.ticks++
        val direction = getDirectionalInputTowards(goal.subtract(position), frameYRot)
        // Opening the elytra takes a fresh jump press in the air; after that the recast reopens it on every bounce.
        val jump = player.isFallFlying || player.onGround() || bounce.ticks % 2 == 0
        context.body.press(
            Input(direction.forward(), direction.backward(), direction.left(), direction.right(), jump, false, false),
            true,
        )

        context.route.blocks(emptyList(), emptyList())
        context.route.following(listOf(position, bounce.lineEnd), progress)

        context.body.look(ViewRotation(bounce.goal.subtract(position).yRot, X_ROT), LookPurpose.STEER)
        return true
    }

    fun halt() {
        bounce = null
    }

    private fun start(position: Vec3, goal: Vec3): Bounce? {
        if (!context.body.player.onGround() || position.horizontalDistanceTo(goal) < MIN_DISTANCE) {
            return null
        }

        val end = bounceLine(Terrain.live(context.body.level), position, goal, LOOKAHEAD) ?: return null
        return Bounce(goal, floor = position.y, end)
    }

    /**
     * [bounce] with its line moved on, or null once the goal is near or the floor ends.
     */
    private fun keep(bounce: Bounce, position: Vec3, goal: Vec3): Bounce? {
        if (position.horizontalDistanceTo(goal) < STOP_DISTANCE || position.y < bounce.floor - FLOOR_TOLERANCE) {
            return null
        }

        val feet = Vec3(position.x, bounce.floor, position.z)
        bounce.lineEnd = bounceLine(Terrain.live(context.body.level), feet, goal, KEEP_LOOKAHEAD) ?: return null
        return bounce
    }

    private val progress = TargetProgress(context.body.player, { isActive }) { bounce?.lineEnd }

    private class Bounce(val goal: Vec3, val floor: Double, var lineEnd: Vec3) {
        var ticks = 0
    }

    companion object {
        // The pitch that bounces fastest.
        private const val X_ROT = 75f
        private const val MIN_DISTANCE = 32.0
        private const val STOP_DISTANCE = 12.0
        private const val LOOKAHEAD = 24.0
        private const val KEEP_LOOKAHEAD = 12.0
        private const val MIN_KNOWN = 8.0
        private const val STEP = 0.5
        private const val FLAT = 1.0E-3
        private const val FLOOR_TOLERANCE = 0.5
        private const val HEADROOM = 2
        private const val HALF_WIDTH = 0.4
        private val SIDES = doubleArrayOf(-HALF_WIDTH, 0.0, HALF_WIDTH)

        /**
         * The point towards [goal], up to [lookahead] blocks on, that the flat open floor under [feet] runs to.
         */
        fun bounceLine(terrain: Terrain, feet: Vec3, goal: Vec3, lookahead: Double): Vec3? {
            val dx = goal.x - feet.x
            val dz = goal.z - feet.z
            val length = Mth.length(dx, dz)
            if (length < STEP) {
                return null
            }

            val ux = dx / length
            val uz = dz / length
            val reach = min(lookahead, length)
            val y = Mth.floor(feet.y + FLAT)
            var along = 0.0
            while (along <= reach) {
                for (side in SIDES) {
                    val x = Mth.floor(feet.x + ux * along - uz * side)
                    val z = Mth.floor(feet.z + uz * along + ux * side)
                    if (!terrain.isKnown(x, y, z)) {
                        return if (along >= MIN_KNOWN) at(feet, ux, uz, along) else null
                    }
                    val stand = terrain.standHeight(x, y, z)
                    if (stand.isNaN() || abs(stand - feet.y) > FLAT || !terrain.isPassable(x, y + HEADROOM, z)) {
                        return null
                    }
                }
                along += STEP
            }
            return at(feet, ux, uz, reach)
        }

        private fun at(feet: Vec3, ux: Double, uz: Double, along: Double) =
            Vec3(feet.x + ux * along, feet.y, feet.z + uz * along)
    }

}
