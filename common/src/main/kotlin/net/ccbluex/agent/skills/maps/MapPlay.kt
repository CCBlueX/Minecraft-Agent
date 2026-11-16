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

import kotlinx.coroutines.launch
import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.navigation.Ending
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.navigation.Options as NavigationOptions
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.skills.AgentListener
import net.ccbluex.agent.skills.Skills
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.tick.TickScope
import net.ccbluex.agent.tick.tickUntil
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Plays a map by itself: tells a map it knows by its blocks and goes on with its [Story] from wherever the player
 * is, glides where the map drops the player into open space ([Glide]), answers the death screen, gets up from seats
 * and waits through scenes. What it learns goes into the known map at once, so a restart carries on with it.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public class MapPlay(
    private val maps: KnownMaps,
    private val texts: MapTexts,
    reporter: Consumer<String>,
    private val mapName: Supplier<String>,
) : GameMode {

    private val report: (String) -> Unit = reporter::accept

    override val name: String = "Map"

    override var phase: String = "Starting"
        private set

    override var goal: String? = null
        private set

    private var player: Player? = null
    private val scope = TickScope()
    private val changes = Changes()
    private val story = Story(this, maps, changes, report)
    private var map: KnownMap? = null
    private var started = false
    private var startTick = 0
    private var deaths = 0
    private var glides = 0
    private var isDone = false

    /**
     * Every block the gliding body touches, for the navigator: a collision, but for the touch-down where it lands.
     */
    internal val listener: Listener = object : Listener {
        override fun touched(pos: BlockPos, direction: Direction) {
            val glide = glide ?: return
            val at = Vec3.atCenterOf(pos)
            if (glide.landing?.let { it.distanceTo(at) < TOUCHDOWN } == true) return
            touches++
            map?.hazards?.add(KnownMap.Point.of(at))
            val kind = when (direction) {
                Direction.UP -> "ceiling"
                Direction.DOWN -> "floor"
                else -> "wall"
            }
            report("map collision $kind at ${describe(at)}")
        }
    }
    private var glide: Glide? = null
    private var touches = 0

    override fun tick(agent: Agent) {
        val player = agent.body.player
        this.player = player
        changes.tick(player)
        if (!started) {
            started = true
            scope.launch { play(agent) }
        }
        scope.tick()
    }

    /**
     * An agent that plays this map: no abilities, and nothing broken or placed, as maps keep players in adventure
     * mode.
     */
    @JvmOverloads
    public fun agent(body: AgentBody, listener: AgentListener = AgentListener.NONE): Agent {
        val navigator = Navigator(body, NavigationOptions(glideClearance = 1.0), this.listener)
        return Agent(body, navigator, Skills(), listener = listener).also { it.game = this }
    }

    override fun stop(agent: Agent) {
        scope.cancel()
    }

    /**
     * A line the server wrote in chat; the host hands in each one as it comes.
     */
    public fun chat(line: String) {
        changes.chat(line)
        if (!isDone && DONE.containsMatchIn(line)) {
            isDone = true
            val map = map ?: return
            map.complete = true
            maps.save(map)
            val seconds = (changes.ticks - startTick) / TICKS_PER_SECOND
            report("map done ${map.name} in ${seconds}s deaths=$deaths")
        }
    }

    /**
     * The server set the block at [pos] to [state]; the host hands in each such change as it comes.
     */
    public fun blockChanged(pos: BlockPos, state: BlockState) {
        val player = player ?: return
        changes.blockChanged(pos, state, player.position())
    }

    internal fun show(phase: String, goal: String?) {
        this.phase = phase
        this.goal = goal
    }

    /**
     * Walks to [goal] within [radius]; null when it took too long, the player died or took off.
     */
    internal suspend fun walk(agent: Agent, player: Player, goal: Vec3, radius: Double): Ending? {
        var ending: Ending? = null
        agent.navigator.moveTo(goal, radius) { ending = it }
        tickUntil { ticks -> ending != null || ticks >= WALK_TICKS || !player.isAlive || player.isFallFlying || isFalling(player) }
        if (ending == null) agent.navigator.stop()
        return ending
    }

    private suspend fun play(agent: Agent) {
        show("Looking around", null)
        // The chunks around the player come in over the first ticks
        tickUntil { it >= LOAD_TICKS || allLoaded(agent.body.player) }
        val first = agent.body.player
        report("map situation ${situation(first)}")
        recognise(first)
        startTick = changes.ticks
        while (true) {
            // A respawn makes a new player
            val player = agent.body.player
            when {
                !player.isAlive || player.isDeadOrDying -> respawn(agent, player)
                player.isFallFlying -> glide(agent, player)
                isFalling(player) -> openWings(agent, player)
                player.isPassenger -> leaveSeat(agent)
                else -> story.advance(agent, player)
            }
        }
    }

    private fun allLoaded(player: Player): Boolean {
        val chunk = player.chunkPosition()
        return (-2..2).all { dx -> (-2..2).all { dz -> player.level().hasChunk(chunk.x + dx, chunk.z + dz) } }
    }

    private fun recognise(player: Player) {
        val level = player.level()
        val found = maps.recognise { samples -> Fingerprint.similarity(samples) { x, y, z -> Fingerprint.blockAt(level, x, y, z) } }
        if (found != null) {
            map = found.map
            report("Recognised map ${found.map.name} (${found.map.id}), similarity ${"%.2f".format(found.similarity)}")
        } else {
            val name = mapName.get()
            val map = maps.create(name)
            map.samples += Fingerprint.sample(level, level.levelData.respawnData.pos())
            landmark(map, player)
            this.map = map
            report("Unknown map $name, learning it as ${map.id}")
        }
        story.map = map
    }

    /**
     * Samples around where the player is, so the map is told again from here too; once per place.
     */
    internal fun landmark(map: KnownMap, player: Player) {
        val here = player.blockPosition()
        if (map.samples.none { here.distManhattan(BlockPos(it.x, it.y, it.z)) < LANDMARK_SPACING }) {
            map.samples += Fingerprint.sample(player.level(), here)
            map.signs += Fingerprint.signs(player.level(), here, SIGN_RANGE).filter { it !in map.signs }
        }
    }

    private suspend fun respawn(agent: Agent, player: Player) {
        show("Respawning", null)
        deaths++
        report("map died at ${describe(player.position())} deaths=$deaths, respawning")
        agent.body.respawn()
        tickUntil { agent.body.player.let { it.isAlive && !it.isDeadOrDying } || it >= RESPAWN_TICKS }
    }

    internal fun isFalling(player: Player): Boolean = !player.onGround() && player.deltaMovement.y < FALL_SPEED &&
        player.fallDistance > FALL_DISTANCE && player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)

    /**
     * Jump presses mid-air start the glide, one tick down and one up.
     */
    private suspend fun openWings(agent: Agent, player: Player) {
        show("Opening wings", null)
        report("map opening wings at ${describe(player.position())}")
        tickUntil { ticks ->
            if (ticks % 2 == 1) agent.press(Input(false, false, false, false, true, false, false), true)
            player.isFallFlying || player.onGround() || !player.isAlive || ticks >= WINGS_TICKS
        }
    }

    private suspend fun glide(agent: Agent, player: Player) {
        val map = map
        show("Gliding", null)
        // Dropping into open space is the story moving on
        story.commitIfProgressed(player)
        val start = player.position()
        if (glides++ == 0) {
            val seconds = (changes.ticks - startTick) / TICKS_PER_SECOND.toDouble()
            report("map entry done in ${"%.1f".format(seconds)}s")
            map?.let { if (seconds < (it.best["entry"] ?: Double.MAX_VALUE)) it.best["entry"] = seconds }
        }
        val course = map?.courses?.filter { it.start.toVec3().distanceTo(start) < COURSE_RANGE }
            ?.minByOrNull { it.start.toVec3().distanceTo(start) }
        report("map course start at ${describe(start)} known=${course != null}")
        val deathsBefore = deaths
        val flight = Glide(changes)
        glide = flight
        touches = 0
        try {
            flight.fly(agent, course)
        } finally {
            glide = null
        }
        // The map kills a player that touched the ground in its course a moment later
        tickUntil { it >= JUDGE_TICKS || agent.body.player !== player || !player.isAlive }
        val seconds = flight.ticks / TICKS_PER_SECOND.toDouble()
        val end = player.position()
        val landed = agent.body.player === player && player.isAlive && !player.isDeadOrDying
        report("map course ${if (landed) "landed" else "ended"} at ${describe(end)} after ${"%.1f".format(seconds)}s touches=$touches")
        if (map == null || !landed || deaths != deathsBefore || flight.points.size < MIN_COURSE_POINTS) return
        map.asks += KnownMap.ASK_GLIDE
        val known = map.courses.indexOfFirst { it.start.toVec3().distanceTo(start) < COURSE_RANGE }
        if (known < 0 || map.courses[known].seconds > seconds) {
            val learnt = KnownMap.Course(KnownMap.Point.of(start), KnownMap.Point.of(end), flight.points.toList(), seconds, deaths - deathsBefore)
            if (known < 0) map.courses += learnt else map.courses[known] = learnt
            map.best["course ${map.courses.indexOf(learnt) + 1}"] = seconds
        }
        maps.save(map)
    }

    private suspend fun leaveSeat(agent: Agent) {
        show("Getting up", null)
        report("map getting up from a seat")
        tickUntil { ticks ->
            agent.press(Input(false, false, false, false, false, ticks % 4 < 2, false), true)
            ticks >= SEAT_TICKS
        }
    }

    private fun situation(player: Player) = "pos=${describe(player.position())} title=${texts.shownTitle ?: "none"} " +
        "chat=${texts.chat().size} dead=${player.isDeadOrDying} gliding=${player.isFallFlying}"

    private fun describe(pos: Vec3) = "${pos.x.toInt()},${pos.y.toInt()},${pos.z.toInt()}"

    private companion object {
        const val TICKS_PER_SECOND = 20
        const val LOAD_TICKS = 100
        const val RESPAWN_TICKS = 100
        const val WINGS_TICKS = 40
        const val SEAT_TICKS = 10
        const val WALK_TICKS = 30 * TICKS_PER_SECOND
        const val FALL_SPEED = -0.3
        const val FALL_DISTANCE = 2.0
        const val LANDMARK_SPACING = 48
        const val SIGN_RANGE = 24
        const val COURSE_RANGE = 48.0
        const val MIN_COURSE_POINTS = 5
        const val JUDGE_TICKS = 10
        const val TOUCHDOWN = 3.0
        val DONE = Regex("""(?i)thank(s| you) for playing|you (have )?(completed|finished) the map""")
    }
}
