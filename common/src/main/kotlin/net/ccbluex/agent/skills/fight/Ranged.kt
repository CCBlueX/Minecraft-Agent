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

import net.ccbluex.agent.skills.Throws
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.item.Enchantments
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items

/**
 * Bow, rod, throwables and pearls: everything the fighter shoots or throws at its target.
 *
 * @agent claude-opus-5-5
 */
internal object Ranged {

    fun bowLoaded(fight: BuiltInFight): Boolean {
        val slot = fight.hands.find { it.isOf(Items.BOW) } ?: return false
        return fight.hands.stack(slot).enchantLevel(Enchantments.INFINITY) > 0 ||
            fight.hands.has { it.isOf(Items.ARROW) }
    }

    fun throwable(stack: ItemStack): Boolean =
        stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG) || stack.isOf(Items.BLUE_EGG) ||
            stack.isOf(Items.BROWN_EGG) || stack.isOf(Items.WIND_CHARGE)
}

/**
 * Draws the bow on a target out of melee range and shoots where it will be.
 *
 * @agent claude-opus-5-5
 */
internal class Bow : Technique(Skill.BOW, 20, 30) {

    private var since = 0L
    private var draw = 0
    private var shot = false

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        val distance = fight.horizontalDistance()
        return distance in MIN_RANGE..MAX_RANGE && fight.hasLineOfSight(target) && Ranged.bowLoaded(fight)
    }

    override fun begin(fight: BuiltInFight) {
        shot = false
        if (fight.hands.hold { it.isOf(Items.BOW) }) fight.body.startUsingItem(InteractionHand.MAIN_HAND)
        since = fight.tick
        draw = fight.slow(20, 40)
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val target = fight.target ?: return false
        if (fight.horizontalDistance() < ABORT_RANGE) return false
        fight.lookAt(fight.throwAt(target.position().add(0.0, AIM_HEIGHT, 0.0), SPEED, GRAVITY), 2.0)
        if (fight.tick - since < draw) return true
        fight.body.releaseUsingItem()
        shot = true
        return false
    }

    // Changing the held item lowers a drawn bow without shooting it
    override fun end(fight: BuiltInFight) {
        fight.hands.holdPrimary()
        if (!shot && fight.body.isUsingItem && fight.hands.mainHand.isOf(Items.BOW)) fight.body.releaseUsingItem()
    }

    private companion object {
        const val MIN_RANGE = 9.0
        const val MAX_RANGE = 45.0
        const val ABORT_RANGE = 5.0
        const val AIM_HEIGHT = 1.2

        /**
         * A fully drawn arrow, blocks a tick.
         */
        const val SPEED = 3.0
        const val GRAVITY = 0.05
    }
}

/**
 * At 4 to 7 blocks: casts the rod at the target and reels in once the hook caught it, which pulls the target into
 * the next hit; reels in anyway when the hook missed.
 *
 * @agent claude-opus-5-5
 */
internal class Rod : Technique(Skill.ROD, 30, 40) {

    private var out = false
    private var phaseAt = 0L

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        if (!fight.player.onGround() || fight.body.isUsingItem || fight.player.fishing != null) return false
        return fight.horizontalDistance() in MIN_RANGE..MAX_RANGE && fight.hands.has { it.isOf(Items.FISHING_ROD) } &&
            fight.hasLineOfSight(target)
    }

    override fun begin(fight: BuiltInFight) {
        fight.hands.hold { it.isOf(Items.FISHING_ROD) }
        out = false
        phaseAt = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val target = fight.target ?: return false
        val elapsed = fight.tick - phaseAt
        if (!fight.hands.mainHand.isOf(Items.FISHING_ROD)) return false
        if (!out) {
            val rotation = fight.throwAt(target.position().add(0.0, 1.0, 0.0), SPEED, GRAVITY)
            fight.lookAt(rotation, 3.0)
            if (!fight.aimed(rotation, AIM_TOLERANCE) && elapsed < AIM_TICKS) return true
            if (!fight.hands.use()) return false
            out = true
            phaseAt = fight.tick
            return true
        }
        val hook = fight.player.fishing ?: return elapsed < 3
        val caught = hook.hookedIn === target ||
            Aiming.nearestPoint(target, hook.position()).distanceToSqr(hook.position()) < CAUGHT_SQ
        if (caught || hook.onGround() || elapsed > REEL_TICKS) {
            fight.hands.use()
            return false
        }
        return true
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val MIN_RANGE = 4.0
        const val MAX_RANGE = 7.0
        const val AIM_TOLERANCE = 4.0
        const val AIM_TICKS = 8
        const val REEL_TICKS = 15
        const val CAUGHT_SQ = 0.25

        /**
         * A cast hook flies at about 1.1 blocks a tick, falling 0.03 a tick.
         */
        const val SPEED = 1.1
        const val GRAVITY = 0.03
    }
}

/**
 * Snowballs, eggs and wind charges thrown at a target out of reach: knocks it back, off its pillar or into a hole.
 *
 * @agent claude-opus-5-5
 */
internal class Throw : Technique(Skill.THROW, 25, 15) {

    private var since = 0L
    private var aim = 0

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        val slot = fight.hands.find(Ranged::throwable) ?: return false
        val max = if (fight.hands.stack(slot).isOf(Items.WIND_CHARGE)) WIND_RANGE else MAX_RANGE
        return fight.horizontalDistance() in MIN_RANGE..max && fight.hasLineOfSight(target)
    }

    override fun begin(fight: BuiltInFight) {
        fight.hands.hold(Ranged::throwable)
        since = fight.tick
        aim = fight.slow(2, 10)
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val target = fight.target ?: return false
        if (!Ranged.throwable(fight.hands.mainHand)) return false
        val gravity = if (fight.hands.mainHand.isOf(Items.WIND_CHARGE)) WIND_GRAVITY else GRAVITY
        val rotation = fight.throwAt(target.position().add(0.0, 1.0, 0.0), SPEED, gravity)
        fight.lookAt(rotation, 3.0)
        if (fight.tick - since < aim && !fight.aimed(rotation, 3.0)) return true
        fight.hands.use()
        return false
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val MIN_RANGE = 4.0
        const val MAX_RANGE = 24.0
        const val WIND_RANGE = 12.0
        const val SPEED = 1.5
        const val GRAVITY = 0.03
        const val WIND_GRAVITY = 0.001
    }
}

/**
 * Far away: a pearl onto the target, thrown once the sent rotation brings the fighter down on the target's ground.
 *
 * @agent claude-opus-5-5
 */
internal class Pearl : Technique(Skill.PEARL, 20, 200) {

    private var since = 0L

    override fun can(fight: BuiltInFight): Boolean {
        val target = fight.target ?: return false
        return fight.horizontalDistance() in MIN_RANGE..MAX_RANGE && fight.player.onGround() &&
            fight.hands.has { it.isOf(Items.ENDER_PEARL) } &&
            !fight.player.cooldowns.isOnCooldown(Items.ENDER_PEARL) &&
            fight.hasLineOfSight(target)
    }

    override fun begin(fight: BuiltInFight) {
        fight.hands.hold { it.isOf(Items.ENDER_PEARL) }
        since = fight.tick
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val target = fight.target ?: return false
        val level = fight.body.level
        val players = fight.body.players()
        val rotation = Throws.pearlRotation(level, fight.player, target.position(), players) ?: return false
        fight.lookAt(rotation, 3.0)
        if (!Throws.pearlLandsAt(level, fight.player, fight.body.sentRotation, target.position(), players)) {
            return fight.tick - since <= AIM_TICKS
        }
        if (fight.hands.mainHand.isOf(Items.ENDER_PEARL)) fight.hands.use()
        return false
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val MIN_RANGE = 14.0
        const val MAX_RANGE = 45.0
        const val AIM_TICKS = 10
    }
}
