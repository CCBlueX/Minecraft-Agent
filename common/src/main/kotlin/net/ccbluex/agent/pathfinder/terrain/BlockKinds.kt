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

package net.ccbluex.agent.pathfinder.terrain

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * Blocks that hurt or trap a player passing through, besides lava and fire.
 *
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L398-L473)
 *
 * @agent claude-opus-5-5
 */
internal val HAZARDS = ReferenceOpenHashSet<Block>(
    listOf(
        Blocks.COBWEB,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.POWDER_SNOW,
        Blocks.WITHER_ROSE,
        Blocks.MAGMA_BLOCK,
        Blocks.CAMPFIRE,
        Blocks.SOUL_CAMPFIRE,
        Blocks.CACTUS,
    ),
)

/**
 * From LiquidBounce: [Terrain.kt](https://github.com/CCBlueX/LiquidBounce/blob/d240e1012aaaca32895a341eab69595685510175/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/terrain/Terrain.kt#L398-L473)
 *
 * @agent claude-opus-5-5
 */
internal val CLIMBABLE = ReferenceOpenHashSet<Block>(
    listOf(
        Blocks.LADDER,
        Blocks.VINE,
        Blocks.WEEPING_VINES,
        Blocks.WEEPING_VINES_PLANT,
        Blocks.TWISTING_VINES,
        Blocks.TWISTING_VINES_PLANT,
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT,
    ),
)
