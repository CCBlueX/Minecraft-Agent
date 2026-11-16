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

package net.ccbluex.agent.skills.maps

import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.tick.tickUntil
import net.ccbluex.agent.tick.waitTicks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * The steps that move a map's story on: follows the ones [map] knows, else tries what the map offers and keeps what
 * led somewhere. Something tried only counts once the player got somewhere new after it (a teleport, gear, an
 * effect it never had): a switch that only talks, such as a music test, is never learnt. Switches that turn
 * something off are turned back on and left alone.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class Story(
    private val play: MapPlay,
    private val maps: KnownMaps,
    private val changes: Changes,
    private val report: (String) -> Unit,
) {

    var map: KnownMap? = null

    private val leads = Leads()

    /**
     * What was tried and had an effect since the player last got somewhere new.
     */
    private val pending = ArrayList<Pending>()
    private var committedAt = 0
    private var next = 0
    private var resumed = false
    private val failures = HashMap<Int, Int>()
    private var lastLeadTick = 0
    private var lastTried: Vec3? = null

    /**
     * Does the next thing: the known step from here, else one lead.
     */
    suspend fun advance(agent: Agent, player: Player) {
        commitIfProgressed(player)
        val step = knownStep(player)
        if (step == null || !replay(agent, player, step)) explore(agent, player)
        commitIfProgressed(player)
    }

    /**
     * Keeps the step the player got somewhere new after, as by a teleport, with the steps it followed on: one that
     * turned on what it went for, as a button the teleporter it stepped into. Everything else tried is noise.
     */
    fun commitIfProgressed(player: Player) {
        val progress = changes.since(committedAt).filter { it.kind in PROGRESS }
        if (progress.isEmpty()) return
        val map = map
        committedAt = changes.ticks
        failures.clear()
        val kept = ArrayList<Pending>()
        var last = pending.lastIndex
        if (last >= 0) kept += pending[last]
        while (last > 0 && pending[last].follows) kept.add(0, pending[--last])
        pending.clear()
        if (map == null || kept.isEmpty()) {
            return
        }
        report("map progress: ${progress.joinToString { "${it.kind.label} ${it.detail}" }}, keeping ${kept.size} step(s)")
        var from = map.steps.size.coerceAtMost(next)
        for (done in kept) {
            val step = KnownMap.Step(done.action, done.at, done.target, done.entity, done.effect, null, done.seconds)
            val same = map.steps.indexOfFirst { it.action == step.action && it.target.near(step.target) }
            if (same >= 0) {
                from = same + 1
                continue
            }
            map.steps.add(from++, step)
            report("map step ${from}/${map.steps.size} learnt: ${step.action} ${step.entity ?: ""} at ${step.target?.let(::describe)} (${step.effect})")
        }
        next = from
        pending.clear()
        play.landmark(map, player)
        maps.save(map)
    }

    /**
     * The known step to do from here: the next one in order, or a later one close by; at the start the one nearest
     * the player, as after a restart. None once a step failed twice here.
     */
    private fun knownStep(player: Player): KnownMap.Step? {
        val steps = map?.steps ?: return null
        val position = player.position()
        fun distance(index: Int) = steps[index].at.toVec3().distanceTo(position)
        val candidates = steps.indices.filter { it >= next || !resumed }.filter { distance(it) < NEAR_STEP }
        resumed = true
        if (next < steps.size && distance(next) < STEP_RANGE) {
            return steps[next].takeIf { (failures[next] ?: 0) < MAX_FAILURES }
        }
        next = candidates.minByOrNull(::distance) ?: return null
        return steps[next].takeIf { (failures[next] ?: 0) < MAX_FAILURES }
    }

    /**
     * Does [step] as learnt; true once it had its effect again.
     */
    private suspend fun replay(agent: Agent, player: Player, step: KnownMap.Step): Boolean {
        val index = next
        val target = (step.target ?: step.at).toVec3()
        play.show("Step ${index + 1}: ${step.action}", describe(step.target ?: step.at))
        report("map step ${index + 1}/${map?.steps?.size} replay ${step.action} ${step.entity ?: ""} at ${describe(step.target ?: step.at)}")
        val startTick = changes.ticks
        val mark = changes.mark()
        val lead = when (step.action) {
            Leads.Lead.Kind.USE_ENTITY.label -> player.level().entitiesNear(target, SAME_PLACE)
                .filter { Leads.typeOf(it) == step.entity && it.position().distanceTo(target) < SAME_PLACE }
                .minByOrNull { it.position().distanceTo(target) }
                ?.let { Leads.Lead(Leads.Lead.Kind.USE_ENTITY, it.uuid, it.position(), it, null, false) }
            Leads.Lead.Kind.USE_BLOCK.label -> BlockPos.containing(target).let { Leads.Lead(Leads.Lead.Kind.USE_BLOCK, it, target, null, it, false) }
            Leads.Lead.Kind.STEP_INTO.label -> Leads.Lead(Leads.Lead.Kind.STEP_INTO, target, target, null, null, false)
            else -> Leads.Lead(Leads.Lead.Kind.WALK_UP, target, target, null, null, false)
        }
        if (lead == null || !act(agent, player, lead)) {
            // Shut in while a scene plays, as for a presentation: the way opens once it ends
            if (!changes.isTalking) failures.merge(index, 1, Int::plus)
            report("map step ${index + 1} not reached, waiting")
            play.show("Waiting", null)
            waitTicks(REPLAY_RETRY_TICKS)
            return true
        }
        val wanted = Changes.Kind.entries.filter { it.label in step.effect }.toSet()
        val limit = maxOf((step.seconds * 2 * TICKS_PER_SECOND).toInt(), MIN_EFFECT_TICKS)
        fun done() = changes.since(mark).any { it.kind in wanted } || play.isFalling(player)
        tickUntil { ticks -> done() || ticks >= limit && !changes.isTalking }
        if (!done()) {
            failures.merge(index, 1, Int::plus)
            report("map step ${index + 1} had no effect")
            return false
        }
        val seconds = (changes.ticks - startTick) / TICKS_PER_SECOND.toDouble()
        report("map step ${index + 1} done after ${"%.1f".format(seconds)}s (known): ${step.effect}")
        next = index + 1
        // What the step led to is progress already known
        committedAt = changes.ticks
        return true
    }

    private suspend fun explore(agent: Agent, player: Player) {
        val now = changes.ticks
        // Carried by the map, as up a lift: nothing to try until it puts the player down
        if (!player.onGround() && !player.isFallFlying && !play.isFalling(player) && !player.isInWater) {
            play.show("Riding", null)
            waitTicks(IDLE_TICKS)
            return
        }
        val lead = leads.best(player, changes, map?.signs.orEmpty(), now, lastTried)
        if (lead == null) {
            play.show(if (changes.isTalking || player.activeEffects.isNotEmpty() && !player.onGround()) "Watching the scene" else "Waiting", null)
            waitTicks(IDLE_TICKS)
            if (!changes.isTalking && now - lastLeadTick > RETRY_ALL_TICKS) {
                lastLeadTick = now
                leads.retry()
                wander(agent, player)
            }
            return
        }
        lastLeadTick = now
        play.show("Trying", lead.describe())
        report("map try ${lead.describe()}")
        val talking = changes.isTalking
        val from = player.position()
        val mark = changes.mark()
        val reached = act(agent, player, lead)
        // Sent on while going there: the place it happened is a zone to step into
        changes.since(mark).firstOrNull { it.kind == Changes.Kind.TELEPORT }?.let { teleport ->
            report("map walked into a place that sent the player on, at ${describe(teleport.position)}")
            leads.tried(lead, changes.ticks, worked = true)
            val at = KnownMap.Point.of(teleport.position)
            pending += Pending(
                Leads.Lead.Kind.STEP_INTO.label, at, at, null, Changes.Kind.TELEPORT.label,
                (teleport.tick - committedAt) / TICKS_PER_SECOND.toDouble(), follows = lead.isNew,
            )
            return
        }
        if (!reached) {
            report("map could not reach ${lead.describe()}")
            leads.tried(lead, changes.ticks, worked = false, unreachable = true)
            return
        }
        tickUntil { ticks -> ticks >= EFFECT_TICKS || changes.since(mark).any { it.kind in PROGRESS } || play.isFalling(player) }
        // A scene that talks on its own says nothing about what was tried, nor does a button going down
        val effects = changes.since(mark, if (talking) setOf(Changes.Kind.CHAT) else emptySet())
            .filter { lead.block == null || it.kind != Changes.Kind.SET || BlockPos.containing(it.position) != lead.block }
        if (player.isPassenger) {
            report("map ${lead.describe()} is a seat")
            leads.seat(lead)
            return
        }
        // A drop into open space glides once the wings are open, after this wait
        val fell = play.isFalling(player)
        leads.tried(lead, changes.ticks, worked = effects.isNotEmpty() || fell)
        if (effects.isEmpty() && !fell) return
        report("map ${lead.describe()} did: ${effects.joinToString { "${it.kind.label} ${it.detail}" }}${if (fell) " fall" else ""}")
        val switchedOff = effects.firstOrNull { it.kind == Changes.Kind.CHAT && OFF.containsMatchIn(it.detail) }
        if (switchedOff != null) {
            report("map ${lead.describe()} switched something off, switching it back on")
            act(agent, player, lead)
            leads.avoid(lead)
            return
        }
        val labels = effects.map { it.kind.label } + listOfNotNull(Changes.Kind.GLIDE.label.takeIf { fell })
        pending += Pending(
            lead.kind.label, KnownMap.Point.of(from), KnownMap.Point.of(lead.position), lead.entity?.let(Leads::typeOf),
            labels.joinToString(), (changes.ticks - committedAt) / TICKS_PER_SECOND.toDouble(), lead.isNew,
        )
    }

    /**
     * Goes to [lead] and does what it asks: interact, use, step into or walk up to it. False when it was not reached.
     */
    private suspend fun act(agent: Agent, player: Player, lead: Leads.Lead): Boolean {
        val reach = when (lead.kind) {
            Leads.Lead.Kind.USE_ENTITY, Leads.Lead.Kind.USE_BLOCK -> USE_REACH
            Leads.Lead.Kind.STEP_INTO -> STEP_REACH
            Leads.Lead.Kind.WALK_UP -> WALK_UP_REACH
        }
        val ending = play.walk(agent, player, lead.position, reach)
        lastTried = lead.position
        when (lead.kind) {
            Leads.Lead.Kind.USE_ENTITY -> {
                val entity = lead.entity ?: return false
                if (!entity.isAlive || player.eyePosition.distanceTo(entity.boundingBox.center) > INTERACT_REACH) return false
                agent.navigator.lookAt(entity.boundingBox.center)
                waitTicks(LOOK_TICKS)
                agent.body.interact(entity, InteractionHand.MAIN_HAND)
                agent.body.swing(InteractionHand.MAIN_HAND)
            }
            Leads.Lead.Kind.USE_BLOCK -> {
                val pos = lead.block ?: return false
                if (player.eyePosition.distanceTo(lead.position) > INTERACT_REACH) return false
                agent.navigator.lookAt(Vec3.atCenterOf(pos))
                waitTicks(LOOK_TICKS)
                agent.body.useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                agent.body.swing(InteractionHand.MAIN_HAND)
            }
            // A drop the walk will not take, or a place inside a block: straight at it
            Leads.Lead.Kind.STEP_INTO -> if (ending != Ending.ARRIVED) {
                if (player.position().distanceTo(lead.position) > STEP_PUSH_RANGE) return false
                tickUntil { ticks ->
                    val towards = ViewRotation.fromVector(lead.position.subtract(player.position()))
                    agent.body.look(ViewRotation(towards.yRot, 0f), LookPurpose.MOVEMENT)
                    agent.press(Input(true, false, false, false, false, false, false), true)
                    ticks >= STEP_INTO_TICKS || !player.onGround() || player.position().distanceTo(lead.position) < STEP_REACH
                }
            }
            Leads.Lead.Kind.WALK_UP -> if (ending != Ending.ARRIVED) return false
        }
        return true
    }

    /**
     * Walks somewhere new when nothing is left to try, to find what the map hides further away.
     */
    private suspend fun wander(agent: Agent, player: Player) {
        val random = player.random
        val angle = random.nextDouble() * Math.PI * 2
        val distance = WANDER_MIN + random.nextDouble() * WANDER_MIN
        val goal = player.position().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance)
        play.show("Wandering", describe(goal))
        report("map wander to ${describe(goal)}")
        play.walk(agent, player, goal, WANDER_RADIUS)
    }

    private fun KnownMap.Point?.near(other: KnownMap.Point?) =
        this != null && other != null && toVec3().distanceTo(other.toVec3()) < SAME_PLACE

    private companion object {
        const val TICKS_PER_SECOND = 20
        val PROGRESS = setOf(Changes.Kind.TELEPORT, Changes.Kind.ARMOUR, Changes.Kind.EFFECT, Changes.Kind.GLIDE)
        val OFF = Regex("""(?i)\b(off|disabled)\b""")
        const val STEP_RANGE = 48.0
        const val NEAR_STEP = 16.0
        const val SAME_PLACE = 2.5
        const val MAX_FAILURES = 2
        const val REPLAY_RETRY_TICKS = 100
        const val MIN_EFFECT_TICKS = 15 * TICKS_PER_SECOND
        const val EFFECT_TICKS = 60
        const val IDLE_TICKS = 20
        const val RETRY_ALL_TICKS = 30 * TICKS_PER_SECOND
        const val USE_REACH = 2.0
        const val STEP_REACH = 0.7
        const val STEP_PUSH_RANGE = 6.0
        const val WALK_UP_REACH = 0.8
        const val INTERACT_REACH = 4.5
        const val LOOK_TICKS = 2
        const val STEP_INTO_TICKS = 60
        const val WANDER_MIN = 12.0
        const val WANDER_RADIUS = 3.0

        fun describe(point: KnownMap.Point) = "${point.x.toInt()},${point.y.toInt()},${point.z.toInt()}"
        fun describe(pos: Vec3) = "${pos.x.toInt()},${pos.y.toInt()},${pos.z.toInt()}"
    }
}
