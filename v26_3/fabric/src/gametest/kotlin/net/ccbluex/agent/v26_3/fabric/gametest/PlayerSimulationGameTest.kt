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

import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.RedStoneOreBlock
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

private const val LANE_Z = 20
private const val LANE_START = 16
private const val LANE_END = 60
private const val LANE_HEIGHT = 12

private const val TOLERANCE = 1.0E-9

private const val SETTLE_TIMEOUT_TICKS = 200
private const val ARM_TIMEOUT_TICKS = 200
private const val ORE_WALK_TICKS = 40

/**
 * [SimulatedPlayer] has to predict where vanilla moves the player, tick for tick.
 *
 * Every scenario builds a lane, holds a set of keys and compares the predicted positions with the ones the player
 * went through. A simulation is made both before the player's tick and during it, where navigation makes its own.
 *
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L60-L70)
 *
 * @agent claude-opus-5-5
 */
internal val playerSimulation: GameTestScope.() -> Unit = {
    run("gamemode survival $playerName")
    run("attribute $playerName minecraft:fall_damage_multiplier base set 0")
    awaitClient("the player on the ground", SETTLE_TIMEOUT_TICKS) { it.player?.onGround() == true }

    val ground = origin.y
    val failures = try {
        scenarios.flatMap { scenario -> play(scenario, ground) }
    } finally {
        context.onClient { Recorder.stop() }
    }

    check(failures.isEmpty()) { "simulated movement differs:\n" + failures.joinToString("\n") }
    assertLevelStaysUntouched(ground)
}

/**
 * @agent claude-opus-5-5
 */
public class PlayerSimulationGameTest : ServerGameTest(playerSimulation)

/**
 * Redstone ore lights up under whoever walks over it, on the client as well.
 *
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L75-L97)
 *
 * @agent claude-fable-5-2
 */
private fun GameTestScope.assertLevelStaysUntouched(ground: Int) {
    val lane = Lane(this, ground)
    lane.fill(LANE_START - 2, 0, LANE_END, LANE_HEIGHT, "air", halfWidth = 3)
    lane.floor(LANE_START, 20)
    lane.floor(21, LANE_END, "redstone_ore")
    run("tp $playerName 18.5 ${ground + 1} $LANE_Z.5 -90 0")
    ticks(10)

    val (reached, lit) = client { client ->
        val keys = Input(true, false, false, false, false, false, true)
        val simulation = SimulatedPlayer.fromPlayer(checkNotNull(client.player), SimulatedInput(keys), false)
        repeat(ORE_WALK_TICKS) { simulation.tick() }

        val level = checkNotNull(client.level)
        simulation.blockX to (21..LANE_END).count { x ->
            level.getBlockState(BlockPos(x, ground, LANE_Z)).getValue(RedStoneOreBlock.LIT)
        }
    }

    check(reached > 21) { "the simulation only walked to x=$reached and never stepped on the ore" }
    check(lit == 0) { "a simulation walking over redstone ore lit $lit blocks of it" }
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L99-L128)
 *
 * @agent claude-opus-5-5
 */
private fun GameTestScope.play(scenario: Scenario, ground: Int): List<String> {
    val lane = Lane(this, ground)
    // The next build would close around a player still standing where the last scenario left it
    run("tp $playerName ${LANE_START - 6}.5 $ground $LANE_Z.5 -90 0")
    lane.fill(LANE_START - 2, 0, LANE_END, LANE_HEIGHT, "air", halfWidth = 3)
    scenario.build(lane)
    ticks(scenario.buildTicks)

    val (x, height) = scenario.start
    run("tp $playerName $x ${ground + height} $LANE_Z.5 -90 ${scenario.xRot}")
    ticks(scenario.settleTicks)
    scenario.prepare(this)

    client { client ->
        scenario.keys.forEach { key -> key.mapping(client).isDown = true }
        Recorder.start(scenario)
    }
    val recording = try {
        awaitClient(scenario.name, ARM_TIMEOUT_TICKS + scenario.ticks) { Recorder.recording?.isComplete == true }
        checkNotNull(Recorder.recording)
    } finally {
        // A failed attempt is played again, which must not start with the keys held
        client { client ->
            Key.entries.forEach { key -> key.mapping(client).isDown = false }
            Recorder.stop()
        }
    }
    scenario.cleanUp(this)

    return recording.failures()
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L130-L154)
 *
 * @agent claude-fable-5-2
 */
private class Lane(private val scope: GameTestScope, private val ground: Int) {

    /**
     * Fills the lane from [fromX] to [toX], with heights counted from the first free block above the ground.
     */
    fun fill(fromX: Int, fromHeight: Int, toX: Int, toHeight: Int, block: String, halfWidth: Int = 1) {
        scope.run(
            "fill $fromX ${ground + fromHeight} ${LANE_Z - halfWidth} " +
                "$toX ${ground + toHeight} ${LANE_Z + halfWidth} $block"
        )
    }

    fun command(command: String) {
        scope.run(command)
    }

    fun floor(fromX: Int, toX: Int, block: String = "stone") = fill(fromX, 0, toX, 0, block)

    /**
     * A glass basin with its water surface at [depth].
     */
    fun pool(fromX: Int, toX: Int, depth: Int) {
        fill(fromX - 1, 0, toX + 1, depth, "glass", halfWidth = 2)
        fill(fromX, 0, toX, depth, "water")
    }
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L157-L163)
 *
 * @agent claude-fable-5-2
 */
private enum class Key(val mapping: (Minecraft) -> KeyMapping) {
    FORWARD({ it.options.keyUp }),
    LEFT({ it.options.keyLeft }),
    JUMP({ it.options.keyJump }),
    SNEAK({ it.options.keyShift }),
    SPRINT({ it.options.keySprint }),
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L170-L189)
 *
 * @param start x and height above the ground the player is teleported to, looking down the lane
 * @param armWhen the tick the comparison starts at, with [keys] held since [prepare]
 * @agent claude-fable-5-2
 */
private class Scenario(
    val name: String,
    val keys: Set<Key>,
    val ticks: Int,
    val start: Pair<Double, Int> = 18.5 to 1,
    val xRot: Float = 0.0f,
    val buildTicks: Int = 2,
    val settleTicks: Int = 10,
    val armWhen: (LocalPlayer) -> Boolean = { true },
    val prepare: GameTestScope.() -> Unit = {},
    val cleanUp: GameTestScope.() -> Unit = {},
    val build: (Lane) -> Unit,
) {
    fun input() = SimulatedInput(
        Input(
            Key.FORWARD in keys, false, Key.LEFT in keys, false,
            Key.JUMP in keys, Key.SNEAK in keys, Key.SPRINT in keys,
        ),
    )
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L192-L194)
 *
 * @agent claude-fable-5-2
 */
private data class Step(val pos: Vec3, val onGround: Boolean) {
    constructor(player: Player) : this(player.position(), player.onGround())
}

/**
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L196-L228)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
private class Recording(val scenario: Scenario) {
    var beforeTick: List<Step>? = null
    var duringTick: List<Step>? = null

    val steps = ArrayList<Step>()

    val isComplete get() = steps.size >= scenario.ticks

    fun predict(player: LocalPlayer, continuesTick: Boolean): List<Step> {
        val simulation = SimulatedPlayer.fromPlayer(player, scenario.input(), continuesTick)
        return List(scenario.ticks) {
            simulation.tick()
            Step(simulation)
        }
    }

    fun failures(): List<String> = listOfNotNull(
        difference("before the tick", checkNotNull(beforeTick)),
        difference("during the tick", checkNotNull(duringTick)),
    ).map { "${scenario.name}, $it" }

    private fun difference(made: String, predicted: List<Step>): String? {
        val tick = steps.indices.firstOrNull { tick ->
            val real = steps[tick]
            val simulated = predicted[tick]
            real.onGround != simulated.onGround || real.pos.distanceTo(simulated.pos) > TOLERANCE
        } ?: return null

        return "simulation made $made: tick $tick is ${predicted[tick]}, the player was ${steps[tick]}"
    }
}

/**
 * Predicts at the start of the client tick and right after the input read the keys, then records where the player
 * went at the end of each of its ticks. Only on the client thread.
 *
 * From LiquidBounce: [PlayerSimulationGameTest.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/gametest/kotlin/net/ccbluex/liquidbounce/gametest/PlayerSimulationGameTest.kt#L245-L398)
 *
 * @agent claude-fable-5-2
 * @agent claude-opus-5-5
 */
private object Recorder {
    private var pending: Scenario? = null

    @Volatile
    var recording: Recording? = null
        private set

    private val tickHandler = Runnable {
        val scenario = pending ?: return@Runnable
        val player = Minecraft.getInstance().player ?: return@Runnable
        if (!scenario.armWhen(player)) {
            return@Runnable
        }

        pending = null
        recording = Recording(scenario).apply { beforeTick = predict(player, continuesTick = false) }
    }

    private val inputHandler = Consumer<LocalPlayer> { player ->
        val recording = recording?.takeIf { it.duringTick == null } ?: return@Consumer
        recording.duringTick = recording.predict(player, continuesTick = true)
    }

    private val postTickHandler = Consumer<LocalPlayer> { player ->
        val recording = recording?.takeIf { it.duringTick != null && !it.isComplete } ?: return@Consumer
        recording.steps += Step(player)
    }

    fun start(scenario: Scenario) {
        pending = scenario
        recording = null
        GameTicks.clientTick.addIfAbsent(tickHandler)
        GameTicks.afterInput.addIfAbsent(inputHandler)
        GameTicks.afterPlayerTick.addIfAbsent(postTickHandler)
    }

    fun stop() {
        pending = null
        recording = null
        GameTicks.clientTick.remove(tickHandler)
        GameTicks.afterInput.remove(inputHandler)
        GameTicks.afterPlayerTick.remove(postTickHandler)
    }
}

private val walk = setOf(Key.FORWARD)
private val sprint = setOf(Key.FORWARD, Key.SPRINT)
private val sprintJump = setOf(Key.FORWARD, Key.SPRINT, Key.JUMP)
private val effects = listOf("speed", "jump_boost", "slow_falling")

private val scenarios = listOf(
    Scenario("sprint jumping from a standstill", sprintJump, ticks = 50) { lane ->
        lane.floor(LANE_START, LANE_END)
    },
    Scenario("strafing while walking", setOf(Key.FORWARD, Key.LEFT), ticks = 20) { lane ->
        lane.fill(LANE_START, 0, LANE_END, 0, "stone", halfWidth = 3)
    },
    Scenario("sneaking up to a ledge", setOf(Key.FORWARD, Key.SNEAK), ticks = 60, start = 18.5 to 3) { lane ->
        lane.fill(LANE_START, 0, 20, 2, "stone")
    },
    Scenario("walking off a ledge", walk, ticks = 50, start = 18.5 to 8) { lane ->
        lane.fill(LANE_START, 0, 20, 7, "stone")
    },
    Scenario("stepping up slabs and running into a wall", sprint, ticks = 100) { lane ->
        lane.floor(LANE_START, LANE_END)
        lane.fill(24, 1, 26, 1, "stone_slab")
        lane.fill(27, 1, 29, 1, "stone")
        lane.fill(36, 1, 36, 3, "stone")
    },
    Scenario("jumping into a pool and swimming", setOf(Key.FORWARD, Key.JUMP), ticks = 80, start = 18.5 to 4) {
            lane ->
        lane.fill(LANE_START, 0, 21, 3, "stone", halfWidth = 2)
        lane.pool(22, 30, depth = 3)
    },
    Scenario(
        "sprint swimming under water",
        sprint,
        ticks = 40,
        start = 23.5 to 3,
        xRot = 5.0f,
    ) { lane ->
        lane.pool(22, 32, depth = 5)
    },
    Scenario("drifting in flowing water", emptySet(), ticks = 40, start = 19.5 to 1, buildTicks = 100) { lane ->
        lane.floor(LANE_START, LANE_END)
        lane.fill(LANE_START, 1, 40, 2, "glass")
        lane.fill(17, 1, 39, 2, "air", halfWidth = 0)
        lane.fill(17, 1, 17, 1, "water", halfWidth = 0)
    },
    Scenario("climbing a ladder and dropping behind it", walk, ticks = 90, start = 20.5 to 1) { lane ->
        lane.floor(LANE_START, LANE_END)
        lane.fill(24, 1, 24, 8, "stone")
        lane.fill(23, 1, 23, 8, "ladder[facing=west]", halfWidth = 0)
    },
    Scenario("sprint jumping over soul sand, ice, slime, honey and into cobwebs", sprintJump, ticks = 160) {
            lane ->
        lane.floor(LANE_START, LANE_END)
        lane.floor(21, 24, "soul_sand")
        lane.floor(25, 28, "packed_ice")
        lane.floor(29, 32, "slime_block")
        lane.floor(33, 36, "honey_block")
        lane.fill(39, 1, 39, 2, "cobweb")
    },
    Scenario(
        "bouncing on slime",
        emptySet(),
        ticks = 80,
        start = 18.5 to 10,
        settleTicks = 3,
        armWhen = { player -> player.fallDistance > 0.3 },
    ) { lane ->
        lane.floor(LANE_START, 22, "slime_block")
    },
    Scenario(
        "falling onto the edge of a slab",
        walk,
        ticks = 40,
        start = 18.5 to 8,
        armWhen = { player -> player.fallDistance > 0.3 },
    ) { lane ->
        lane.fill(LANE_START, 0, 20, 7, "stone")
        lane.floor(21, 22)
        lane.fill(23, 1, 24, 1, "stone_slab")
    },
    Scenario(
        "sprint jumping with speed, jump boost and slow falling",
        sprintJump,
        ticks = 80,
        prepare = {
            awaitClient("the effects", SETTLE_TIMEOUT_TICKS) { client ->
                checkNotNull(client.player).activeEffects.size == effects.size
            }
        },
        cleanUp = { run("effect clear @p") },
    ) { lane ->
        lane.floor(LANE_START, LANE_END)
        effects.forEach { effect -> lane.command("effect give @p minecraft:$effect 600 1 true") }
    },
    Scenario(
        "gliding with an elytra",
        walk,
        ticks = 60,
        start = 18.5 to 70,
        xRot = 15.0f,
        settleTicks = 3,
        armWhen = { player -> player.isFallFlying },
        prepare = {
            awaitClient("the fall", SETTLE_TIMEOUT_TICKS) { client ->
                checkNotNull(client.player).deltaMovement.y < -0.5
            }
            input.holdKey { options -> options.keyJump }
            ticks(2)
            input.releaseKey { options -> options.keyJump }
        },
        cleanUp = { run("item replace entity @p armor.chest with minecraft:air") },
    ) { lane ->
        lane.command("item replace entity @p armor.chest with minecraft:elytra")
    },
)
