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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.FightingStyle
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.SkillLevel
import net.ccbluex.agent.world.entity.Entity
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.entity.Player
import net.ccbluex.agent.world.item.Items
import java.util.Random
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A fighter on the agent's body: a target, its delayed and noisy aim, the movement, the clicks and the skill
 * scheduler. One tick runs: target, hit reaction, skills, aim, movement, attack, then the keys and the look.
 * [Footwork] moves, [Striking] hits; the skills reach into this class for everything else.
 *
 * From LiquidBounce: [HurtTimeSync.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/killaura/features/HurtTimeSync.kt#L56-L72)
 *
 * @agent claude-opus-5-5
 */
internal class BuiltInFight(
    val body: AgentBody,
    val skillLevel: SkillLevel,
    val personality: Personality,
    val rules: Rules,
    val random: Random = Random(),
    private val clock: () -> Long = System::currentTimeMillis,
) : Fight {

    val player: Player get() = body.player
    val style: FightingStyle get() = personality.fighting
    val use: SkillLevel.Use = skillLevel.use(rules.blastWeight)
    val hands = Hands(body)
    val eyes = EyeTrail()
    val runner = SkillRunner(skillsFor(rules))
    val striking = Striking(this)
    val footwork = Footwork(this)
    val tally = Tally()

    /**
     * Ticks this fight has run.
     */
    var tick = 0L
        private set

    /**
     * The agent's navigator, the way every click of the fight goes out; set every tick.
     */
    lateinit var navigator: Navigator

    val cpsMode: Boolean get() = body.isCombat1_8 || rules.oldCombat
    val cps: Double = rules.cps * skillLevel.cps
    val critChance: Double = if (rules.crits) min(1.0, rules.critChance * skillLevel.technique) else 0.0
    val wtapChance: Double get() = min(1.0, rules.wtapChance * skillLevel.technique * style.wtap)
    val reaction: Int = max(0, (rules.reaction * skillLevel.reaction).roundToInt())

    private var engaged: LivingEntity? = null
    override var target: LivingEntity? = null
        private set
    private var seen = 0
    private var attacker: LivingEntity? = null
    private var attackedAt = 0L
    private var attackerUntil = 0L
    private var hurt = false
    private var fighting = false

    /**
     * The target's recent positions, newest first.
     */
    private val trail = ArrayDeque<Vec3>()

    var forward = 0f
    var strafe = 0f
    var jump = false
    var sprint = false
    var sneak = false

    /**
     * A skill set the movement keys this tick.
     */
    var moveControlled = false

    /**
     * A skill keeps the fighter from hitting this tick.
     */
    var attackHeld = false

    /**
     * The rotation the server has, which the fighter's aim checks read.
     */
    val yRot: Float get() = body.sentRotation.yRot
    val xRot: Float get() = body.sentRotation.xRot
    private var look: ViewRotation? = null
    private var lookSpeed = 1.0

    /**
     * Where the fighter turns this tick, with the noise of its hand.
     */
    private var aimRotation = ViewRotation.ZERO

    /**
     * Until when the target's shield is down after an axe hit.
     */
    var stunUntil = 0L

    /**
     * When the off-hand totem last popped.
     */
    var poppedAt = -1000L
        private set
    private var hadTotem = false

    /**
     * Whether the agent's eating ability wants to eat now, so the heal skills leave it alone.
     */
    var eatWanted = false
        private set

    /**
     * Straight down as this fighter looks it: a little short of the 90 degrees a hand never stops at.
     */
    var down = DOWN_MOST
        private set

    override val isHealing: Boolean get() = runner.active?.heals == true
    override val isBusy: Boolean get() = runner.active != null
    override val stats: Stats get() = tally.snapshot()

    override fun engage(target: LivingEntity) {
        engaged = target
    }

    override fun disengage() {
        engaged = null
        target = null
        stand()
    }

    override fun damaged(agent: Agent, attacker: Entity?) {
        this.attacker = attacker as? LivingEntity
        attackedAt = tick
        attackerUntil = tick + ATTACKER_TICKS
        tally.hurt()
        hurt = true
    }

    override fun tick(agent: Agent) {
        tick++
        navigator = agent.navigator
        eatWanted = agent.skills.eat?.wantsToEat == true
        eyes.record(clock(), player.eyePosition)
        val hit = hurt
        hurt = false
        val totem = hands.offHand.isOf(Items.TOTEM_OF_UNDYING)
        if (hadTotem && !totem && player.health > 0) poppedAt = tick
        hadTotem = totem
        resetInputs()
        // The fall comes first: the rescue needs the look, the hand and the keys
        if (agent.skills.voidRescue?.isRescuing == true) return stand()

        val found = findTarget(agent)
        if (found !== target) {
            target = found
            seen = 0
            trail.clear()
        }
        val target = found ?: return stand()
        if (!fighting) {
            down = DOWN_MOST - random.nextFloat() * DOWN_SPREAD
            fighting = true
        }
        if (!eatWanted && !hands.holding) hands.holdPrimary()
        seen++
        trail.addFirst(target.position())
        if (trail.size > TRAIL) trail.removeLast()

        val reacted = seen > reaction
        if (reacted && hit) footwork.reactToHit()
        // A meal of the eating ability holds the hand: no skills, and hits only to save the fighter's life
        if (eatWanted) runner.end(this) else if (reacted) runner.run(this)
        aim(target)
        if (!moveControlled && reacted) footwork.move(target)
        if (eatWanted && !(player.health <= MEAL_HP && distance() <= MEAL_RANGE)) attackHeld = true
        if (!attackHeld && reacted && rules.attacks) striking.attack(target)
        striking.watchLanding()
        apply(agent, target)
        if (!eatWanted) hands.tick()
    }

    private fun resetInputs() {
        forward = 0f
        strafe = 0f
        jump = false
        sprint = false
        sneak = false
        moveControlled = false
        attackHeld = false
        look = null
        lookSpeed = 1.0
        footwork.startTick()
    }

    /**
     * The engaged target; else whoever hit the fighter lately, or the nearest opponent in sight when hunting.
     */
    private fun findTarget(agent: Agent): LivingEntity? {
        val engaged = engaged
        if (engaged != null && !valid(engaged)) return null
        val fought = rules.range * rules.range * ATTACKER_RANGE
        val attacker = attacker?.takeIf { rules.retaliates && tick < attackerUntil }
            ?.takeIf { valid(it) && it.distanceToSqr(player) <= fought }
        // A running skill keeps its target: a rocket climbs out of the range over the target it dives on
        val current = target?.takeIf { valid(it) && (runner.active != null || it.distanceToSqr(player) <= fought) }
        val nearest = if (rules.hunts && engaged == null) nearestOpponent(agent) else null
        return chooseTarget(engaged, attacker, tick - attackedAt, current, nearest, rules.hunts, { inArea(it) }) {
            it.distanceTo(player).toDouble()
        }
    }

    /**
     * Whether a block, crystal, anchor or bucket may go at [pos]: inside the rules' area, or anywhere while engaged.
     */
    fun mayPlace(pos: BlockPos): Boolean = engaged != null || rules.area?.contains(Vec3.atCenterOf(pos)) != false

    fun valid(entity: LivingEntity): Boolean {
        if (entity === player || entity.isRemoved || !entity.isAlive || entity.level() !== body.level) return false
        if (entity is Player) return !entity.isSpectator && !entity.isCreative
        return true
    }

    /**
     * Lets go of the body: ends the skill and any use, leaves the hotbar and the keys to others.
     */
    private fun stand() {
        if (!fighting) return
        fighting = false
        runner.end(this)
        if (body.isUsingItem) body.releaseUsingItem()
        footwork.stop()
        hands.release()
    }

    /**
     * Looks towards [rotation] this tick instead of the target; at [speed] [FLICK] or more as a flick the body has
     * to land now ([LookPurpose.STEER]), slower at the aim's pace.
     */
    fun lookAt(rotation: ViewRotation, speed: Double) {
        look = rotation
        lookSpeed = speed
    }

    fun lookAt(point: Vec3, speed: Double) = lookAt(Aiming.rotation(player.eyePosition, point), speed)

    /**
     * Straight down or up at the current yaw.
     */
    fun lookXRot(xRot: Float, speed: Double) = lookAt(ViewRotation(yRot, xRot), speed)

    private fun aim(target: LivingEntity) {
        val want = look ?: Aiming.rotation(player.eyePosition, aimPoint(target))
        aimRotation = Aiming.jitter(want, skillLevel.noise, random.nextGaussian(), random.nextGaussian())
    }

    /**
     * How the body turns to this tick's look: a flick it has to land now, or an aim.
     */
    val lookPurpose: LookPurpose
        get() = if (lookSpeed >= FLICK) LookPurpose.STEER else LookPurpose.AIM

    /**
     * Where the fighter sees the target: a little behind where it is, at the height of the own eyes on its body.
     */
    private fun aimPoint(target: LivingEntity): Vec3 {
        val seenAt = trail[min(reaction / 2, trail.size - 1)]
        val y = max(seenAt.y + 0.5, min(seenAt.y + target.bbHeight - 0.2, player.eyeY))
        return Vec3(seenAt.x, y, seenAt.z)
    }

    fun aimed(rotation: ViewRotation, tolerance: Double): Boolean = Aiming.aimed(body.sentRotation, rotation, tolerance)

    /**
     * Whether the fighter looks at [xRot] at its own yaw, as for a throw at its feet.
     */
    fun aimedXRot(xRot: Float, tolerance: Double): Boolean = aimed(ViewRotation(yRot, xRot), tolerance)

    /**
     * Whether the fighter looks at [entity] closely enough to hit it.
     */
    fun aimedAt(entity: Entity): Boolean {
        val eye = player.eyePosition
        val point = Aiming.nearestPoint(entity, eye)
        return aimed(Aiming.rotation(eye, point), Aiming.hitTolerance(point.distanceTo(eye)))
    }

    /**
     * Whether the fighter looks at [point] closely enough for its ray to land in the point's block.
     */
    fun aimedAtPoint(point: Vec3): Boolean {
        val eye = player.eyePosition
        return aimed(Aiming.rotation(eye, point), Math.toDegrees(atan2(0.3, max(0.5, eye.distanceTo(point)))))
    }

    /**
     * The rotation that throws onto [point], leading the target's motion.
     */
    fun throwAt(point: Vec3, speed: Double, gravity: Double): ViewRotation =
        Aiming.throwAt(player.eyePosition, point, targetMotion(), speed, gravity)

    /**
     * The target's movement per tick over the last ticks.
     */
    fun targetMotion(): Vec3 = if (trail.size < 3) Vec3.ZERO else trail[0].subtract(trail[2]).scale(0.5)

    fun maxReach(): Double = if (rules.reach > 0) rules.reach else body.maxAttackReach

    fun minReach(): Double = if (rules.reach > 0) 0.0 else body.minAttackReach

    /**
     * How far the next swing reaches: the difficulty's share of the weapon's reach, less this swing's slack.
     */
    fun swingReach(): Double = maxReach() * skillLevel.reach - striking.reachSlack

    fun reachDistance(entity: Entity): Double =
        player.eyePosition.distanceTo(Aiming.nearestPoint(entity, player.eyePosition))

    /**
     * Whether a swing at [entity] lands with what is held: past the item's minimum reach, and for a living one within
     * the next swing's reach as its player sees it; anything else (a crystal) within the item's reach.
     */
    fun inReach(entity: Entity): Boolean {
        val distance = reachDistance(entity)
        if (distance < minReach()) return false
        return if (entity is LivingEntity) seenDistance(entity, distance) <= swingReach() else distance <= maxReach()
    }

    /**
     * The distance to [entity] as its player's screen shows it, or the real one when longer. Only where the fighter
     * stood lags behind; its height stays, so falling onto the target in a crit still lands.
     */
    private fun seenDistance(entity: LivingEntity, distance: Double): Double {
        val lag = viewLag(entity)
        if (lag == 0L) return distance
        val old = eyes.at(clock(), lag) ?: return distance
        val eye = Vec3(old.x, player.eyeY, old.z)
        return max(distance, Aiming.nearestPoint(entity, eye).distanceTo(eye))
    }

    /**
     * How late [entity]'s screen shows the fighter; 0 for anything without a player at a client.
     */
    private fun viewLag(entity: Entity): Long = when (entity) {
        is Player -> when (val latency = entity.latency) {
            null -> EyeTrail.viewLag(body.latencyMillis)
            else -> latency.takeIf { it > 0 }?.let(EyeTrail::viewLag) ?: 0L
        }
        else -> 0L
    }

    fun distance(): Double = target?.let(::reachDistance) ?: Double.MAX_VALUE

    fun horizontalDistance(): Double =
        target?.let { Aiming.horizontal(player.position(), it.position()) } ?: Double.MAX_VALUE

    fun hasLineOfSight(entity: Entity): Boolean = player.hasLineOfSight(entity)

    /**
     * Health and absorption over max health: absorption takes damage first, so it counts as health.
     */
    fun health(): Double = (player.health + player.absorptionAmount).toDouble() / player.maxHealth

    /**
     * Whether health is under [share], which the style moves: a defensive fighter heals earlier.
     */
    fun low(share: Double): Boolean = health() < share * style.heal

    /**
     * Backs off, as healing does; a style that stands its ground stays where it is.
     */
    fun backOff() {
        forward = if (style.retreats) -1f else 0f
    }

    /**
     * How long a skill's step takes: [hard] ticks on hard, up to [slowest] on the easiest level.
     */
    fun slow(hard: Int, slowest: Int): Int = hard + ((slowest - hard) * use.slowness).roundToInt()

    /**
     * Whether a placing click lands; a miss costs the tick.
     */
    fun clickLands(): Boolean = random.nextDouble() < 0.5 + 0.5 * use.placement

    /**
     * Noise in blocks on the score of where a block, crystal or anchor goes: worse spots on the easier levels.
     */
    fun placementNoise(): Double = random.nextGaussian() * (1 - use.placement) * 2.5

    /**
     * Whether the fighter carries totems: then it eats right after a pop and whenever absorption is gone.
     */
    fun carriesTotems(): Boolean =
        hands.offHand.isOf(Items.TOTEM_OF_UNDYING) || hands.has { it.isOf(Items.TOTEM_OF_UNDYING) }

    fun strike(entity: Entity) = striking.strike(entity)

    private fun apply(agent: Agent, target: LivingEntity) {
        footwork.secure()
        if (!moveControlled && look == null && footwork.chasing) {
            // Far or across a gap: the navigator walks there and turns the body; the aim picks up where it looks
            agent.walkTo(target.position(), CHASE_RADIUS)
            return
        }
        agent.press(footwork.input(), exact = false)
        body.look(aimRotation, lookPurpose)
    }

    companion object {
        /**
         * A look this many times as fast as aiming is a flick.
         */
        const val FLICK = 3.0

        private const val TRAIL = 20

        private const val ATTACKER_TICKS = 200

        /**
         * Squared share of the range an attacker or the current target stays fought within.
         */
        private const val ATTACKER_RANGE = 2.25

        private const val CHASE_RADIUS = 2.0

        /**
         * While the agent eats it only hits at this little health with the target this close.
         */
        private const val MEAL_HP = 4f
        private const val MEAL_RANGE = 3.5

        /**
         * The pitch of [down], rolled per fight within [DOWN_SPREAD] degrees under [DOWN_MOST].
         */
        private const val DOWN_MOST = 89.4f
        private const val DOWN_SPREAD = 3f
    }
}
