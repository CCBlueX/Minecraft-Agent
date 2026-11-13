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

import net.ccbluex.agent.skills.FightingStyle
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * How the fighter moves around its target: the forward ladder (w-tap, minimum reach, feint, kept distance, approach),
 * strafing, jumps, the hit reactions, sword block-hits, and the checks that keep it from stepping off edges or into
 * lava. Far away or across a gap it leaves the walk to the navigator ([chasing]).
 *
 * From LiquidBounce: [Outnumbered.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Outnumbered.kt#L60-L60)
 *
 * @agent claude-opus-5-5
 */
internal class Footwork(private val fight: BuiltInFight) {

    private var strafeDir = 1
    private var strafing = true
    private var strafeSwitchAt = 0L
    private var wtapTicks = 0
    private var stap = false
    private var feintUntil = 0L
    private var feintAt = 0L
    private var blockHitUntil = 0L
    private var resetJump = false

    /**
     * Whether the target is far or across a gap this tick, so the navigator walks there.
     */
    var chasing = false
        private set

    private val player get() = fight.player
    private val level get() = fight.body.level

    fun startTick() {
        resetJump = false
        chasing = false
    }

    fun stop() {
        wtapTicks = 0
        if (blockHitUntil != 0L) {
            blockHitUntil = 0
            fight.body.releaseUsingItem()
        }
    }

    fun startWtap() {
        wtapTicks = fight.random.nextInt(2, 5)
        stap = fight.random.nextDouble() < STAP_SHARE
    }

    /**
     * A jump the tick a hit lands takes the edge off its knockback, and the return hit comes at once when the charge
     * allows a sprint hit. A style that hits back does so under any rules.
     */
    fun reactToHit() {
        val tick = fight.tick
        if (fight.style.hitBack) {
            fight.striking.hitBackUntil = tick + HIT_BACK_TICKS
            if (fight.cpsMode) fight.striking.clickNow()
        }
        if (!fight.rules.hitReactions) return
        val chance = minOf(1.0, fight.skillLevel.technique * REACTION_SHARE)
        resetJump = player.onGround() && fight.random.nextDouble() < chance
        if (fight.random.nextDouble() < chance) fight.striking.hitBackUntil = tick + HIT_BACK_TICKS
    }

    fun move(target: LivingEntity) {
        val here = player.position()
        val distance = Aiming.horizontal(here, target.position())
        val keep = keep()
        if (distance > maxOf(CHASE_RANGE, keep + 2) || distance > GAP_CHECK_RANGE && gapTowards(target.position())) {
            chasing = true
            return
        }
        val reachDistance = fight.reachDistance(target)
        val tick = fight.tick
        if (fight.style == FightingStyle.TRICKSTER) feint(reachDistance)
        fight.forward = when {
            wtapTicks > 0 -> {
                wtapTicks--
                if (stap) -1f else 0f
            }
            // Inside the weapon's minimum reach (a spear): back out of it, never step in to hit
            fight.minReach() > 0 && reachDistance < fight.minReach() + 0.4 -> -1f
            // A fake retreat: the opponent follows into the next hit
            tick < feintUntil -> -1f
            keep > 0 && reachDistance < keep + 0.4 -> if (reachDistance < keep - 0.4) -1f else 0f
            reachDistance > approachDistance() -> {
                fight.sprint = true
                1f
            }
            reachDistance < 0.6 -> -1f
            else -> 0f
        }
        if (distance < STRAFE_RANGE) strafe(here)
        if (player.onGround() && fight.forward > 0 &&
            (blockedAhead(here) || distance > 7 && fight.random.nextDouble() < FAR_JUMP_CHANCE)
        ) {
            fight.jump = true
        }
        // Step into the return hit at a sprint, and jump with it on the tick a hit lands
        if (tick <= fight.striking.hitBackUntil && fight.forward == 0f && wtapTicks == 0) {
            fight.forward = 1f
            fight.sprint = true
        }
        if (resetJump) {
            fight.jump = true
            if (fight.forward >= 0) {
                fight.forward = 1f
                fight.sprint = true
            }
        }
        blockHit()
    }

    private fun approachDistance(): Double =
        if (fight.style == FightingStyle.AGGRESSIVE) 1.2 else fight.swingReach() - 0.35

    /**
     * The distance the style holds this tick; 0 to go in for the hit.
     */
    private fun keep(): Double = when (fight.style) {
        FightingStyle.DEFENSIVE -> if (fight.striking.charging()) KEEP_CHARGING else 0.0
        FightingStyle.KITER -> when {
            fight.runner.readySoon(fight, Skill.BOW) && Ranged.bowLoaded(fight) -> KEEP_BOW
            fight.runner.readySoon(fight, Skill.ROD) -> KEEP_ROD
            fight.striking.charging() -> KEEP_CHARGING
            else -> 0.0
        }
        else -> 0.0
    }

    /**
     * Now and then within reach: backs off for a few ticks to draw the opponent in.
     */
    private fun feint(reachDistance: Double) {
        if (fight.tick < feintAt || reachDistance > fight.swingReach() + 0.5) return
        feintAt = fight.tick + fight.random.nextInt(40, 90)
        if (fight.random.nextDouble() < FEINT_CHANCE) feintUntil = fight.tick + fight.random.nextInt(5, 11)
    }

    private fun strafe(here: Vec3) {
        val chance = fight.skillLevel.strafe * fight.style.strafe
        if (fight.tick >= strafeSwitchAt) {
            if (fight.random.nextBoolean()) strafeDir = -strafeDir
            strafing = fight.random.nextDouble() < chance
            strafeSwitchAt = fight.tick + fight.random.nextInt(10, 36)
        }
        val ring = fight.rules.ringCentre?.takeIf { chance > 0 }
        if (ring != null) strafeDir = towards(here, ring)
        if (strafing || ring != null) fight.strafe = strafeDir.toFloat()
    }

    /**
     * The strafe side that leads towards [centre]: 1 for left.
     */
    private fun towards(here: Vec3, centre: Vec3): Int {
        if (Aiming.horizontal(here, centre) < 1.5) return strafeDir
        val radians = Math.toRadians(fight.yRot.toDouble())
        val dot = (centre.x - here.x) * cos(radians) + (centre.z - here.z) * sin(radians)
        return if (dot >= 0) 1 else -1
    }

    /**
     * A short sword block right after a hit under 1.8 rules takes the edge off the return hit.
     */
    private fun blockHit() {
        if (!fight.cpsMode || !fight.hands.mainHand.isSword) return
        val tick = fight.tick
        if (tick - fight.striking.lastAttack == 1L &&
            fight.random.nextDouble() < BLOCK_HIT_SHARE * fight.skillLevel.technique
        ) {
            blockHitUntil = tick + fight.random.nextInt(2, 5)
            fight.body.startUsingItem(InteractionHand.MAIN_HAND)
        } else if (blockHitUntil != 0L && tick >= blockHitUntil) {
            blockHitUntil = 0
            fight.body.releaseUsingItem()
        }
    }

    private fun blockedAhead(here: Vec3): Boolean {
        val feet = BlockPos.containing(here.add(heading(fight.yRot.toDouble()).scale(0.8)))
        return solid(feet) && !solid(feet.above()) && !solid(feet.above(2))
    }

    /**
     * Whether the ground ends between the fighter and [goal], where only the navigator gets across.
     */
    private fun gapTowards(goal: Vec3): Boolean {
        val here = player.position()
        val way = goal.subtract(here).multiply(1.0, 0.0, 1.0)
        val length = way.length()
        if (length < 1) return false
        val unit = way.scale(1 / length)
        var step = 1.0
        while (step < length - 0.5) {
            if (!groundBelow(here.add(unit.scale(step)))) return true
            step += 1.0
        }
        return false
    }

    /**
     * Last checks on the keys: never into lava or off an edge.
     */
    fun secure() {
        if (fight.moveControlled.not() && chasing) return
        if (player.onGround() && (fight.forward != 0f || fight.strafe != 0f) && !safeStep()) {
            fight.forward = 0f
            fight.strafe = 0f
            strafeDir = -strafeDir
        }
        if (fight.rules.guardsEdges) guardEdges()
    }

    /**
     * Whether the step the keys lead to has ground under it, and no lava.
     */
    private fun safeStep(): Boolean {
        val way = way() ?: return true
        val probe = BlockPos.containing(player.position().add(way.scale(0.9)))
        if (lava(probe)) return false
        if (solid(probe)) return true
        for (dy in 1..3) {
            val below = probe.below(dy)
            if (lava(below)) return false
            if (solid(below) || liquid(below)) return true
        }
        return false
    }

    /**
     * Over the void a step, sprint or jump towards a target across a gap carries the body off the edge: a sneak and
     * no keys when the next step has no ground below; no sprint or jump when the ground ends within a sprint jump's
     * length, or beside the body on a bridge or ledge, where a jump's sideways drift takes it off.
     */
    private fun guardEdges() {
        val at = player.position()
        val velocity = player.deltaMovement.multiply(1.0, 0.0, 1.0)
        val way = way() ?: velocity.takeIf { it.lengthSqr() > 0.0025 }?.normalize()
        val edge = way != null && !groundBelow(at.add(way.scale(0.7)))
        fight.sneak = fight.sneak || edge || player.onGround() && !groundBelow(at)
        if (way == null) return
        if (edge) {
            fight.forward = 0f
            fight.strafe = 0f
            fight.jump = false
            fight.sprint = false
            return
        }
        val side = Vec3(-way.z, 0.0, way.x).scale(0.6)
        val narrow = !groundBelow(at.add(side)) || !groundBelow(at.subtract(side))
        if (narrow || EDGE_DISTANCES.any { !groundBelow(at.add(way.scale(it))) }) {
            fight.jump = false
            fight.sprint = false
        }
    }

    /**
     * The world direction the keys lead to, null without keys.
     */
    private fun way(): Vec3? {
        val radians = Math.toRadians(fight.yRot.toDouble())
        val x = fight.strafe * cos(radians) - fight.forward * sin(radians)
        val z = fight.forward * cos(radians) + fight.strafe * sin(radians)
        val length = hypot(x, z)
        return if (length < 0.01) null else Vec3(x / length, 0.0, z / length)
    }

    private fun groundBelow(point: Vec3): Boolean {
        val pos = BlockPos.containing(point)
        return (0..GROUND_DEPTH).any { solid(pos.below(it)) || liquid(pos.below(it)) }
    }

    private fun solid(pos: BlockPos) = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty

    private fun liquid(pos: BlockPos) = !level.getFluidState(pos).isEmpty

    private fun lava(pos: BlockPos) = level.getFluidState(pos).type.isSame(Fluids.LAVA)

    fun input(): Input = Input(
        fight.forward > 0, fight.forward < 0, fight.strafe > 0, fight.strafe < 0, fight.jump, fight.sneak,
        fight.sprint && fight.forward > 0 && !fight.sneak,
    )

    companion object {
        private const val STAP_SHARE = 0.3
        private const val HIT_BACK_TICKS = 3
        private const val REACTION_SHARE = 0.8
        private const val BLOCK_HIT_SHARE = 0.3
        private const val FEINT_CHANCE = 0.4
        private const val FAR_JUMP_CHANCE = 0.25
        private const val KEEP_CHARGING = 3.6
        private const val KEEP_BOW = 10.0
        private const val KEEP_ROD = 5.5
        private const val STRAFE_RANGE = 6.0

        /**
         * Beyond this the navigator walks to the target; within it the fighter steers itself.
         */
        const val CHASE_RANGE = 8.0
        private const val GAP_CHECK_RANGE = 2.5
        private const val GROUND_DEPTH = 4
        private val EDGE_DISTANCES = doubleArrayOf(1.5, 2.5, 3.5)

        fun heading(yRot: Double): Vec3 {
            val radians = Math.toRadians(yRot)
            return Vec3(-sin(radians), 0.0, cos(radians))
        }
    }
}
