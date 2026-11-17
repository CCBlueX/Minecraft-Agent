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

package net.ccbluex.agent.skills.build

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.world.item.Item

/**
 * What [build] left undone: the positions still not as wanted, how many of each item it would have needed more, and
 * [isMissingScaffold] when some showed a face only from a spare block to stand on and none was carried.
 *
 * @agent claude-opus-5-5
 */
public class BuildResult(
    public val missing: Map<Item, Int>,
    public val unbuilt: Set<BlockPos>,
    public val isMissingScaffold: Boolean,
) {

    public val isDone: Boolean
        get() = unbuilt.isEmpty()
}
