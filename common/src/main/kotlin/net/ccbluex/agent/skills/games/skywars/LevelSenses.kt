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

package net.ccbluex.agent.skills.games.skywars

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.skills.games.LevelRoundSenses
import net.ccbluex.agent.skills.games.LoadedBlocks
import net.ccbluex.agent.skills.games.RoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.loot.Chests
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.block.BlockKind

/**
 * A Sky Wars round of a client. The chests are those of the loaded chunks, added to [known]; the host shares that
 * [Chests] with the loot skill and tells it about refills. Doom is the world border closing in. The client knows no
 * cages, middle or own spawn; the game mode finds its cage from the blocks around it.
 *
 * The host calls [tick] every client tick.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public class LevelSenses internal constructor(
    level: Level,
    facts: RoundFacts,
    private val known: Chests,
    private val blocks: LoadedBlocks,
) : LevelRoundSenses(level, facts), Senses {

    public constructor(level: Level, facts: RoundFacts, chests: Chests) :
        this(level, facts, chests, LoadedBlocks.IN_CHUNKS)

    override val cages: List<Vec3>
        get() = emptyList()

    override val middle: Vec3?
        get() = null

    override val ownSpawn: Vec3?
        get() = null

    override val chests: Collection<BlockPos>
        get() = known.all

    override val refills: Int
        get() = known.refills

    override val secondsToRefill: Int?
        get() = known.secondsToRefill

    override var isDoom: Boolean = false
        private set

    override fun scan() {
        val me = self ?: return
        blocks.find(level, me.blockPosition()) { it.`is`(BlockKind.CHEST) }.forEach(known::add)
        isDoom = isDoom || state == RoundState.PLAYING && level.worldBorder.isShrinking
    }
}
