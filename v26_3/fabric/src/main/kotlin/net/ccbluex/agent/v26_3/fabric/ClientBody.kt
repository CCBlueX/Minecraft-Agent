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

package net.ccbluex.agent.v26_3.fabric

import net.ccbluex.agent.v26_3.fabric.mixin.ClientInputAccessor
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.math.copy
import net.ccbluex.agent.math.turned
import net.ccbluex.agent.navigation.Body
import net.ccbluex.agent.navigation.BodyTraits
import net.ccbluex.agent.navigation.FlightControl
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Teleporter
import net.ccbluex.agent.simulation.SimulatedInput
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The local player as a [Body], moved with vanilla means only: the movement keys of the tick, the player's own
 * rotation, the hotbar and [net.minecraft.client.multiplayer.MultiPlayerGameMode] for digging and using items.
 *
 * The rotation the walk faces ([LookPurpose.MOVEMENT]) or aims with ([LookPurpose.AIM]) is turned to at most
 * [maxTurn] degrees a tick in whole mouse steps, and holds while it is asked for every tick; then it turns back to the
 * camera. The player moves and the movement packet goes out with it, and the keys pressed for the camera are turned
 * onto it; once the packet is out the player looks through the camera again. The camera only turns with the mouse and
 * [LookPurpose.VIEW].
 *
 * [ClientNavigation] drives it. A client with its own rotation and input handling overrides [look], [press] and the
 * rotation members and calls the navigator from its own input hook instead.
 *
 * @agent claude-opus-5-5
 */
public open class ClientBody @JvmOverloads constructor(

    /**
     * Largest turn towards a movement or aim rotation per tick, in degrees; 180 turns at once.
     */
    public var maxTurn: Float = DEFAULT_MAX_TURN,
) : Body {

    private val minecraft: Minecraft
        get() = Minecraft.getInstance()

    override val player: LocalPlayer
        get() = checkNotNull(minecraft.player) { "There is no local player" }

    override var safeWalk: Boolean = false

    /**
     * Simulations of the local player are kept from touching the level by this module's mixins.
     */
    override val traits: BodyTraits = BodyTraits(jumpClimbsLadders = true, exactSimulation = true)

    override val flight: FlightControl?
        get() = null

    override val teleporter: Teleporter?
        get() = null

    override val latencyMillis: Int
        get() {
            val player = player
            return player.connection.getPlayerInfo(player.uuid)?.latency ?: 0
        }

    override val sentRotation: ViewRotation
        get() = lastSentRotation ?: player.let { ViewRotation(it.yRot, it.xRot) }

    override val isViewTakenOver: Boolean
        get() = isMouseTurning()

    /**
     * One mouse count at the current sensitivity, as `MouseHandler.turnPlayer` and `Entity.turn` turn.
     */
    override val viewStep: Float
        get() {
            val sensitivity = minecraft.options.sensitivity().get() * SENSITIVITY_SCALE + SENSITIVITY_BASE
            return (sensitivity * sensitivity * sensitivity * MOUSE_FACTOR * DEGREES_PER_COUNT).toFloat()
        }

    private var pressed: Input? = null
    private var pressedYRot = 0f
    private var ownSlot = NO_SLOT

    /**
     * The rotation the player moves and is sent with, while it differs from the camera.
     */
    private var rotation: ViewRotation? = null

    /**
     * This tick's [rotation], once a movement or aim rotation was asked for.
     */
    private var nextRotation: ViewRotation? = null
    private var nextPurpose: LookPurpose? = null

    /**
     * The camera, put back once the movement packet of the tick is out.
     */
    private var camera: ViewRotation? = null

    override val movementYRot: Float
        get() = (nextRotation ?: rotation)?.yRot ?: player.yRot

    /**
     * Whether a dig runs that vanilla's own attack handling must not cancel.
     */
    internal var isDestroying: Boolean = false
        private set

    override fun press(input: Input) {
        pressed = input
        pressedYRot = inputYRot
    }

    override fun look(rotation: ViewRotation, purpose: LookPurpose) {
        when (purpose) {
            LookPurpose.VIEW -> {
                val player = player
                player.yRot = rotation.yRot
                player.xRot = rotation.xRot.coerceIn(MIN_X_ROT, MAX_X_ROT)
            }

            LookPurpose.MOVEMENT, LookPurpose.AIM -> if (nextPurpose != LookPurpose.AIM) {
                nextRotation = turn(this.rotation ?: player.let { ViewRotation(it.yRot, it.xRot) }, rotation)
                nextPurpose = purpose
            }
        }
    }

    override fun selectHotbarSlot(slot: Int) {
        val inventory = player.inventory
        if (ownSlot == NO_SLOT) {
            ownSlot = inventory.selectedSlot
        }
        inventory.selectedSlot = slot
    }

    override fun releaseHotbarSlot() {
        if (ownSlot == NO_SLOT) {
            return
        }
        minecraft.player?.inventory?.selectedSlot = ownSlot
        ownSlot = NO_SLOT
    }

    /**
     * Like a click on the block in `Minecraft.startAttack`: starts digging, swings and punches.
     */
    override fun startDestroy(pos: BlockPos, face: Direction): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val started = gameMode.startDestroyBlock(pos, face)
        isDestroying = gameMode.isDestroying
        swingAttack()
        return started
    }

    /**
     * Like holding the attack key in `Minecraft.continueAttack`.
     */
    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        isDestroying = true
        val continued = gameMode.continueDestroyBlock(pos, face)
        if (continued) {
            swingAttack()
        }
        return continued
    }

    override fun stopDestroy() {
        isDestroying = false
        minecraft.gameMode?.takeIf { it.isDestroying }?.stopDestroyBlock()
    }

    /**
     * The block branch of `Minecraft.startUseItem`; true if the use went through.
     */
    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val player = player
        val stack = player.getItemInHand(hand)
        val count = stack.count
        val result = gameMode.useItemOn(player, hand, hit)
        if (result !is InteractionResult.Success) {
            return false
        }

        if (result.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(hand, stack.interactAnimation, false)
            if (!stack.isEmpty && (stack.count != count || player.hasInfiniteMaterials())) {
                player.itemUsed(hand)
            }
        }
        return true
    }

    /**
     * The item branch of `Minecraft.startUseItem`; true if the use went through.
     */
    override fun useItem(hand: InteractionHand): Boolean {
        val gameMode = minecraft.gameMode ?: return false
        val player = player
        val stack = player.getItemInHand(hand)
        if (stack.isEmpty) {
            return false
        }

        // The use packet carries the rotation, which has to be the one the movement packet of the tick sends
        val result = facing(player, nextRotation ?: rotation) { gameMode.useItem(player, hand) }
        if (result !is InteractionResult.Success) {
            return false
        }

        if (result.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(hand, stack.interactAnimation, false)
        }
        player.itemUsed(hand)
        return true
    }

    /**
     * After the navigator ran in the player's tick, before the player moves: puts the player on the rotation of the
     * tick, or turns it back towards the camera, and puts the keys into [input], turned onto that rotation.
     */
    internal fun applyTick(player: LocalPlayer, input: ClientInput) {
        val view = ViewRotation(player.yRot, player.xRot)
        val last = this.rotation
        val rotation = nextRotation ?: last?.let { turn(it, view) }?.takeUnless { it.reaches(view) }
        this.rotation = rotation
        nextRotation = null
        nextPurpose = null
        if (rotation == null && last != null) {
            alignCamera(player, last.yRot)
        }
        if (rotation != null) {
            camera = view
            player.yRot = rotation.yRot
            player.xRot = rotation.xRot
        }

        val pressed = pressed
        this.pressed = null
        input.keyPresses = when {
            // Sprint left unset leaves it to the player's own key
            pressed != null -> pressed.turned(pressedYRot, player.yRot)
                .copy(sprint = pressed.sprint() || input.keyPresses.sprint())

            rotation != null -> input.keyPresses.turned(view.yRot, player.yRot)
            else -> return
        }
        (input as ClientInputAccessor).agentSetMoveVector(SimulatedInput(input.keyPresses).moveVector)
    }

    /**
     * Back from a rotation of its own, the camera's yaw is moved by whole turns next to the yaw sent last, which looks
     * the same; the server would take a jump of a whole turn for a turn no player makes.
     */
    private fun alignCamera(player: LocalPlayer, sentYRot: Float) {
        val turns = Math.round((sentYRot - player.yRot) / FULL_TURN) * FULL_TURN
        player.yRot += turns
        player.yRotO += turns
    }

    /**
     * The server set the rotation along with the position; the next turn starts from there.
     */
    internal fun positionCorrected() {
        rotation = null
        nextRotation = null
        nextPurpose = null
    }

    /**
     * Once the movement packet of the tick is out: the player looks through the camera again.
     */
    internal fun restoreCamera() {
        val camera = camera ?: return
        this.camera = null
        minecraft.player?.let {
            it.yRot = camera.yRot
            it.xRot = camera.xRot
        }
    }

    /**
     * Lets go of everything held, for when navigation stops.
     */
    internal fun release() {
        pressed = null
        restoreCamera()
        val last = rotation
        rotation = null
        nextRotation = null
        nextPurpose = null
        val player = minecraft.player
        if (last != null && player != null) {
            alignCamera(player, last.yRot)
        }
        safeWalk = false
        if (isDestroying) {
            stopDestroy()
        }
        releaseHotbarSlot()
    }

    /**
     * Runs [action] with [player] turned to [rotation] when there is one, and turns it back.
     */
    private inline fun <T> facing(player: LocalPlayer, rotation: ViewRotation?, action: () -> T): T {
        if (rotation == null) {
            return action()
        }
        val yRot = player.yRot
        val xRot = player.xRot
        player.yRot = rotation.yRot
        player.xRot = rotation.xRot
        try {
            return action()
        } finally {
            player.yRot = yRot
            player.xRot = xRot
        }
    }

    /**
     * [from] turned towards [to] by at most [maxTurn] on each axis, in whole [viewStep]s.
     */
    private fun turn(from: ViewRotation, to: ViewRotation): ViewRotation {
        val step = viewStep
        val yRot = quantize(Mth.wrapDegrees(to.yRot - from.yRot).coerceIn(-maxTurn, maxTurn), step)
        val xRot = quantize((to.xRot - from.xRot).coerceIn(-maxTurn, maxTurn), step)
        return ViewRotation(from.yRot + yRot, (from.xRot + xRot).coerceIn(MIN_X_ROT, MAX_X_ROT))
    }

    /**
     * Whether this is [other] within half a [viewStep], which turns cannot get any closer.
     */
    private fun ViewRotation.reaches(other: ViewRotation): Boolean {
        val tolerance = viewStep / 2f + EPSILON
        return abs(Mth.wrapDegrees(yRot - other.yRot)) <= tolerance && abs(xRot - other.xRot) <= tolerance
    }

    /**
     * [value] in whole [step]s; any value when [step] is 0.
     */
    private fun quantize(value: Float, step: Float) = if (step > 0f) (value / step).roundToInt() * step else value

    private fun swingAttack() {
        val player = player
        player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
        player.connection.send(ServerboundPunchPacket.INSTANCE)
    }

    private companion object {
        const val DEFAULT_MAX_TURN = 180f
        const val NO_SLOT = -1
        const val MIN_X_ROT = -90f
        const val MAX_X_ROT = 90f
        const val SENSITIVITY_SCALE = 0.6
        const val SENSITIVITY_BASE = 0.2
        const val MOUSE_FACTOR = 8.0
        const val DEGREES_PER_COUNT = 0.15
        const val EPSILON = 1.0E-4f
        const val FULL_TURN = 360f
    }

}
