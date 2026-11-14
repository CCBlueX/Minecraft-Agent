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

package net.ccbluex.agent.skills

import net.ccbluex.agent.navigation.Navigator
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.skills.games.RoundSenses
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * One bot that plays. Its host ticks it once per game tick, after the body's own tick input: the game mode first,
 * then the abilities, then the navigator once: with the walk goal the tick ended with, the keys an ability pressed,
 * or else the walk the navigator holds. Every key goes through the void rescue's guard.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L340-L355), [GameBotSkyWars.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotSkyWars.kt#L265-L297)
 *
 * @agent claude-opus-5-5
 */
public class Agent @JvmOverloads constructor(
    public val body: AgentBody,
    public val navigator: Navigator,
    public val skills: Skills,
    public val personality: Personality = Personality.roll(java.util.Random()),
    public val skillLevel: SkillLevel = SkillLevel.NORMAL,
    public val listener: AgentListener = AgentListener.NONE,
) {

    /**
     * What it plays; null runs only the abilities on what they are told.
     */
    public var game: GameMode? = null
        set(value) {
            if (field !== value) field?.stop(this)
            field = value
        }

    /**
     * The round facts of the game it plays, if any.
     */
    public val senses: RoundSenses?
        get() = game?.senses

    internal val logger: Logger = LoggerFactory.getLogger("Agent/" + body.player.scoreboardName)

    /**
     * Ticks since the agent was made.
     */
    public var ticks: Long = 0
        private set

    private var walkGoal: Vec3? = null
    private var walkRadius = 0.0
    private var movementTaken = false
    private var pressed: Input? = null
    private var pressedExact = false
    private val guard: (Input) -> Input = { skills.voidRescue?.guard(this, it) ?: it }

    /**
     * Walks towards [goal] this tick, within [radius]. The last call in a tick wins; abilities tick after the game
     * mode, so their walks go first.
     */
    public fun walkTo(goal: Vec3, radius: Double) {
        walkGoal = goal
        walkRadius = radius
    }

    /**
     * Something pressed the keys itself this tick, so the navigator only holds still.
     */
    public fun takeMovement() {
        movementTaken = true
    }

    /**
     * An ability presses [input] this tick instead of walking; the void rescue still holds it back at an edge.
     * [exact] as in [net.ccbluex.agent.navigation.Body.press].
     */
    internal fun press(input: Input, exact: Boolean) {
        movementTaken = true
        pressed = input
        pressedExact = exact
    }

    public fun tick() {
        ticks++
        walkGoal = null
        movementTaken = false
        pressed = null

        game?.tick(this)
        skills.voidRescue?.tick(this)
        // A fall into the void owns the hotbar and the keys: eating or swapping weapons then drops the blocks
        if (skills.voidRescue?.isRescuing != true) {
            skills.eat?.tick(this)
            skills.gear?.tick(this)
            skills.shop?.tick(this)
            skills.loot?.tick(this)
            skills.fight?.tick(this)
        }

        val goal = walkGoal
        val pressed = pressed
        navigator.guard = guard
        try {
            when {
                movementTaken -> if (pressed != null) navigator.press(pressed, pressedExact) else navigator.halt()
                goal != null -> navigator.navigate(goal, walkRadius)
                else -> navigator.tick()
            }
        } finally {
            navigator.guard = null
        }
        idleKeys(navigator.input, guard)?.let { body.press(it, true) }
    }

    /**
     * Hosts call it on every hit the body takes; [attacker] is null for falls, fire and the like. Servers older than
     * 1.19.4 name no source for any hit, so without one an opponent that has just swung at the agent counts as it.
     */
    public fun damaged(attacker: Entity?) {
        val source = attacker ?: swingingOpponent()
        game?.damaged(this, source)
        skills.fight?.damaged(this, source)
    }

    private fun swingingOpponent(): Player? {
        val player = body.player
        val senses = senses
        val opponents = body.players().filter { it !== player && senses?.isOpponent(it) != false }
        return likelyAttacker(opponents, { it.distanceTo(player).toDouble() }) {
            if (it.isSwinging) it.getSwingAnimation(1f) else null
        }
    }

    public fun stop() {
        game?.stop(this)
        skills.fight?.disengage()
        if (body.isUsingItem) body.releaseUsingItem()
        if (body.containerMenu != null) body.closeContainer()
        body.releaseHotbarSlot()
        navigator.stop()
    }

    internal fun decide(label: String, score: Double, reason: String) {
        logger.debug("{} ({}): {}", label, score, reason)
        listener.decision(label, score, reason)
    }
}

/**
 * The keys to press on a tick that pressed none, from the void rescue's [guard], or null for none: a player let go of
 * at an edge, such as when a plan changes, slides on with its momentum, and the navigator guards only the keys it
 * presses.
 *
 * @agent claude-sonnet-5-5
 */
internal fun idleKeys(last: Input, guard: (Input) -> Input): Input? {
    if (last != Input.EMPTY) return null
    return guard(Input.EMPTY).takeIf { it != Input.EMPTY }
}

/**
 * A server takes hits from up to this far, 6 blocks on 1.8.
 */
private const val HIT_REACH = 6.0

/**
 * How far into its swing an arm is that hit just now: a 1.8 client swings before it attacks.
 */
private const val FRESH_SWING = 0.5f

/**
 * Which of [players] hit the agent, by their [distance] and how far into a [swing] their arm is (null without one):
 * the nearest within [HIT_REACH] early in a swing, or null.
 *
 * @agent claude-opus-5-5
 */
internal fun <T> likelyAttacker(players: List<T>, distance: (T) -> Double, swing: (T) -> Float?): T? =
    players
        .filter { other -> distance(other) <= HIT_REACH && swing(other)?.let { it <= FRESH_SWING } == true }
        .minByOrNull(distance)
