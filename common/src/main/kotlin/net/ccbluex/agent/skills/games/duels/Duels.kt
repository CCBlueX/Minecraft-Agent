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

package net.ccbluex.agent.skills.games.duels

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.fight.Rules
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.games.TickScope
import net.ccbluex.agent.skills.gear.GearScore
import net.ccbluex.agent.skills.hold
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A one-on-one duel against [Senses.opponent]: still and facing the opponent
 * with the weapon out through the countdown, then the fight ability on them until the duel ends. Building the arena,
 * the items and the duel's own rules stay with the host. With [adaptive] the result moves the opponent's next
 * difficulty in [modeKey], the host's name for what was played, such as a kit.
 *
 * @agent claude-opus-5-5
 */
@ExperimentalAgentApi
public class Duels @JvmOverloads constructor(
    override val senses: Senses,
    private val adaptive: AdaptiveSkillLevel? = null,
    private val modeKey: String = senses.mode.name,
) : GameMode {

    private val scope = TickScope(LoggerFactory.getLogger("Agent/Duels"))
    private var opponentId: UUID? = null
    private var started = false

    override val name: String
        get() = "Duels"

    override var phase: String = "WAITING"
        private set

    override var goal: String? = null
        private set

    override fun tick(agent: Agent) {
        if (!started) {
            started = true
            scope.start { play(agent) }
        }
        scope.tick()
    }

    override fun stop(agent: Agent) {
        scope.cancel()
        agent.skills.fight?.disengage()
    }

    private suspend fun play(agent: Agent) {
        enter(agent, "WAITING")
        while (senses.state == RoundState.LOBBY) scope.nextTick()

        enter(agent, "COUNTDOWN")
        while (senses.state == RoundState.COUNTDOWN) {
            standReady(agent)
            scope.nextTick()
        }

        enter(agent, "FIGHTING")
        while (senses.state == RoundState.PLAYING) {
            fight(agent)
            scope.nextTick()
        }

        enter(agent, "ENDED")
        finish(agent)
    }

    /**
     * Holds still facing the opponent with the weapon out, as the countdown keeps the bot anchored.
     */
    private fun standReady(agent: Agent) {
        agent.takeMovement()
        val opponent = senses.opponent ?: return
        goal = "Facing ${opponent.scoreboardName}"
        holdWeapon(agent)
        val toward = opponent.eyePosition.subtract(agent.body.player.eyePosition)
        agent.body.look(ViewRotation.fromVector(toward), LookPurpose.AIM)
    }

    private fun fight(agent: Agent) {
        val fight = agent.skills.fight
        val opponent = senses.opponent?.takeIf(LivingEntity::isAlive)
        if (opponent == null) {
            goal = "Waiting for the opponent"
            fight?.disengage()
            return
        }
        if (opponentId == null) agent.listener.engaged(opponent)
        opponentId = opponent.uuid
        goal = "Fighting ${opponent.scoreboardName}"
        fight?.engage(opponent)
    }

    private fun finish(agent: Agent) {
        agent.skills.fight?.disengage()
        val result = senses.result ?: Outcome.DRAW
        goal = null
        agent.listener.roundEnded(result == Outcome.WON)
        val opponent = opponentId ?: return
        adaptive?.record(opponent, modeKey, agent.skillLevel, result)
    }

    private fun enter(agent: Agent, next: String) {
        phase = next
        agent.listener.phase(next)
    }

    private fun holdWeapon(agent: Agent) {
        val inventory = agent.body.player.inventory
        var best = -1
        var bestScore = 0.0
        for (slot in 0 until inventory.containerSize) {
            val score = GearScore.weaponScore(inventory.getItem(slot))
            if (score > bestScore) {
                best = slot
                bestScore = score
            }
        }
        if (best >= 0) agent.body.hold(best)
    }

    public companion object {

        /**
         * The fight rules [senses]' mode plays by on top of [base]: no crits where hits only push, no hits at all in
         * TNT run, the sumo ring to stay in.
         */
        @JvmStatic
        @JvmOverloads
        public fun fightRules(senses: Senses, base: Rules = Rules.DEFAULT): Rules =
            when (senses.mode) {
                Mode.SUMO -> base.copy(crits = false, ringCentre = senses.battleArea?.center)
                Mode.BOXING, Mode.SPLEEF, Mode.TNT_TAG -> base.copy(crits = false)
                Mode.TNT_RUN -> base.copy(crits = false, attacks = false)
                Mode.CLASSIC, Mode.FLOOR_IS_LAVA, Mode.PILLAR_OF_FORTUNE -> base
            }
    }
}
