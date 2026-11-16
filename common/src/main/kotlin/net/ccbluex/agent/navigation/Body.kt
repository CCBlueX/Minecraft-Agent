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

package net.ccbluex.agent.navigation

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulationHooks
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.Level
import net.ccbluex.agent.world.entity.Player

/**
 * The player a [Navigator] moves, and the means the host gives it: keys, looking, digging, using items. A client
 * implements it for the local player, a server for a bot. Every call comes from inside [Navigator.navigate] or
 * [Navigator.halt], on the thread that ticks the player.
 *
 * @agent claude-opus-5-5
 */
public interface Body {

    /**
     * A `LocalPlayer` on a client, a `ServerPlayer` for server bots.
     */
    public val player: Player

    public val level: Level
        get() = player.level()

    /**
     * The keys for this tick, once per tick the [Navigator] moves. `sprint` set means sprinting is forced while
     * moving. [exact] keys must reach the player as they are, as a jump or a climb needs them: no host feature may
     * change them, and an unset `sprint` means not to sprint. Other keys are a plain walk, which host features may
     * change, and an unset `sprint` leaves sprinting to the player.
     */
    public fun press(input: Input, exact: Boolean)

    /**
     * [rotation] is where to end up. [LookPurpose.MOVEMENT], [LookPurpose.AIM] and [LookPurpose.STEER] are turned to
     * at the body's own pace, except that STEER is taken as given while the player glides on an elytra;
     * [LookPurpose.VIEW] is taken as given. Of the looks of a tick, STEER wins over AIM and AIM over MOVEMENT; VIEW
     * only turns the camera. The navigator never counts on a pace: it reads the rotation reached every tick from
     * [movementYRot], [inputYRot] and [sentRotation].
     */
    public fun look(rotation: ViewRotation, purpose: LookPurpose)

    /**
     * The last rotation the server knows; the player's own rotation on a server.
     */
    public val sentRotation: ViewRotation

    /**
     * The yaw the game moves the player along, which differs from the view under movement correction.
     */
    public val movementYRot: Float
        get() = player.yRot

    /**
     * The yaw the movement keys act relative to: the rotation under strict movement correction, else the view.
     */
    public val inputYRot: Float
        get() = player.yRot

    /**
     * Whether the user or another feature turns the view now, so [LookPurpose.VIEW] turns stop.
     */
    public val isViewTakenOver: Boolean
        get() = false

    /**
     * The smallest view turn, such as one mouse count; view turns are made in whole steps. 0 turns freely.
     */
    public val viewStep: Float
        get() = 0f

    /**
     * Holds the player at ledges. A server bot backs off as sneaking does, without its slowdown; a client body sneaks
     * there as a player does ([LedgeSneak]), since anticheats see a back-off at an edge without sneaking.
     */
    public var safeWalk: Boolean

    /**
     * Round trip to the server in milliseconds, 0 on a server.
     */
    public val latencyMillis: Int

    public val traits: BodyTraits

    /**
     * How the game moves the player, for the walks the navigator simulates. A host that changes the movement, such as
     * a speed feature, can describe it here; the walk times its jumps from the movement it measures either way.
     */
    public val simulationHooks: SimulationHooks
        get() = SimulationHooks.VANILLA

    /**
     * A copy of [player] holding [input], facing [yRot], moved by the game's own player movement with
     * [simulationHooks]; only where [BodyTraits.exactSimulation] is set. The first tick finishes the player's current
     * movement tick when [BodyTraits.navigatesInMovementTick] is set.
     */
    public fun simulate(input: SimulatedInput, yRot: Float): PlayerSimulation.Steered

    /**
     * Holds hotbar [slot] (0 to 8) selected until [releaseHotbarSlot]; called every tick it is wanted.
     */
    public fun selectHotbarSlot(slot: Int)

    public fun releaseHotbarSlot()

    /**
     * Starts digging [pos]; the body keeps the dig going until [stopDestroy], even with no attack key held.
     */
    public fun startDestroy(pos: BlockPos, face: Direction): Boolean

    public fun continueDestroy(pos: BlockPos, face: Direction): Boolean

    public fun stopDestroy()

    public fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean

    public fun useItem(hand: InteractionHand): Boolean

    /**
     * Whether a feature of the host, such as a fly module, flies the player right now, besides creative flight. The
     * navigator measures how fast it flies and how far it coasts from what the player does, so the feature may change
     * its speed at any time.
     */
    public val isFlying: Boolean
        get() = false

    /**
     * Whether the host keeps an elytra glide going along the floor, as an elytra recast feature does.
     */
    public val recastsElytra: Boolean
        get() = false

    /**
     * How this body teleports, with packets on a client or by setting the position on a server; null without.
     */
    public val teleporter: Teleporter?

}

/**
 * What a [Navigator] does with a rotation it asks for.
 *
 * @agent claude-opus-5-5
 */
public enum class LookPurpose {

    /**
     * The way the walk faces, so forward keys move the player there; any other rotation goes first.
     */
    MOVEMENT,

    /**
     * At a block to mine, place or open; the next movement packet has to carry it, as the action checks it.
     */
    AIM,

    /**
     * The camera, turned the way navigation goes; the client view only, never sent on its own.
     */
    VIEW,

    /**
     * Worked out for this very tick, and the movement relies on reaching it. While the player glides on an elytra,
     * as elytra steering and a roof bounce do, bodies apply it as given: turned slower, the glide goes astray.
     * Otherwise it is a quick turn a skill has to land now, such as a block clutch, which bodies that pace their
     * turns still pace, as fast as a person flicks.
     */
    STEER,
}

/**
 * Facts about the game the body plays.
 *
 * @agent claude-opus-5-5
 */
public class BodyTraits(
    /**
     * Whether holding jump climbs a ladder or vine; before 1.14 only pushing against the block beside it does.
     */
    public val jumpClimbsLadders: Boolean = true,

    /**
     * Whether the walk may run the game's own player movement on copies of the player to predict it. Off on a
     * server, where that movement sets off pressure plates, tramples farmland and so on; a lighter model is used.
     */
    public val exactSimulation: Boolean = true,

    /**
     * Whether [Navigator.navigate] runs inside the player's movement tick, before its movement is applied.
     */
    public val navigatesInMovementTick: Boolean = true,
)

/**
 * Moves the body along a teleport route.
 *
 * @agent claude-opus-5-5
 */
public interface Teleporter {

    /**
     * Tells the server the player stands at [position]; on a client a movement packet, on a server a position set.
     */
    public fun send(position: Vec3, onGround: Boolean)

    /**
     * Puts the player, or the vehicle it rides, at [position] with no motion and no fall distance left, as the route
     * ends there.
     */
    public fun land(position: Vec3)
}
