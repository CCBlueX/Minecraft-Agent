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

package net.ccbluex.agent.skills.games.blitz

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.skills.games.LevelRoundSenses
import net.ccbluex.agent.skills.games.LoadedBlocks
import net.ccbluex.agent.skills.games.RoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.loot.Chests
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.BlockKind
import net.ccbluex.agent.world.item.Items

/**
 * A Blitz round of a client: the timers from [LevelSenses.Facts], the chests of the loaded chunks added to [known]
 * (shared with the loot skill, which the host tells about refills), and the compass target from the spawn point the
 * server moves onto a player carrying a compass. The [ownSpawn] is the pedestal the agent stood on as the round
 * started.
 *
 * The host calls [tick] every client tick.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public class LevelSenses internal constructor(
    level: Level,
    private val facts: LevelSenses.Facts,
    private val known: Chests,
    private val blocks: LoadedBlocks,
) : LevelRoundSenses(level, facts), Senses {

    public constructor(level: Level, facts: Facts, chests: Chests) :
        this(level, facts, chests, LoadedBlocks.IN_CHUNKS)

    /**
     * What a client learns about a Blitz round from text: the kit and deathmatch timers. The level gives the chests
     * and the compass target.
     *
     * @agent claude-sonnet-5-5
     */
    public interface Facts : RoundFacts {

        /**
         * Seconds after the start the kit items arrive; null without a kit or before it is known.
         */
        public val kitDelaySeconds: Int?

        /**
         * Seconds until the deathmatch: before its teleport the time to the teleport, during the countdown after it
         * the time to the fight; null when not announced.
         */
        public val secondsToDeathmatch: Int?

        /**
         * Whether the server said the deathmatch fight began.
         */
        public val isDeathmatchStarted: Boolean
    }

    private var roundSpawn: BlockPos? = null

    override val chests: Collection<BlockPos>
        get() = known.all

    override val refills: Int
        get() = known.refills

    override val secondsToRefill: Int?
        get() = known.secondsToRefill

    override val kitDelaySeconds: Int?
        get() = facts.kitDelaySeconds

    override val secondsToDeathmatch: Int?
        get() = facts.secondsToDeathmatch

    override val isDeathmatchStarted: Boolean
        get() = facts.isDeathmatchStarted

    override var compassTarget: BlockPos? = null
        private set

    override fun scan() {
        val me = self ?: return
        blocks.find(level, me.blockPosition()) { it.`is`(BlockKind.CHEST) }.forEach(known::add)
        val spawn = level.respawnPos
        if (state != RoundState.PLAYING) {
            roundSpawn = spawn
            return
        }
        val carried = (0 until me.inventory.containerSize).any { me.inventory.getItem(it).`is`(Items.COMPASS) }
        compassTarget = spawn.takeIf { carried && it != roundSpawn }
    }
}
