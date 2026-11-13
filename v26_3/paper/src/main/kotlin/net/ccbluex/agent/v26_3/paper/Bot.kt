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

package net.ccbluex.agent.v26_3.paper

import java.util.EnumMap
import java.util.Random
import net.ccbluex.agent.navigation.Listener
import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.bukkit.AgentListeners
import net.ccbluex.agent.bukkit.BotSkin
import net.ccbluex.agent.bukkit.NavigationListeners
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.AgentListener
import net.ccbluex.agent.skills.Personality
import net.ccbluex.agent.skills.Skills
import net.ccbluex.agent.skills.games.GameMode
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * A bot: its name, home and settings, and the body [Bots] spawns for it at home, again after a death or an unloaded
 * chunk. While it is spawned, its [navigator] walks the body to the goal given by [moveTo] or [follow], and its
 * [agent] plays the game given by [play] or fights the target given by [fight].
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class Bot internal constructor(
    public val name: String,
    home: Location,
    settings: BotSettings,
    skin: BotSkin?,
    private val bots: Bots,
) {

    private var homeLocation = home.clone()

    /**
     * Where the body spawns.
     */
    public var home: Location
        get() = homeLocation.clone()
        set(value) {
            homeLocation = value.clone()
        }

    /**
     * A change applies to the spawned body right away; difficulty and personality to the next [play] or body.
     */
    public var settings: BotSettings = settings
        set(value) {
            field = value
            navigator?.options = value.navigation
            body?.let { apply(it, value) }
        }

    /**
     * The skin of the next body.
     */
    public var skin: BotSkin? = skin

    /**
     * Equipment put on every new body.
     */
    public val equipment: MutableMap<EquipmentSlot, ItemStack> = EnumMap(EquipmentSlot::class.java)

    /**
     * The spawned body, null while there is none.
     */
    public var body: PaperBody? = null
        private set

    /**
     * Walks the spawned body; a new one comes with every body, null while there is none.
     */
    public var navigator: Navigator? = null
        private set

    /**
     * Plays and fights with the spawned body; a new one comes with every body, null while there is none.
     */
    public var agent: Agent? = null
        private set

    /**
     * The game the bot plays, null while it plays none.
     */
    public val game: GameMode?
        get() = agent?.game

    /**
     * The body as a Bukkit player, null while there is none.
     */
    public val player: Player?
        get() = body?.entity

    public val isSpawned: Boolean
        get() = body?.entity?.isValid == true

    /**
     * Whether the body died and stays dead, as its settings keep it from coming back; [respawn] brings it back.
     */
    public var isDead: Boolean = false
        internal set

    private var goal: Destination? = null
    private var level: Level? = null
    private var engaged: LivingEntity? = null
    private var acting = false
    private val navigationListeners = NavigationListeners()
    private val agentListeners = AgentListeners()

    /**
     * Walks to [location] until it is within [radius], and stays there until another goal or [stop].
     */
    public fun moveTo(location: Location, radius: Double) {
        goal = Destination.Point(location.clone(), radius)
    }

    /**
     * Walks after [entity], to within [radius], until it is gone or [stop].
     */
    @JvmOverloads
    public fun follow(entity: Entity, radius: Double = FOLLOW_RADIUS) {
        goal = Destination.Following(entity, radius)
    }

    /**
     * Fights [target] with what the body holds until it is dead or gone, or [stop]; walking waits meanwhile.
     */
    public fun fight(target: LivingEntity) {
        val agent = checkNotNull(agent) { "Bot $name has no body" }
        val fight = checkNotNull(agent.skills.fight) { "Bot $name fights with no fight ability" }
        engaged = target
        fight.engage((target as CraftLivingEntity).handle)
    }

    /**
     * Plays [game] with the body, with the built-in abilities at the settings' difficulty, until [stopPlaying]. The
     * game ends with the body: a new body plays nothing.
     */
    public fun play(game: GameMode) {
        play(game, null)
    }

    /**
     * Plays [game] with [skills], built for the spawned [body], or the built-in abilities when null. The game and
     * the skills end with the body.
     */
    public fun play(game: GameMode, skills: Skills?) {
        val body = checkNotNull(body) { "Bot $name has no body" }
        val navigator = checkNotNull(navigator)
        val old = agent
        goal = null
        old?.stop()
        val agent = if (skills == null && old != null) old else newAgent(body, navigator, skills, old?.personality)
        this.agent = agent
        agent.game = game
    }

    /**
     * Stops the game and lets go of every key; the bot stays where it is.
     */
    public fun stopPlaying() {
        val agent = agent ?: return
        agent.game = null
        settle(agent)
    }

    /**
     * Stops walking, fighting and playing, and lets go of every key.
     */
    public fun stop() {
        goal = null
        engaged = null
        agent?.let {
            it.game = null
            settle(it)
        }
        navigator?.stop()
        body?.let(::releaseKeys)
    }

    /**
     * Takes the body away and spawns a new one at home with the current settings and skin.
     */
    public fun respawn() {
        bots.respawn(this)
    }

    /**
     * Takes the body away and forgets the bot.
     */
    public fun remove() {
        bots.remove(this)
    }

    /**
     * Hears what the navigator of every body of this bot reports.
     */
    public fun addListener(listener: Listener) {
        navigationListeners.add(listener)
    }

    public fun removeListener(listener: Listener) {
        navigationListeners.remove(listener)
    }

    /**
     * Hears what the agent of every body of this bot decides and does.
     */
    public fun addAgentListener(listener: AgentListener) {
        agentListeners.add(listener)
    }

    public fun removeAgentListener(listener: AgentListener) {
        agentListeners.remove(listener)
    }

    internal fun attach(body: PaperBody) {
        this.body = body
        val navigator = Navigator(body, settings.navigation, navigationListeners)
        this.navigator = navigator
        agent = newAgent(body, navigator, null, null)
        level = body.level
        apply(body, settings)
    }

    internal fun detach(): PaperBody? {
        agent?.let {
            it.game = null
            it.stop()
        }
        agent = null
        acting = false
        engaged = null
        navigator?.stop()
        navigator = null
        val body = body
        this.body = null
        return body
    }

    /**
     * The body took a hit; the agent hears of it while it plays or fights.
     */
    internal fun damaged(attacker: net.minecraft.world.entity.Entity?) {
        if (acting) agent?.damaged(attacker)
    }

    internal fun tick() {
        val body = body ?: return
        val navigator = navigator ?: return
        val agent = agent ?: return
        body.turn()
        if (body.level !== level) {
            level = body.level
            navigator.levelChanged()
        }
        val active = isActing(agent)
        if (acting && !active) settle(agent)
        acting = active
        when {
            active -> {
                // Keys last one tick, as a client's do: whatever the agent no longer presses is let go
                releaseKeys(body)
                agent.tick()
            }
            else -> walk(body, navigator)
        }
        body.drainTouched { navigator.blockConfirmed(it, body.level.getBlockState(it)) }
    }

    private fun walk(body: PaperBody, navigator: Navigator) {
        val target = goalPosition(body)
        if (target != null) {
            navigator.navigate(target, goal?.radius ?: 0.0)
        } else if (goal != null) {
            navigator.halt()
            releaseKeys(body)
        }
    }

    private fun isActing(agent: Agent): Boolean {
        val target = engaged
        if (target != null && (!target.isValid || target.isDead)) engaged = null
        return agent.game != null || engaged != null || agent.skills.fight?.target != null
    }

    // The agent leaves keys, the held slot and an item in use behind when it stops
    private fun settle(agent: Agent) {
        agent.stop()
        acting = false
        engaged = null
        body?.let(::releaseKeys)
    }

    private fun newAgent(body: PaperBody, navigator: Navigator, skills: Skills?, personality: Personality?): Agent {
        val settings = settings
        val leaning = personality ?: settings.personality ?: Personality.roll(RANDOM)
        val abilities = skills ?: Skills.builtIn(body, settings.skillLevel, leaning)
        return Agent(body, navigator, abilities, leaning, settings.skillLevel, agentListeners)
    }

    /**
     * Where the goal is now, null while it is in another world; a followed entity that is gone ends the walk.
     */
    private fun goalPosition(body: PaperBody): Vec3? {
        val world = body.entity.world
        return when (val goal = goal) {
            null -> null
            is Destination.Point -> goal.location.takeIf { it.isWorldLoaded && it.world == world }?.let(::position)
            is Destination.Following -> when {
                !goal.entity.isValid -> {
                    stop()
                    null
                }
                goal.entity.world == world -> position(goal.entity.location)
                else -> null
            }
        }
    }

    // Navigator.halt keeps the keys of the last walk
    private fun releaseKeys(body: PaperBody) {
        body.press(Input.EMPTY)
        body.isShiftKeyDown = settings.sneak
    }

    private fun apply(body: PaperBody, settings: BotSettings) {
        body.immortal = settings.immortal
        body.knockback = settings.knockback
        if (body.anchored != settings.anchored) body.anchored = settings.anchored
        body.isShiftKeyDown = settings.sneak
        body.isSprinting = settings.sprint
        body.turnSpeed = settings.turnSpeed
    }

    override fun toString(): String = "Bot($name)"

    private sealed interface Destination {
        val radius: Double

        class Point(val location: Location, override val radius: Double) : Destination
        class Following(val entity: Entity, override val radius: Double) : Destination
    }

    private companion object {
        const val FOLLOW_RADIUS = 2.0
        val RANDOM = Random()

        fun position(location: Location): Vec3 = Vec3(location.x, location.y, location.z)
    }
}
