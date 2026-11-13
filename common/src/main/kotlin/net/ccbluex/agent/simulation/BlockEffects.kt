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

package net.ccbluex.agent.simulation

import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity.DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2
import net.minecraft.world.entity.Entity.DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.SlimeBlock
import net.minecraft.world.level.block.WallBlock

// The blocks around the feet of a PlayerMotion that change how it moves.

/**
 * Block.fallOn: the part of a fall onto the block landed on that hurts; none onto slime.
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L395-L398)
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.fallScale(): Double {
    val block = onPosLegacy()
    return if (block is SlimeBlock) 0.0 else 1.0 - block.fallDistanceReduction
}

/**
 * Entity.getOnPosLegacy: the block a landing falls on and the feet step on.
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L401-L412)
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.onPosLegacy(): Block {
    val below = Mth.floor(y - DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2)
    if (!hasSupport) {
        return terrain.state(Mth.floor(x), below, Mth.floor(z)).block
    }

    val support = terrain.state(supportX, supportY, supportZ).block
    if (support is FenceBlock || support is WallBlock || support is FenceGateBlock) {
        return support
    }
    return terrain.state(supportX, below, supportZ).block
}

/**
 * Entity.getBlockPosBelowThatAffectsMyMovement
 *
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L415-L426)
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.movementBlock(): Block {
    val below = Mth.floor(y - DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5)
    if (!hasSupport) {
        return terrain.state(Mth.floor(x), below, Mth.floor(z)).block
    }

    val support = terrain.state(supportX, supportY, supportZ).block
    if (support is WallBlock || support is FenceGateBlock) {
        return support
    }
    return terrain.state(supportX, below, supportZ).block
}

/**
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L428-L431)
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.jumpFactor(): Float {
    val here = terrain.state(Mth.floor(x), Mth.floor(y), Mth.floor(z)).block.jumpFactor
    return if (here == 1f) movementBlock().jumpFactor else here
}

/**
 * From LiquidBounce: [PlayerMotion.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerMotion.kt#L433-L440)
 *
 * @agent claude-opus-5-5
 */
internal fun PlayerMotion.speedFactor(): Double {
    val here = terrain.state(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    val factor = here.block.speedFactor
    if (factor != 1f || !here.fluidState.isEmpty) {
        return factor.toDouble()
    }
    return movementBlock().speedFactor.toDouble()
}
