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

package net.ccbluex.agent.v26_3.fabric.litematica

import net.fabricmc.loader.api.FabricLoader
import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.block.BlockState

/**
 * The blocks of Litematica's schematic placements, as a build takes them. Without Litematica there are none, and no
 * Litematica class is loaded.
 *
 * @agent claude-opus-5-5
 */
public object Placements {

    /**
     * Whether Litematica is installed.
     */
    @JvmStatic
    public val isAvailable: Boolean = FabricLoader.getInstance().isModLoaded("litematica")

    /**
     * The blocks of every enabled placement by position, air left out; empty without Litematica. Only the chunks
     * Litematica has loaded around the player count. Call it on the client thread.
     */
    @JvmStatic
    public fun blocks(): Map<BlockPos, BlockState> = if (isAvailable) schematicBlocks() else emptyMap()
}
