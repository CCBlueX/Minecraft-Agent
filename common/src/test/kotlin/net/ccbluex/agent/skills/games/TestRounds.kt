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

package net.ccbluex.agent.skills.games

import com.mojang.authlib.GameProfile
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.pathfinder.TestLevel
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.entity.Player
import net.minecraft.world.level.GameType
import java.util.UUID
import net.minecraft.world.entity.player.Player as McPlayer

/**
 * Round facts a test sets by hand: playing, the agent called Me, nobody a friend or a bot.
 *
 * @agent claude-sonnet-5-5
 */
internal open class FakeRoundFacts : RoundFacts {
    override var state = RoundState.PLAYING
    override var countdownSeconds: Int? = null
    override var matchSeconds = 0
    override var ownName: String? = "Me"
    val friends = HashSet<String>()
    val bots = HashSet<String>()

    override fun isFriend(player: Player): Boolean = player.scoreboardName in friends

    override fun isBot(player: Player): Boolean = player.scoreboardName in bots
}

/**
 * A player called [name] standing at [feet], added to this level; [TestLevel.player] always names its players Tester.
 *
 * @agent claude-sonnet-5-5
 */
internal fun TestLevel.playerNamed(name: String, feet: Vec3, mode: GameType = GameType.SURVIVAL): McPlayer {
    val player = object : McPlayer(this, GameProfile(UUID.randomUUID(), name)) {
        override fun gameMode(): GameType = mode
    }
    player.snapTo(feet.toMinecraft(), 0f, 0f)
    player.setOnGround(true)
    add(player)
    return player
}

/**
 * Finds blocks by looking at every position from y = 0 to 4 within 40 blocks of the origin, as a [TestLevel] has no
 * chunks to walk.
 *
 * @agent claude-sonnet-5-5
 */
internal val BLOCKS_AROUND_ORIGIN = LoadedBlocks { level, _, test ->
    BlockPos.betweenClosed(BlockPos(-40, 0, -40), BlockPos(40, 4, 40)).mapNotNull { pos ->
        if (test.test(level.getBlockState(pos))) pos.immutable() else null
    }
}

/**
 * Calls [LevelRoundSenses.tick] often enough for one more read of the level.
 *
 * @agent claude-sonnet-5-5
 */
internal fun LevelRoundSenses.scanNow() {
    repeat(21) { tick() }
}
