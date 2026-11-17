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

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.agent.v26_3.fabric.litematica.Placements
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.gametest.harness.verdict.ClientErrors
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

private const val PLACEMENT_TICKS = 200

/**
 * With Litematica installed (`-PgameTestLitematica`), the agent builds the stepped pyramid from a Litematica placement
 * of it; without, the placements are empty and nothing of Litematica is needed to get there.
 *
 * @agent claude-opus-5-5
 */
internal val litematicaBuilding: GameTestScope.() -> Unit = {
    val centre = origin.offset(8, 0, 0)
    prepareSite(centre)
    if (Placements.isAvailable) {
        // Litematica logs "Waking up threads" as an ERROR while blocks of its placement change
        ClientErrors.ignore("Waking up threads")
        val blocks = steppedPyramid(centre)
        context.onClient { placeSchematic(blocks) }
        val took = await(context, PLACEMENT_TICKS, "Litematica did not show the placement") {
            context.fromClient { placementBlocks() }.keys == blocks.keys
        }
        logTicks("Showing the placement", took)
        buildAndCheck(context.fromClient { placementBlocks() }, canBreak = false)
        context.onClient { removeSchematics() }
    } else {
        check(context.fromClient { Placements.blocks() }.isEmpty()) { "Placements without Litematica" }
        logger.info("Litematica is not installed; its placements are empty")
    }
}

private fun placementBlocks(): Map<BlockPos, BlockState> =
    Placements.blocks().entries.associate { it.key.toMinecraft() to it.value.toMinecraft() }

/**
 * @agent claude-opus-5-5
 */
public class LitematicaBuildGameTest : ServerGameTest(litematicaBuilding)
