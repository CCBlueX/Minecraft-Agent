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

import net.ccbluex.agent.pathfinder.terrain.Terrain
import net.ccbluex.agent.simulation.MotionTraits
import net.ccbluex.agent.simulation.PlayerMotion
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.world.Input
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

/**
 * The motion works out its turns with a sine table.
 */
private const val TOLERANCE = 1.0E-4

private const val ARRIVAL_TICKS = 200
private const val SETTLE_TICKS = 10

/**
 * [PlayerMotion], which route searches plan jumps with, has to move like the game moves the player on land. It is
 * held against [SimulatedPlayer], which is the game's own movement, with the same keys from the same spot.
 *
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L42-L59)
 *
 * @agent claude-opus-5-5
 */
internal val playerMotion: GameTestScope.() -> Unit = {
    val y = origin.y
    run("gamemode survival $playerName")

    val failures = courses.mapNotNull { course ->
        run("fill 0 $y -4 40 ${y + 9} 4 air")
        run("fill 0 ${y - 1} -4 40 ${y - 1} 4 stone")
        course.build(y).forEach { run(it) }
        val start = Vec3(2.5, (y + course.height).toDouble(), 0.5)
        run("tp $playerName ${start.x} ${start.y} ${start.z} -90 0")
        awaitClient("the player at $start", ARRIVAL_TICKS) { it.player?.position()?.near(start) == true }
        ticks(SETTLE_TICKS)

        client { client -> course.difference(checkNotNull(client.player)) }
    }

    check(failures.isEmpty()) { "PlayerMotion moves unlike the game:\n" + failures.joinToString("\n") }
}

/**
 * @agent claude-opus-5-5
 */
public class PlayerMotionGameTest : ServerGameTest(playerMotion)

/**
 * The player starts [height] above the ground at x 2.5, looking down the x axis, with the keys held from then on.
 *
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L64-L92)
 *
 * @agent claude-fable-5-2
 */
private class Course(
    val name: String,
    val forward: Int = 1,
    val strafe: Int = 0,
    val jump: Boolean = false,
    val sprint: Boolean = false,
    val sneak: Boolean = false,
    val ticks: Int = 40,
    val height: Int = 0,
    val build: (Int) -> List<String> = { emptyList() },
) {
    /**
     * The first tick the two are apart at, if any.
     *
     * @agent claude-fable-5-2
     * @agent claude-opus-5-5
     */
    fun difference(player: LocalPlayer): String? {
        val motion = PlayerMotion(Terrain.live(player.level().toAgent()), MotionTraits.capture(player.toAgent()))
        motion.reset(player.toAgent())
        val keys = Input(forward > 0, forward < 0, strafe > 0, strafe < 0, jump, sneak, sprint)
        val game = SimulatedPlayer.fromPlayer(player, SimulatedInput(keys), continuesTick = false)

        repeat(ticks) { tick ->
            motion.tick(player.yRot, forward, strafe, jump, sprint, sneak)
            game.tick()
            val gamePosition = game.position().toAgent()
            if (motion.position.distanceTo(gamePosition) > TOLERANCE || motion.onGround != game.onGround()) {
                return "$name: tick $tick is ${motion.position} on ground ${motion.onGround}, " +
                    "the game moves to ${game.position()} on ground ${game.onGround()}"
            }
        }
        return null
    }
}

/**
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L95-L95)
 *
 * @agent claude-opus-5-5
 */
private fun platform(y: Int) = listOf("fill 0 $y -1 5 ${y + 2} 1 stone")

/**
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L97-L97)
 *
 * @agent claude-opus-5-5
 */
private fun floor(block: String) = { y: Int -> listOf("fill 4 ${y - 1} -1 30 ${y - 1} 1 $block") }

/**
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L99-L100)
 *
 * @agent claude-opus-5-5
 */
private fun slimeBelowTower(y: Int) =
    listOf("fill 0 $y -1 5 ${y + 5} 1 stone", "fill 6 ${y - 1} -4 40 ${y - 1} 4 slime_block")

/**
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L102-L102)
 *
 * @agent claude-opus-5-5
 */
private fun carpetOnSlimeBelowTower(y: Int) = slimeBelowTower(y) + "fill 6 $y -4 40 $y 4 red_carpet"

/**
 * A tower like [slimeBelowTower] with rows of beds east of it, head to the east.
 *
 * From LiquidBounce: [PlayerMotionGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/navigation/PlayerMotionGameTest.kt#L105-L171)
 *
 * @agent claude-opus-5-5
 */
private fun bedsBelowTower(y: Int) = listOf("fill 0 $y -1 5 ${y + 5} 1 stone") +
    (6..38 step 2).flatMap { x ->
        listOf(
            "fill $x $y -4 $x $y 4 red_bed[part=foot,facing=east]",
            "fill ${x + 1} $y -4 ${x + 1} $y 4 red_bed[part=head,facing=east]",
        )
    }

private val courses = listOf(
    Course("walking"),
    Course("sprinting", sprint = true),
    Course("sprint jumping", jump = true, sprint = true, ticks = 60),
    Course("walking diagonally", strafe = 1, ticks = 30),
    Course("walking backwards to the right", forward = -1, strafe = -1, ticks = 30),
    Course("sneaking", sneak = true, ticks = 20),
    Course("sneaking up to a ledge", sneak = true, ticks = 60, height = 3, build = ::platform),
    Course("sneaking into a corner", strafe = 1, sneak = true, ticks = 60, height = 3, build = ::platform),
    Course("walking off a ledge", height = 3, build = ::platform),
    Course("sprint jumping off a ledge", jump = true, sprint = true, height = 3, build = ::platform),
    Course("sprint jumping over a gap", jump = true, sprint = true, ticks = 30, height = 3) { y ->
        listOf("fill 0 $y -1 4 ${y + 2} 1 stone", "fill 8 $y -1 14 ${y + 2} 1 stone")
    },
    Course("sprint jumping on soul sand", jump = true, sprint = true, ticks = 60, build = floor("soul_sand")),
    Course("sprint jumping on ice", jump = true, sprint = true, ticks = 60, build = floor("packed_ice")),
    Course("sprint jumping on slime", jump = true, sprint = true, ticks = 60, build = floor("slime_block")),
    Course("walking off a tower onto slime", ticks = 100, height = 6, build = ::slimeBelowTower),
    Course(
        "sprint jumping off a tower onto slime", jump = true, sprint = true, ticks = 80, height = 6,
        build = ::slimeBelowTower,
    ),
    Course(
        "walking off a tower onto carpet on slime", ticks = 100, height = 6, build = ::carpetOnSlimeBelowTower,
    ),
    Course("walking off a tower onto beds", ticks = 100, height = 6, build = ::bedsBelowTower),
    Course(
        "sprint jumping off a tower onto beds", jump = true, sprint = true, ticks = 80, height = 6,
        build = ::bedsBelowTower,
    ),
    Course(
        "sneak jumping off a tower onto beds", jump = true, sneak = true, ticks = 60, height = 6,
        build = ::bedsBelowTower,
    ),
    Course("sprinting on honey", sprint = true, build = floor("honey_block")),
    Course("sprinting up slabs and into a wall", sprint = true, ticks = 80) { y ->
        listOf(
            "fill 5 $y -1 7 $y 1 stone_slab",
            "fill 8 $y -1 10 $y 1 stone",
            "fill 16 $y -1 16 ${y + 3} 1 stone",
        )
    },
    Course("sprint jumping up a block", jump = true, sprint = true) { y ->
        listOf("fill 6 $y -1 20 $y 1 stone")
    },
    Course("sprint jumping under a ceiling", jump = true, sprint = true) { y ->
        listOf("fill 0 ${y + 2} -1 30 ${y + 2} 1 stone")
    },
    Course("sprinting diagonally past a fence", strafe = 1, sprint = true) { y ->
        listOf("setblock 5 $y 2 oak_fence")
    },
    Course("sprinting over carpet and stairs", sprint = true) { y ->
        listOf(
            "fill 4 $y -1 5 $y 1 white_carpet",
            "fill 7 $y -1 7 $y 1 oak_stairs[facing=east]",
            "fill 8 $y -1 12 $y 1 stone",
        )
    },
)
