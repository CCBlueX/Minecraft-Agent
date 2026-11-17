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

@file:OptIn(ExperimentalAgentApi::class)

package net.ccbluex.agent.v26_3.fabric.gametest

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.Agent
import net.ccbluex.agent.skills.danger.Dangers
import net.ccbluex.agent.skills.games.GameMode
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.summon
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.min

/**
 * A survival agent's reflexes alone: under the stone roof of a flooded room it swims to the one shaft of air before
 * it drowns, it backs away from a creeper, and it climbs out of powder snow.
 *
 * @agent claude-opus-5-5
 */
internal val dangers: GameTestScope.() -> Unit = {
    run("clear $playerName")
    run("gamerule minecraft:natural_health_regeneration false")
    // Peaceful removes every monster, the creeper included
    run("difficulty easy")
    lateinit var agent: TestAgent
    context.onClient { agent = TestAgent(SURVIVAL_OPTIONS) }
    val reflexes = Reflexes()
    // The checks of play() run on the client thread
    fun player(): LocalPlayer = checkNotNull(Minecraft.getInstance().player)
    try {
        // A room of water five blocks across under stone, with air only in a shaft above its far corner
        fill(BlockPos(9, -61, -3), BlockPos(15, -52, 3), "minecraft:stone")
        fill(BlockPos(10, -60, -2), BlockPos(14, -56, 2), "minecraft:water")
        fill(BlockPos(14, -55, 2), BlockPos(14, -53, 2), "minecraft:air")
        awaitServer("block 14 -53 2 minecraft:air")
        moveTo(Vec3(10.5, -60.0, -1.5))
        var air = Int.MAX_VALUE
        unhurt("Out of a flooded room") {
            agent.play(context, reflexes, 900, "Did not swim to air") {
                val player = player()
                air = min(air, player.airSupply)
                air < 300 && !player.isUnderWater
            }
        }
        check(air > 0) { "Ran out of air" }
        fill(BlockPos(9, -61, -3), BlockPos(15, -52, 3), "minecraft:air")
        fill(BlockPos(9, -61, -3), BlockPos(15, -61, 3), "minecraft:grass_block")

        moveTo(Vec3(0.5, -60.0, 0.5))
        summon("minecraft:creeper", BlockPos(3, -60, 0), "{NoAI:1b}")
        awaitServer("entity @e[type=minecraft:creeper,x=3,y=-60,z=0,distance=..2]")
        agent.play(context, reflexes, 200, "Did not back away from the creeper") {
            player().position().distanceTo(Vec3(3.5, -60.0, 0.5)) > 6.0
        }
        run("kill @e[type=minecraft:creeper]")

        fill(BlockPos(20, -60, -1), BlockPos(22, -59, 1), "minecraft:powder_snow")
        awaitServer("block 22 -59 1 minecraft:powder_snow")
        moveTo(Vec3(21.5, -60.0, 0.5))
        agent.play(context, reflexes, 300, "Did not climb out of the powder snow") {
            val player = player()
            !player.isInPowderSnow && player.onGround()
        }
    } finally {
        run("kill @e[type=minecraft:creeper]")
        fill(BlockPos(9, -61, -3), BlockPos(15, -52, 3), "minecraft:air")
        fill(BlockPos(9, -61, -3), BlockPos(15, -61, 3), "minecraft:grass_block")
        fill(BlockPos(20, -60, -1), BlockPos(22, -59, 1), "minecraft:air")
        run("gamerule minecraft:natural_health_regeneration true")
        run("difficulty peaceful")
    }
}

/**
 * A game mode that only ticks the [Dangers] reflexes.
 *
 * @agent claude-opus-5-5
 */
private class Reflexes : GameMode {

    private val dangers = Dangers()

    override val name: String = "Reflexes"

    override val phase: String = "Watching"

    override val goal: String? = null

    override fun tick(agent: Agent) {
        dangers.tick(agent, false)
    }
}
